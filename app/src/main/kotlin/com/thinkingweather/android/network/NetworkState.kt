package com.thinkingweather.android.network

/** A usable default route; the request itself determines whether the weather host is reachable. */
internal enum class NetworkState { OFFLINE, CELLULAR, OTHER }

internal fun networkState(hasInternet: Boolean, captivePortal: Boolean, usesCellular: Boolean): NetworkState = when {
    !hasInternet || captivePortal -> NetworkState.OFFLINE
    usesCellular -> NetworkState.CELLULAR
    else -> NetworkState.OTHER
}

internal fun networkNotice(state: NetworkState): String? = when (state) {
    NetworkState.OFFLINE -> "当前未连接网络"
    NetworkState.CELLULAR -> "请注意，当前正在使用移动流量"
    NetworkState.OTHER -> null
}
