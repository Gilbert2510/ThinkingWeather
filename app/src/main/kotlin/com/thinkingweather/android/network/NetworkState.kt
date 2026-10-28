package com.thinkingweather.android.network

/** Validated internet access, rather than merely an enabled Wi-Fi radio. */
internal enum class NetworkState { OFFLINE, CELLULAR, OTHER }

internal fun networkState(hasInternet: Boolean, validated: Boolean, usesCellular: Boolean): NetworkState = when {
    !hasInternet || !validated -> NetworkState.OFFLINE
    usesCellular -> NetworkState.CELLULAR
    else -> NetworkState.OTHER
}

internal fun networkNotice(state: NetworkState): String? = when (state) {
    NetworkState.OFFLINE -> "当前未连接网络"
    NetworkState.CELLULAR -> "请注意，当前正在使用移动流量"
    NetworkState.OTHER -> null
}

/** Local development addresses are never accepted as release cleartext gateways. */
internal object DevelopmentGateway {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1")
    fun isLoopbackHost(host: String?): Boolean = host?.removeSurrounding("[", "]") in loopbackHosts
    fun isLocalHost(host: String?): Boolean = isLoopbackHost(host) || host == "10.0.2.2"

    fun isEmulator(fingerprint: String, model: String, hardware: String, product: String): Boolean =
        hardware in setOf("goldfish", "ranchu") ||
            product.contains("sdk", ignoreCase = true) || product.contains("emulator", ignoreCase = true) ||
            model.contains("sdk_gphone", ignoreCase = true) || model.contains("Android SDK", ignoreCase = true) ||
            (fingerprint.startsWith("generic") && product.startsWith("generic"))

    fun configurationError(host: String?, emulator: Boolean): String? =
        if (host == "10.0.2.2" && !emulator)
            "10.0.2.2 仅供 Android 模拟器访问电脑。真机调试请改用 127.0.0.1，并通过 USB 执行 adb reverse tcp:8080 tcp:8080；独立使用需配置 HTTPS 天气网关。"
        else null

    fun connectionFailure(host: String, port: Int, emulator: Boolean): String =
        if (emulator && host == "10.0.2.2")
            "无法连接电脑上的天气网关。模拟器通过 10.0.2.2 直连电脑，请启动天气后端并确认 $port 端口可用；Android 17 请允许本地网络权限。"
        else
            "无法连接电脑上的天气网关。请启动后端，保持 USB 调试连接，并执行 adb reverse tcp:$port tcp:$port；手机独立使用需配置 HTTPS 天气网关。"
}
