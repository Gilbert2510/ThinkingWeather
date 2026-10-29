package com.thinkingweather.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thinkingweather.android.data.*
import com.thinkingweather.android.model.*
import com.thinkingweather.android.network.DevelopmentGateway
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
    val isDemo: Boolean = false,
    val backendConfigured: Boolean = false,
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

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val repositoryResult = runCatching {
        WeatherRepository(BuildConfig.WEATHER_BASE_URL, BuildConfig.DEBUG,
            isEmulator = {
                DevelopmentGateway.isEmulator(android.os.Build.FINGERPRINT, android.os.Build.MODEL,
                    android.os.Build.HARDWARE, android.os.Build.PRODUCT)
            },
            hasInternet = { NetworkMonitor.hasInternet(application) },
        ) {
            android.os.Build.VERSION.SDK_INT < 37 ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    application, "android.permission.ACCESS_LOCAL_NETWORK"
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    private val repository = repositoryResult.getOrElse { WeatherRepository("", BuildConfig.DEBUG) }
    private val store = WeatherStore(application, BuildConfig.WEATHER_BASE_URL.isNotBlank())
    private val summaryStore = CitySummaryStore(application)
    private val mutableState = MutableStateFlow(WeatherUiState(isDemo = BuildConfig.WEATHER_BASE_URL.isBlank(),
        backendConfigured = repository.configured))
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
    // Preview browsing never replaces or persists the user's real favourites.
    private var liveCities = DefaultCities.initial
    private var liveSelection = DefaultCities.beijing
    private var demoSelection = DefaultCities.beijing

    init {
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.read() }
                liveCities = saved.cities
                liveSelection = saved.selectedCity
                demoSelection = DefaultCities.demoCatalog.firstOrNull { it.location == saved.selectedCity.location }
                    ?: DefaultCities.beijing
                mutableState.update { it.copy(
                    cities = if (saved.isDemo) DefaultCities.demoCatalog else liveCities,
                    selectedCity = if (saved.isDemo) demoSelection else liveSelection,
                    temperatureUnit = saved.unit, isDemo = saved.isDemo,
                    locatedCityLocation = saved.locatedCityLocation) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(
                    cities = if (it.isDemo) DefaultCities.demoCatalog else liveCities,
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

    /** Location calls this in its own job, so a new lookup, manual selection or mode change cancels it. */
    private suspend fun selectCityAndPersist(city: City, fromLocation: Boolean) {
        val current = mutableState.value
        if (current.isDemo) {
            if (fromLocation) return
            if (current.selectedCity.location == city.location && (current.snapshot != null || weatherJob?.isActive == true)) return
            val scenarioCity = DefaultCities.demoCatalog.firstOrNull { it.location == city.location } ?: return
            demoSelection = scenarioCity
            mutableState.update { it.copy(selectedCity = scenarioCity, snapshot = null, errorMessage = null) }
            loadWeather(force = false)
            return
        }
        val cities = if (current.cities.any { it.location == city.location })
            current.cities.map { if (it.location == city.location) city else it } else
            (current.cities + city).take(WeatherStore.MAX_CITIES)
        if (cities.none { it.location == city.location }) {
            mutableState.update { it.copy(errorMessage = "最多可保存 ${WeatherStore.MAX_CITIES} 个城市。") }
            return
        }
        val reuseWeather = current.selectedCity.location == city.location && (current.snapshot != null || weatherJob?.isActive == true)
        if (reuseWeather && city == current.selectedCity && cities == current.cities && !fromLocation) return
        liveCities = cities
        liveSelection = city
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
            if (current.isDemo) return@launch
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
            liveCities = cities
            liveSelection = selection
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
            if (current.isDemo) return@launch
            val cities = orderedSavedCities(current.cities, locations)
            if (cities == current.cities) return@launch
            liveCities = cities
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
            // The activity also calls refresh after granting local-network access.
            if (citiesPageOpen) { cancelSummaryRefresh(); refreshCitySummaries() }
        }
    }

    private fun loadWeather(force: Boolean) {
        weatherJob?.cancel()
        val generation = ++weatherGeneration
        val city = mutableState.value.selectedCity
        val demo = mutableState.value.isDemo
        mutableState.update { it.copy(isLoading = it.snapshot == null, isRefreshing = it.snapshot != null, errorMessage = null) }
        weatherJob = viewModelScope.launch {
            try {
                // Preview scenarios are cheap and fixed. Never reuse old generated
                // preview caches that may predate the current city/weather mapping.
                val cached = if (demo || (force && mutableState.value.snapshot != null)) null
                    else withContext(Dispatchers.IO) { store.cached(city, false) }
                if (generation != weatherGeneration) return@launch
                if (cached != null) {
                    // Persisted live data is provisional until the gateway confirms its current feeds.
                    val displayed = cached.copy(isStale = true)
                    mutableState.update { it.copy(snapshot = displayed.copy(city = it.selectedCity), isLoading = false, isRefreshing = true) }
                    rememberSummary(CityWeatherSummary.from(displayed))
                }
                if (!demo) repositoryResult.exceptionOrNull()?.let { throw it }
                val received = repository.weather(city, demo)
                if (generation != weatherGeneration) return@launch
                val snapshot = received.copy(city = mutableState.value.selectedCity)
                mutableState.update { it.copy(snapshot = snapshot, isLoading = false, errorMessage = null) }
                if (!demo) rememberSummary(CityWeatherSummary.from(snapshot))
                try {
                    if (!demo) withContext(Dispatchers.IO) { store.save(snapshot) }
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
            val demo = mutableState.value.isDemo
            val savedMatches = if (demo) emptyList() else liveCities.filter {
                it.name.contains(trimmed, ignoreCase = true) || it.description.contains(trimmed, ignoreCase = true)
            }
            if (!demo && repository.networkUnavailable) {
                mutableState.update { it.copy(isSearching = false, searchResults = savedMatches,
                    searchErrorMessage = "天气服务不可用，请稍后重试", searchErrorIsOffline = true) }
                return@launch
            }
            if (!demo && shouldWaitForLongerCityQuery(trimmed)) {
                mutableState.update { it.copy(isSearching = false, searchResults = savedMatches,
                    searchHint = "请继续输入拼音或城市名称") }
                return@launch
            }
            delay(350)
            try {
                if (!demo) repositoryResult.exceptionOrNull()?.let { throw it }
                val cities = repository.search(trimmed, demo)
                mutableState.update { it.copy(searchResults = cities, isSearching = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val notice = citySearchNotice(error, !demo && repository.networkUnavailable)
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
            if (mutableState.value.isDemo || !citiesPageOpen) return@launch
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
                if (liveCities.any { mutableState.value.citySummaries[it.location] == null }) {
                    val cached = withContext(Dispatchers.IO) { store.cachedAll(false) }
                    if (!canApplySummary(generation)) return@launch
                    cached.forEach { rememberSummary(CityWeatherSummary.from(it), persist = false) }
                }
                mutableState.value.snapshot?.takeUnless { it.isDemo }?.let {
                    rememberSummary(CityWeatherSummary.from(it), persist = false)
                }
                val now = System.currentTimeMillis()
                val pending = liveCities.filter { mutableState.value.citySummaries[it.location]?.isFresh(now) != true }
                val keys = pending.map(City::location).toSet()
                mutableState.update { state -> state.copy(
                    citySummaries = state.citySummaries.mapValues { (_, value) ->
                        if (value.isFresh(now)) value else value.copy(isStale = true)
                    }, loadingCitySummaries = keys, citySummaryErrors = state.citySummaryErrors - keys,
                ) }
                if (pending.isEmpty() || repository.networkUnavailable || !repository.configured) return@launch
                repositoryResult.exceptionOrNull()?.let { throw it }
                CitySummaryLoader(repository::currentSummary).load(pending) { city, result ->
                    if (canApplySummary(generation) && liveCities.any { it.location == city.location }) {
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
        generation == citySummaryGeneration && citiesPageOpen && !mutableState.value.isDemo

    private fun cancelSummaryRefresh() {
        ++citySummaryGeneration
        citySummaryJob?.cancel()
        mutableState.update { it.copy(loadingCitySummaries = emptySet()) }
    }

    private fun rememberSummary(summary: CityWeatherSummary, persist: Boolean = true) {
        val canonicalCity = liveCities.firstOrNull { it.location == summary.city.location } ?: return
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
            val allowed = liveCities.map(City::location).toSet()
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

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsReady.await()
            if (!enabled && !repository.configured) {
                mutableState.update { it.copy(errorMessage = "尚未配置天气服务。请按 README 设置 WEATHER_BASE_URL 后重新构建。") }
                return@launch
            }
            if (enabled == mutableState.value.isDemo) return@launch
            cancelLocationLookup()
            searchJob?.cancel()
            cancelSummaryRefresh()
            mutableState.update { it.copy(isDemo = enabled,
                cities = if (enabled) DefaultCities.demoCatalog else liveCities,
                selectedCity = if (enabled) demoSelection else liveSelection,
                snapshot = null, searchResults = emptyList(),
                isSearching = false, searchErrorMessage = null, searchErrorIsOffline = false, searchHint = null,
                lastLocationMessage = null, errorMessage = null) }
            loadWeather(force = false)
            setSearchQuery(mutableState.value.searchQuery)
            persist { store.setDemo(enabled) }
            if (citiesPageOpen && !enabled) refreshCitySummaries()
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
            if (mutableState.value.isDemo) {
                mutableState.update { it.copy(lastLocationMessage = "演示模式不使用真实位置，请手动选择演示城市。") }
                return@launch
            }
            mutableState.update { it.copy(lastLocationMessage = "正在识别当前位置…") }
            try {
                repositoryResult.exceptionOrNull()?.let { throw it }
                val coordinates = String.format(Locale.ROOT, "%.2f,%.2f", longitude, latitude)
                val city = repository.search(coordinates, isDemo = false).firstOrNull()
                    ?: City(coordinates, "当前位置", lat = String.format(Locale.ROOT, "%.2f", latitude),
                        lon = String.format(Locale.ROOT, "%.2f", longitude))
                if (liveCities.none { it.location == city.location } && liveCities.size >= WeatherStore.MAX_CITIES) {
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
    fun reportServiceAccessDenied(message: String) {
        weatherJob?.cancel()
        ++weatherGeneration
        mutableState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = message) }
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
