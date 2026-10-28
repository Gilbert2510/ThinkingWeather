package com.thinkingweather.android

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import com.thinkingweather.android.ui.WeatherApp
import com.thinkingweather.android.network.DevelopmentGateway
import com.thinkingweather.android.network.NetworkMonitor
import com.thinkingweather.android.network.networkNotice
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel
    private var locationJob: Job? = null
    private lateinit var networkMonitor: NetworkMonitor
    private var networkToast: Toast? = null
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) weatherViewModel.refresh()
        else weatherViewModel.reportServiceAccessDenied(
            "本机调试需要本地网络权限。请在系统的应用权限设置中允许本地网络访问后重试。"
        )
    }
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) resolveLocation()
        else weatherViewModel.reportLocationError("未获得大致位置权限，你仍可搜索并添加城市。")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        weatherViewModel = ViewModelProvider(this)[WeatherViewModel::class.java]
        networkMonitor = NetworkMonitor(applicationContext) { state ->
            networkToast?.cancel()
            networkToast = networkNotice(state)?.let { message ->
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).also(Toast::show)
            }
        }
        lifecycleScope.launch {
            weatherViewModel.uiState.map { it.selectedCity.location to it.isDemo }
                .distinctUntilChanged().drop(1).collect { locationJob?.cancel() }
        }
        lifecycleScope.launch {
            weatherViewModel.uiState.map { it.settingsLoaded && !it.isDemo && it.backendConfigured }
                .distinctUntilChanged().collect { needsNetwork ->
                    if (needsNetwork) requestLocalDevelopmentNetwork()
                }
        }
        setContent {
            WeatherApp(weatherViewModel, onRequestLocation = ::requestWeatherLocation)
        }
    }

    private fun requestLocalDevelopmentNetwork() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < 37) return
        val host = runCatching { java.net.URI(BuildConfig.WEATHER_BASE_URL).host }.getOrNull()
        if (DevelopmentGateway.isLocalHost(host) && ContextCompat.checkSelfPermission(
                this, "android.permission.ACCESS_LOCAL_NETWORK"
            ) != PackageManager.PERMISSION_GRANTED
        ) localNetworkPermission.launch("android.permission.ACCESS_LOCAL_NETWORK")
    }

    private fun requestWeatherLocation() {
        if (weatherViewModel.uiState.value.isDemo) {
            weatherViewModel.reportLocationError("示例预览不使用真实位置，请手动选择示例城市。")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) resolveLocation()
        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun resolveLocation() {
        locationJob?.cancel()
        if (weatherViewModel.uiState.value.isDemo) return
        locationJob = lifecycleScope.launch {
            val requestedCity = weatherViewModel.uiState.value.selectedCity.location
            val requestedMode = weatherViewModel.uiState.value.isDemo
            val manager = getSystemService(LocationManager::class.java)
            if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
                weatherViewModel.reportLocationError("系统定位已关闭，请开启定位或手动搜索城市。")
                return@launch
            }
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider == null) {
                weatherViewModel.reportLocationError("当前没有可用的定位服务，请手动选择城市。")
                return@launch
            }
            try {
                val location = withTimeoutOrNull(15_000) {
                    currentLocation(manager, provider)
                }
                val currentState = weatherViewModel.uiState.value
                if (currentState.selectedCity.location != requestedCity || currentState.isDemo != requestedMode) return@launch
                if (location == null) {
                    weatherViewModel.reportLocationError("暂时无法获取位置，请稍后重试或手动选择城市。")
                } else {
                    weatherViewModel.useLocation(location.latitude, location.longitude)
                }
            } catch (_: SecurityException) {
                weatherViewModel.reportLocationError("无法使用大致位置，请通过城市搜索查看天气。")
            } catch (_: IllegalArgumentException) {
                weatherViewModel.reportLocationError("定位服务暂不可用，请手动选择城市。")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onStop() {
        networkMonitor.stop()
        networkToast?.cancel()
        networkToast = null
        locationJob?.cancel()
        locationJob = null
        super.onStop()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager, provider, cancellation, ContextCompat.getMainExecutor(this)
            ) { location: Location? ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
}
