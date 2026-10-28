package com.thinkingweather.android.data

import com.thinkingweather.android.model.City
import com.thinkingweather.android.model.DefaultCities
import com.thinkingweather.android.model.WeatherSnapshot
import com.thinkingweather.android.network.DevelopmentGateway
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class WeatherServiceException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal class WeatherRepository(
    baseUrl: String,
    debug: Boolean,
    isEmulator: () -> Boolean = { false },
    private val hasInternet: () -> Boolean = { true },
    private val canAccessLocalNetwork: () -> Boolean = { true },
) {
    private val rawBaseUrl = baseUrl.trim()
    private val emulator = debug && isEmulator()
    private val base: HttpUrl? = rawBaseUrl.takeIf(String::isNotBlank)?.let {
        gatewayForDevice(validateBaseUrl(it, debug), debug, emulator)
    }
    private val localDevelopment = debug && DevelopmentGateway.isLocalHost(base?.host)
    val configured: Boolean get() = base != null
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    suspend fun search(query: String, isDemo: Boolean): List<City> = withContext(Dispatchers.IO) {
        if (isDemo) DefaultCities.search(query) else WeatherJson.cities(request("cities", "q", query))
    }

    suspend fun weather(city: City, isDemo: Boolean): WeatherSnapshot = withContext(Dispatchers.IO) {
        if (isDemo) DemoWeather.create(city) else WeatherJson.snapshot(request("weather", "location", city.location), city)
    }

    private suspend fun request(resource: String, key: String, value: String): String {
        val gateway = base ?: throw WeatherServiceException("尚未配置天气服务地址，请按 README 配置服务后重新构建。")
        if (localDevelopment) DevelopmentGateway.configurationError(gateway.host, emulator)
            ?.let { throw WeatherServiceException(it) }
        if (localDevelopment && !canAccessLocalNetwork()) {
            throw WeatherServiceException("连接本机调试天气服务需要附近设备权限，请在系统提示中允许后重试。")
        }
        // A USB/ADB-reversed gateway uses the computer's internet connection; the
        // handset itself may have no validated Wi-Fi or cellular internet access.
        if (!localDevelopment && !hasInternet()) throw WeatherServiceException("当前未连接网络")
        val url = gateway.newBuilder().addPathSegment("v1").addPathSegment(resource).addQueryParameter(key, value).build()
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (localDevelopment) WeatherServiceException(
                            DevelopmentGateway.connectionFailure(gateway.host, gateway.port, emulator), e,
                        ) else e
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            val body = response.body ?: throw WeatherServiceException("天气服务未返回内容。")
                            val source = body.source()
                            source.request(MAX_RESPONSE_BYTES + 1)
                            if (source.buffer.size > MAX_RESPONSE_BYTES) throw WeatherServiceException("天气服务响应过大。")
                            val text = source.readUtf8()
                            if (!response.isSuccessful) {
                                val message = when (response.code) {
                                    429 -> "查询过于频繁，请稍后重试。"
                                    401, 403 -> "天气服务认证失败，请联系服务管理员。"
                                    in 500..599 -> "天气服务暂时不可用，请稍后重试。"
                                    else -> "天气查询失败（${response.code}）。"
                                }
                                throw WeatherServiceException(message)
                            }
                            if (text.isBlank()) throw WeatherServiceException("天气服务返回了空内容。")
                            text
                        }
                    }
                    if (continuation.isActive) result.fold(
                        onSuccess = { continuation.resume(it) }, onFailure = { continuation.resumeWithException(it) })
                }
            })
        }
    }

    fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

        /** Emulator host access survives device restarts without a transient adb reverse mapping. */
        internal fun gatewayForDevice(url: HttpUrl, debug: Boolean, emulator: Boolean): HttpUrl =
            if (debug && emulator && !url.isHttps && DevelopmentGateway.isLoopbackHost(url.host))
                url.newBuilder().host("10.0.2.2").build()
            else url

        internal fun validateBaseUrl(raw: String, debug: Boolean): HttpUrl {
            val url = (raw.trimEnd('/') + "/").toHttpUrlOrNull()
                ?: throw IllegalArgumentException("天气服务地址必须是有效 URL。")
            val localDebugHost = debug && DevelopmentGateway.isLocalHost(url.host)
            require(url.isHttps || localDebugHost) { "天气服务必须使用 HTTPS；调试版仅允许本机 HTTP。" }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "天气服务地址不能包含账号、查询参数或片段。"
            }
            return url
        }
    }
}
