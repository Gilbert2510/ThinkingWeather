package com.thinkingweather.android.data

import com.thinkingweather.android.model.DefaultCities
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class WeatherJsonTest {
    @Test fun missingOptionalFeedsDoNotInventWeather() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"16","text":"晴"}}""", DefaultCities.beijing, 123)
        assertEquals("16", result.now.temp)
        assertEquals("", result.now.obsTime)
        assertTrue(result.hourly.isEmpty())
        assertTrue(result.daily.isEmpty())
        assertTrue(result.warnings.isEmpty())
        assertNull(result.air)
        assertFalse(result.isDemo)
        assertEquals(123L, result.fetchedAt)
    }

    @Test(expected = JSONException::class) fun missingCurrentWeatherIsRejected() {
        WeatherJson.snapshot("""{"daily":[]}""", DefaultCities.beijing)
    }

    @Test(expected = JSONException::class) fun missingTemperatureIsRejected() {
        WeatherJson.snapshot("""{"now":{"temp":null}}""", DefaultCities.beijing)
    }

    @Test(expected = JSONException::class) fun nonfiniteTemperatureIsRejected() {
        WeatherJson.snapshot("""{"now":{"temp":"NaN"}}""", DefaultCities.beijing)
    }

    @Test(expected = JSONException::class) fun providerFailureIsRejected() {
        WeatherJson.snapshot("""{"code":"401","now":{"temp":"18"}}""", DefaultCities.beijing)
    }

    @Test fun citiesDeduplicateAndIgnoreIncompleteRecords() {
        val result = WeatherJson.cities("""{"location":[{"id":"a","name":"北京"},{"id":"a","name":"北京"},{"name":"缺少坐标"},{"id":"b","name":"上海","tz":null}]}""")
        assertEquals(listOf("北京", "上海"), result.map { it.name })
        assertEquals("Asia/Shanghai", result.last().tz)
    }

    @Test fun coordinateOnlyCityRemainsUsable() {
        val result = WeatherJson.cities("""{"location":[{"name":"当前位置","lon":"120.1536","lat":"30.2875"}]}""")
        assertEquals("120.1536,30.2875", result.single().location)
    }

    @Test fun airPrefersChinaStandardRatherThanQaqi() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"20"},"air":{"indexes":[{"code":"qaqi","aqiDisplay":"2"},{"code":"cn-mee","aqiDisplay":"43","category":"优"}],"pollutants":[{"code":"pm2p5","concentration":{"value":12.3}}]}}""", DefaultCities.beijing)
        assertEquals("43", result.air?.aqi)
        assertEquals("优", result.air?.category)
        assertEquals("12.3", result.air?.pm2p5)
    }

    @Test fun qaiOnlyDoesNotMasqueradeAsChineseAirIndex() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"20"},"air":{"indexes":[{"code":"qaqi","aqiDisplay":"2"}]}}""", DefaultCities.beijing)
        assertNull(result.air)
    }

    @Test fun pollutantUnitsSurviveApiParsingAndDiskRoundtripWithoutConversion() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"20"},"air":{"indexes":[{"code":"us-epa","aqiDisplay":"30","category":"Good"}],"pollutants":[{"code":"pm2p5","concentration":{"value":5.4,"unit":"μg/m3"}},{"code":"pm10","concentration":{"value":8,"unit":"μg/m3"}},{"code":"o3","concentration":{"value":0.02,"unit":"ppm"}},{"code":"co","concentration":{"value":0.4,"unit":"mg/m3"}}]}}""", DefaultCities.beijing)
        assertEquals("0.02", result.air?.o3)
        assertEquals("ppm", result.air?.o3Unit)
        assertEquals("μg/m3", result.air?.pm2p5Unit)
        assertEquals("mg/m3", result.air?.coUnit)
        assertEquals(result, WeatherJson.cachedSnapshot(WeatherJson.encodeSnapshot(result)))
    }

    @Test fun missingPollutantUnitRemainsUnknown() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"20"},"air":{"aqi":"20","o3":"0.02"}}""", DefaultCities.beijing)
        assertEquals("0.02", result.air?.o3)
        assertEquals("", result.air?.o3Unit)
    }

    @Test fun preservesServerCacheAgeAndActualFetchTime() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"20"},"fetchedAt":"2026-10-28T02:00:00Z","stale":true,"cacheAgeSeconds":3600,"partialFailures":["hourly"]}""", DefaultCities.beijing)
        assertEquals(1793152800000L, result.fetchedAt)
        assertTrue(result.isStale)
        assertEquals(3600L, result.serverCacheAgeSeconds)
        assertEquals(listOf("hourly"), result.partialFailures)
    }

    @Test fun fullCacheRoundtripPreservesDemoIdentityAndForecasts() {
        val fixture = DemoWeather.create(DefaultCities.hangzhou, 1793152800000L)
        assertEquals(fixture, WeatherJson.cachedSnapshot(WeatherJson.encodeSnapshot(fixture)))
    }

    @Test fun nullOptionalValuesStayBlank() {
        val result = WeatherJson.snapshot("""{"now":{"temp":"16","humidity":null},"daily":[{"tempMax":"20","sunrise":null}]}""", DefaultCities.beijing)
        assertEquals("", result.now.humidity)
        assertEquals("", result.daily.single().sunrise)
    }
}
