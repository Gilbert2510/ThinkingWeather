package com.thinkingweather.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thinkingweather.android.data.*
import com.thinkingweather.android.model.*
import com.thinkingweather.android.network.NetworkMonitor
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val cities: List<City> = DefaultCities.initial,
    val selectedCity: City = DefaultCities.beijing,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val lastLocationMessage: String? = null,
    val locatedCityLocation: String? = null,
    val cityEditMessage: String? = null,
    val searchErrorMessage: String? = null,
    val searchErrorIsOffline: Boolean = false,
    val searchHint: String? = null,
    val citySummaries: Map<String, CityWeatherSummary> = emptyMap(),
    val loadingCitySummaries: Set<String> = emptySet(),
    val citySummaryErrors: Set<String> = emptySet(),
    val settingsLoaded: Boolean = false,
)

class WeatherViewModel internal constructor(
    application: Application,
    private val repository: WeatherDataSource,
    private val store: WeatherPreferences,
    private val summaryStore: CitySummaryCache,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application,
        WeatherRepository(BuildConfig.WEATHER_BASE_URL, hasInternet = { NetworkMonitor.hasInternet(application) }),
        WeatherStore(application), CitySummaryStore(application))

    private val mutableState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = mutableState.asStateFlow()
    private val settingsReady = CompletableDeferred<Unit>()
    private var weatherJob: Job? = null
    private var searchJob: Job? = null
    private var locationJob: Job? = null
    private var citySummaryJob: Job? = null
    private var summaryPersistJob: Job? = null
    private var citySummaryGeneration = 0L
    private var citiesPageOpen = false
    private var summaryCacheLoaded = false
    private var weatherGeneration = 0L
    private val persistenceMutex = Mutex()
    private var storedCities = DefaultCities.initial

    init {
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.read() }
                storedCities = saved.cities
                mutableState.update { it.copy(
                    cities = storedCities,
                    selectedCity = saved.selectedCity,
                    temperatureUnit = saved.unit,
                    locatedCityLocation = saved.locatedCityLocation) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(
                    cities = storedCities,
                    errorMessage = "无法读取本地设置，将使用默认设置。") }
            } finally {
                mutableState.update { it.copy(settingsLoaded = true) }
                settingsReady.complete(Unit)
            }
            loadWeather(force = false)
        }
    }

    fun selectCity(city: City) {
        cancelLocationLookup()
        viewModelScope.launch {
            settingsReady.await()
            selectCityAndPersist(city, fromLocation = false)
        }
    }

    /** Location calls this in its own job, so a new lookup or manual selection cancels it. */
    private suspend fun selectCityAndPersist(city: City, fromLocation: Boolean) {
        val current = mutableState.value
        val cities = if (current.cities.any { it.location == city.location })
            current.cities.map { if (it.location == city.location) city else it } else
            (current.cities + city).take(WeatherStore.MAX_CITIES)
        if (cities.none { it.location == city.location }) {
            mutableState.update { it.copy(errorMessage = "最多可保存 ${WeatherStore.MAX_CITIES} 个城市。") }
            return
        }
        val reuseWeather = current.selectedCity.location == city.location && (current.snapshot != null || weatherJob?.isActive == true)
        if (reuseWeather && city == current.selectedCity && cities == current.cities && !fromLocation) return
        storedCities = cities
        // Publish selection, marker and success message together, before the first persistence await.
        // Finishing older I/O never writes location UI again.
        mutableState.update { state -> state.copy(cities = cities, selectedCity = city, errorMessage = null,
            locatedCityLocation = if (fromLocation) city.location else state.locatedCityLocation,
            lastLocationMessage = if (fromLocation) "已定位到${city.name}" else state.lastLocationMessage,
            snapshot = if (reuseWeather) state.snapshot?.copy(city = city) else null,
            citySummaries = state.citySummaries.mapValues { (key, summary) ->
                if (key == city.location) summary.copy(city = city) else summary
            }) }
        if (!reuseWeather) loadWeather(force = false)
        persistCities(cities, city)
        persistSummariesSoon()
        if (citiesPageOpen) { cancelSummaryRefresh(); refreshCitySummaries() }
    }

    fun addCity(city: City) = selectCity(city)

    fun removeCity(city: City) = removeCities(setOf(city.location))

    fun removeCities(locations: Set<String>) {
        viewModelScope.launch {
            settingsReady.await()
            val current = mutableState.value
            val plan = planCityRemoval(current.cities, locations, current.selectedCity)
            if (plan.blocked) {
                mutableState.update { it.copy(cityEditMessage = "请至少保留一个城市。") }
                return@launch
            }
            val cities = plan.cities
            if (cities == current.cities) return@launch
            val selection = plan.selectedCity
            val selectionChanged = current.selectedCity.location != selection.location
            if (selectionChanged) cancelLocationLookup()
            storedCities = cities
            cancelSummaryRefresh()
            mutableState.update { it.copy(cities = cities, selectedCity = selection,
                snapshot = if (selectionChanged) null else it.snapshot, errorMessage = null, cityEditMessage = null,
                locatedCityLocation = it.locatedCityLocation?.takeIf { key -> cities.any { saved -> saved.location == key } },
                citySummaries = it.citySummaries.filterKeys { key -> cities.any { saved -> saved.location == key } },
                citySummaryErrors = it.citySummaryErrors - locations) }
            persistSummariesSoon()
            if (selectionChanged) loadWeather(force = false)
            persistCities(cities, selection)
            if (citiesPageOpen) refreshCitySummaries()
        }
    }

    fun reorderCities(locations: List<String>) {
        viewModelScope.launch {
            settingsReady.await()
            val current = mutableState.value
            val cities = orderedSavedCities(current.cities, locations)
            if (cities == current.cities) return@launch
            storedCities = cities
            mutableState.update { it.copy(cities = cities) }
            persistCities(cities, current.selectedCity)
        }
    }

    fun clearCityEditMessage() { mutableState.update { it.copy(cityEditMessage = null) } }

    fun clearLocationMessage(expected: String) {
        mutableState.update { if (it.lastLocationMessage == expected) it.copy(lastLocationMessage = null) else it }
    }

    private fun cancelLocationLookup() {
        locationJob?.cancel()
        locationJob = null
        mutableState.update { if (it.lastLocationMessage == "正在识别当前位置…") it.copy(lastLocationMessage = null) else it }
    }

    fun refresh() {
        viewModelScope.launch {
            settingsReady.await()
            loadWeather(force = true)
            if (citiesPageOpen) { cancelSummaryRefresh(); refreshCitySummaries() }
        }
    }

    private fun loadWeather(force: Boolean) {
        weatherJob?.cancel()
        val generation = ++weatherGeneration
        val city = mutableState.value.selectedCity
        mutableState.update { it.copy(isLoading = it.snapshot == null, isRefreshing = it.snapshot != null, errorMessage = null) }
        weatherJob = viewModelScope.launch {
            try {
                val cached = if (force && mutableState.value.snapshot != null) null
                    else withContext(Dispatchers.IO) { store.cached(city) }
                if (generation != weatherGeneration) return@launch
                if (cached != null) {
                    // Persisted live data is provisional until the gateway confirms its current feeds.
                    val displayed = cached.copy(isStale = true)
                    mutableState.update { it.copy(snapshot = displayed.copy(city = it.selectedCity), isLoading = false, isRefreshing = true) }
                    rememberSummary(CityWeatherSummary.from(displayed))
                }
                val received = repository.weather(city)
                if (generation != weatherGeneration) return@launch
                val snapshot = received.copy(city = mutableState.value.selectedCity)
                mutableState.update { it.copy(snapshot = snapshot, isLoading = false, errorMessage = null) }
                rememberSummary(CityWeatherSummary.from(snapshot))
                try {
                    withContext(Dispatchers.IO) { store.save(snapshot) }
                } catch (storageError: Exception) {
                    if (storageError is CancellationException) throw storageError
                    if (generation == weatherGeneration) mutableState.update {
                        it.copy(errorMessage = "天气已更新，但暂时无法缓存到设备。请检查设备存储空间。")
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == weatherGeneration) {
                    mutableState.update { it.copy(errorMessage = message(error) + if (it.snapshot != null) " 当前显示已缓存的数据。" else "") }
                }
            } finally {
                if (generation == weatherGeneration) mutableState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim().take(80)
        mutableState.update { it.copy(searchQuery = query.take(80), searchResults = emptyList(),
            isSearching = trimmed.isNotBlank(), searchErrorMessage = null,
            searchErrorIsOffline = false, searchHint = null) }
        if (trimmed.isBlank()) return
        searchJob = viewModelScope.launch {
            settingsReady.await()
            val savedMatches = storedCities.filter {
                it.name.contains(trimmed, ignoreCase = true) || it.description.contains(trimmed, ignoreCase = true)
            }
            if (repository.networkUnavailable) {
                mutableState.update { it.copy(isSearching = false, searchResults = savedMatches,
                    searchErrorMessage = "天气服务不可用，请稍后重试", searchErrorIsOffline = true) }
                return@launch
            }
            if (shouldWaitForLongerCityQuery(trimmed)) {
                mutableState.update { it.copy(isSearching = false, searchResults = savedMatches,
                    searchHint = "请继续输入拼音或城市名称") }
                return@launch
            }
            delay(350)
            try {
                val cities = repository.search(trimmed)
                mutableState.update { it.copy(searchResults = cities, isSearching = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val notice = citySearchNotice(error, repository.networkUnavailable)
                mutableState.update { it.copy(isSearching = false, searchResults = savedMatches,
                    searchErrorMessage = notice.message, searchErrorIsOffline = notice.offline) }
            }
        }
    }

    fun onCitiesOpened() {
        citiesPageOpen = true
        refreshCitySummaries()
    }

    fun onCitiesClosed() {
        citiesPageOpen = false
        cancelSummaryRefresh()
    }

    /** Current-only requests are bounded, cached and scoped to the city page. */
    fun refreshCitySummaries() {
        if (!citiesPageOpen || citySummaryJob?.isActive == true) return
        val generation = ++citySummaryGeneration
        citySummaryJob = viewModelScope.launch {
            settingsReady.await()
            if (!citiesPageOpen) return@launch
            try {
                if (!summaryCacheLoaded) {
                    val cards = try { withContext(Dispatchers.IO) { summaryStore.read() } }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        emptyList()
                    }
                    if (!canApplySummary(generation)) return@launch
                    cards.forEach { rememberSummary(it, persist = false) }
                    summaryCacheLoaded = true
                }
                // A full cached forecast may be the only offline observation available.
                if (storedCities.any { mutableState.value.citySummaries[it.location] == null }) {
                    val cached = withContext(Dispatchers.IO) { store.cachedAll() }
                    if (!canApplySummary(generation)) return@launch
                    cached.forEach { rememberSummary(CityWeatherSummary.from(it), persist = false) }
                }
                mutableState.value.snapshot?.let {
                    rememberSummary(CityWeatherSummary.from(it), persist = false)
                }
                val now = System.currentTimeMillis()
                val pending = storedCities.filter { mutableState.value.citySummaries[it.location]?.isFresh(now) != true }
                val keys = pending.map(City::location).toSet()
                mutableState.update { state -> state.copy(
                    citySummaries = state.citySummaries.mapValues { (_, value) ->
                        if (value.isFresh(now)) value else value.copy(isStale = true)
                    }, loadingCitySummaries = keys, citySummaryErrors = state.citySummaryErrors - keys,
                ) }
                if (pending.isEmpty() || repository.networkUnavailable) return@launch
                CitySummaryLoader(repository::currentSummary).load(pending) { city, result ->
                    if (canApplySummary(generation) && storedCities.any { it.location == city.location }) {
                        result.onSuccess { rememberSummary(it) }
                        mutableState.update { state -> state.copy(
                            loadingCitySummaries = state.loadingCitySummaries - city.location,
                            citySummaryErrors = if (result.isFailure) state.citySummaryErrors + city.location
                                else state.citySummaryErrors - city.location,
                        ) }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (canApplySummary(generation)) mutableState.update {
                    it.copy(citySummaryErrors = it.citySummaryErrors + it.loadingCitySummaries)
                }
            } finally {
                if (generation == citySummaryGeneration) mutableState.update { it.copy(loadingCitySummaries = emptySet()) }
            }
        }
    }

    private fun canApplySummary(generation: Long): Boolean =
        generation == citySummaryGeneration && citiesPageOpen

    private fun cancelSummaryRefresh() {
        ++citySummaryGeneration
        citySummaryJob?.cancel()
        mutableState.update { it.copy(loadingCitySummaries = emptySet()) }
    }

    private fun rememberSummary(summary: CityWeatherSummary, persist: Boolean = true) {
        val canonicalCity = storedCities.firstOrNull { it.location == summary.city.location } ?: return
        val existing = mutableState.value.citySummaries[summary.city.location]
        if (existing != null && (existing.fetchedAt > summary.fetchedAt ||
                (existing.fetchedAt == summary.fetchedAt && !existing.isStale && summary.isStale))) return
        mutableState.update { it.copy(citySummaries = it.citySummaries + (summary.city.location to summary.copy(city = canonicalCity))) }
        if (persist) persistSummariesSoon()
    }

    private fun persistSummariesSoon() {
        summaryPersistJob?.cancel()
        summaryPersistJob = viewModelScope.launch {
            delay(250)
            val allowed = storedCities.map(City::location).toSet()
            val cards = mutableState.value.citySummaries.filterKeys { it in allowed }.values.toList()
            try { persistenceMutex.withLock { withContext(Dispatchers.IO) { summaryStore.save(cards) } } }
            catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            settingsReady.await()
            mutableState.update { it.copy(temperatureUnit = unit) }
            persist { store.setUnit(unit) }
        }
    }

    /** Foreground location only. The activity owns permission and sensor acquisition. */
    fun useLocation(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            reportLocationError("位置坐标无效，请重试。")
            return
        }
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            settingsReady.await()
            mutableState.update { it.copy(lastLocationMessage = "正在识别当前位置…") }
            try {
                val coordinates = String.format(Locale.ROOT, "%.2f,%.2f", longitude, latitude)
                val city = repository.search(coordinates).firstOrNull()
                    ?: City(coordinates, "当前位置", lat = String.format(Locale.ROOT, "%.2f", latitude),
                        lon = String.format(Locale.ROOT, "%.2f", longitude))
                if (storedCities.none { it.location == city.location } && storedCities.size >= WeatherStore.MAX_CITIES) {
                    mutableState.update { it.copy(lastLocationMessage = "已达到 ${WeatherStore.MAX_CITIES} 个城市，请先删除一座再使用定位。") }
                    return@launch
                }
                selectCityAndPersist(city, fromLocation = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(lastLocationMessage = "无法识别位置：${message(error)}") }
            }
        }
    }

    fun reportLocationError(message: String) {
        cancelLocationLookup()
        mutableState.update { it.copy(lastLocationMessage = message) }
    }
    private suspend fun persistCities(cities: List<City>, selected: City) {
        // Capture at enqueue time. The same mutex/transaction writes cities and their marker,
        // so a later removal or location result is never overwritten by a separate old marker write.
        val located = mutableState.value.locatedCityLocation
        persist { store.setCities(cities, selected, located) }
    }

    private suspend fun persist(block: suspend () -> Unit) {
        try { persistenceMutex.withLock { withContext(Dispatchers.IO) { block() } } } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update { it.copy(errorMessage = "设置暂时无法保存，请检查设备存储空间。") }
        }
    }

    private fun message(error: Exception): String = when (error) {
        is UnknownHostException -> "网络不可用，请检查网络连接后重试。"
        is SocketTimeoutException -> "天气查询超时，请重试。"
        is JSONException -> "天气服务数据格式有误，请稍后重试。"
        is IllegalArgumentException -> "天气服务地址配置有误，请检查 WEATHER_BASE_URL。"
        is WeatherServiceException -> error.message ?: "天气服务暂时不可用。"
        is IOException -> "无法连接天气服务，请检查网络后重试。"
        else -> "暂时无法获取天气，请重试。"
    }

    override fun onCleared() {
        repository.close()
    }

}
