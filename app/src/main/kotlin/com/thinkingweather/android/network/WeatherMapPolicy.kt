package com.thinkingweather.android.network

import java.net.URI

internal const val OFFICIAL_WEATHER_MAP = "https://map.qweather.com/"

/** Only the provider's map can navigate inside the browser surface. */
internal fun isOfficialMapUrl(raw: String): Boolean = runCatching {
    val uri = URI(raw)
    uri.scheme.equals("https", true) && uri.host.equals("map.qweather.com", true) &&
        uri.userInfo == null && uri.port in listOf(-1, 443)
}.getOrDefault(false)

internal fun isExternalHttpsUrl(raw: String): Boolean = runCatching {
    val uri = URI(raw)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)
