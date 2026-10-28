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
    val searchErrorMessage: String? = null,
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
    private val mutableState = MutableStateFlow(WeatherUiState(isDemo = BuildConfig.WEATHER_BASE_URL.isBlank(),
        backendConfigured = repository.configured))
    val uiState: StateFlow<WeatherUiState> = mutableState.asStateFlow()
    private val settingsReady = CompletableDeferred<Unit>()
    private var weatherJob: Job? = null
    private var searchJob: Job? = null
    private var locationJob: Job? = null
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
                    temperatureUnit = saved.unit, isDemo = saved.isDemo) }
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
        viewModelScope.launch {
            settingsReady.await()
            locationJob?.cancel()
            val current = mutableState.value
            if (current.selectedCity.location == city.location && (current.snapshot != null || weatherJob?.isActive == true)) return@launch
            if (current.isDemo) {
                val scenarioCity = DefaultCities.demoCatalog.firstOrNull { it.location == city.location } ?: return@launch
                demoSelection = scenarioCity
                mutableState.update { it.copy(selectedCity = scenarioCity, snapshot = null, errorMessage = null) }
                loadWeather(force = false)
                return@launch
            }
            val cities = if (current.cities.any { it.location == city.location }) current.cities else
                (current.cities + city).take(WeatherStore.MAX_CITIES)
            if (cities.none { it.location == city.location }) {
                mutableState.update { it.copy(errorMessage = "最多可保存 ${WeatherStore.MAX_CITIES} 个城市。") }
                return@launch
            }
            mutableState.update { it.copy(selectedCity = city, cities = cities, snapshot = null, errorMessage = null) }
            liveCities = cities
            liveSelection = city
            loadWeather(force = false)
            persistCities(cities, city)
        }
    }

    fun addCity(city: City) = selectCity(city)

    fun removeCity(city: City) {
        viewModelScope.launch {
            settingsReady.await()
            val current = mutableState.value
            if (current.isDemo) return@launch
            if (current.cities.size <= 1) {
                mutableState.update { it.copy(errorMessage = "请至少保留一个城市。") }
                return@launch
            }
            val cities = current.cities.filterNot { it.location == city.location }
            val selectionChanged = current.selectedCity.location == city.location
            if (selectionChanged) locationJob?.cancel()
            val selection = if (selectionChanged) cities.first() else current.selectedCity
            liveCities = cities
            liveSelection = selection
            mutableState.update { it.copy(cities = cities, selectedCity = selection,
                snapshot = if (selectionChanged) null else it.snapshot, errorMessage = null) }
            if (selectionChanged) loadWeather(force = false)
            persistCities(cities, selection)
        }
    }

    fun refresh() {
        viewModelScope.launch { settingsReady.await(); loadWeather(force = true) }
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
                    mutableState.update { it.copy(snapshot = displayed, isLoading = false, isRefreshing = true) }
                }
                if (!demo) repositoryResult.exceptionOrNull()?.let { throw it }
                val snapshot = repository.weather(city, demo)
                if (generation != weatherGeneration) return@launch
                mutableState.update { it.copy(snapshot = snapshot, isLoading = false, errorMessage = null) }
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
            isSearching = trimmed.isNotBlank(), searchErrorMessage = null) }
        if (trimmed.isBlank()) return
        searchJob = viewModelScope.launch {
            settingsReady.await()
            delay(350)
            try {
                val demo = mutableState.value.isDemo
                if (!demo) repositoryResult.exceptionOrNull()?.let { throw it }
                val cities = repository.search(trimmed, demo)
                mutableState.update { it.copy(searchResults = cities, isSearching = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(isSearching = false, searchErrorMessage = message(error)) }
            }
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
            locationJob?.cancel()
            searchJob?.cancel()
            mutableState.update { it.copy(isDemo = enabled,
                cities = if (enabled) DefaultCities.demoCatalog else liveCities,
                selectedCity = if (enabled) demoSelection else liveSelection,
                snapshot = null, searchResults = emptyList(),
                isSearching = false, searchErrorMessage = null, lastLocationMessage = null, errorMessage = null) }
            loadWeather(force = false)
            setSearchQuery(mutableState.value.searchQuery)
            persist { store.setDemo(enabled) }
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
                mutableState.update { it.copy(lastLocationMessage = "已定位到${city.name}") }
                selectCity(city)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(lastLocationMessage = "无法识别位置：${message(error)}") }
            }
        }
    }

    fun reportLocationError(message: String) { mutableState.update { it.copy(lastLocationMessage = message) } }
    fun reportServiceAccessDenied(message: String) {
        weatherJob?.cancel()
        ++weatherGeneration
        mutableState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = message) }
    }

    private suspend fun persistCities(cities: List<City>, selected: City) =
        persist { store.setCities(cities, selected) }

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
