package com.thinkingweather.android.network

import org.junit.Assert.*
import org.junit.Test

class WeatherMapPolicyTest {
    @Test fun inAppNavigationStaysOnTheOfficialHttpsMap() {
        assertTrue(isOfficialMapUrl(OFFICIAL_WEATHER_MAP))
        assertTrue(isOfficialMapUrl("https://map.qweather.com:443/?lang=zh"))
        for (url in listOf("http://map.qweather.com", "https://map.qweather.com.evil.test", "https://evil.test/map.qweather.com",
            "https://user@map.qweather.com", "https://map.qweather.com:444", "file:///map.qweather.com", "javascript:alert(1)", "not a URL")) {
            assertFalse(url, isOfficialMapUrl(url))
        }
    }

    @Test fun externalLinksCannotLaunchFileJavascriptOrIntentSchemes() {
        assertTrue(isExternalHttpsUrl("https://www.qweather.com/"))
        for (url in listOf("intent://map", "javascript:alert(1)", "file:///data", "http://example.com", "https://user:pass@example.com")) {
            assertFalse(url, isExternalHttpsUrl(url))
        }
    }
}
