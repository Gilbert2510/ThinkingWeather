package com.thinkingweather.android.data

import com.thinkingweather.android.model.City
import com.thinkingweather.android.model.DefaultCities
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class CityWeatherSummaryTest {
    @Test fun currentOnlyResponsePreservesCacheTimeAndAttribution() {
        val summary = WeatherJson.currentSummary("""{"now":{"temp":"21.6","text":"阴","icon":"104"},"stale":true,"cacheAgeSeconds":90,"fetchedAt":"2026-10-29T02:00:00Z","refer":{"sources":["QWeather"],"license":["Weather terms"]}}""", DefaultCities.shanghai)
        assertEquals("21.6", summary.temp)
        assertEquals("阴", summary.text)
        assertEquals("104", summary.icon)
        assertEquals(1793239200000L, summary.fetchedAt)
        assertTrue(summary.isStale)
        assertEquals(listOf("QWeather"), summary.sources)
        assertEquals(listOf("Weather terms"), summary.licenses)
        assertEquals(summary, WeatherJson.cachedCurrentSummary(WeatherJson.encodeCurrentSummary(summary)))
    }

    @Test fun missingFetchTimeUsesActualCacheAgeRatherThanLookingFresh() {
        val summary = WeatherJson.currentSummary("""{"now":{"temp":"13"},"cacheAgeSeconds":200}""", DefaultCities.beijing, 300_000)
        assertEquals(100_000L, summary.fetchedAt)
        assertFalse(summary.isFresh(300_000))
        assertEquals("999", summary.icon)
    }

    @Test(expected = JSONException::class) fun invalidCurrentTemperatureIsRejected() {
        WeatherJson.currentSummary("""{"now":{"temp":"NaN"}}""", DefaultCities.beijing)
    }

    @Test fun freshnessExcludesStaleExpiredAndFutureDatedObservations() {
        val value = card(DefaultCities.beijing, 100_000)
        assertTrue(value.isFresh(100_000))
        assertTrue(value.isFresh(219_999))
        assertFalse(value.isFresh(220_000))
        assertFalse(value.isFresh(99_999))
        assertFalse(value.copy(isStale = true).isFresh(100_000))
    }

    @Test fun offlineAndServiceErrorsHaveDifferentSearchPresentation() {
        val offline = citySearchNotice(WeatherOfflineException(), false)
        assertTrue(offline.offline)
        assertEquals("天气服务不可用，请稍后重试", offline.message)
        val unavailable = citySearchNotice(WeatherServiceException("upstream failed"), false)
        assertFalse(unavailable.offline)
        assertFalse(unavailable.message.contains("upstream"))
        assertTrue(citySearchNotice(WeatherServiceException("disconnected mid-request"), true).offline)
    }

    @Test fun incompleteLatinQueryWaitsButChineseSingleCharacterIsSearchable() {
        assertTrue(shouldWaitForLongerCityQuery("c"))
        assertTrue(shouldWaitForLongerCityQuery("S"))
        assertFalse(shouldWaitForLongerCityQuery("重"))
        assertFalse(shouldWaitForLongerCityQuery("cq"))
        assertFalse(shouldWaitForLongerCityQuery(""))
    }

    @Test(timeout = 5000) fun requestsAreLimitedToTwoAndDuplicateCitiesAreNotRepeated() = runBlocking {
        val started = Channel<City>(Channel.UNLIMITED)
        val releases = Channel<Unit>(Channel.UNLIMITED)
        var active = 0
        var maximum = 0
        val results = mutableListOf<City>()
        val loader = CitySummaryLoader(fetch = { city ->
            active++
            maximum = maxOf(maximum, active)
            started.send(city)
            try { releases.receive(); card(city) } finally { active-- }
        })
        val pending = launch {
            loader.load(DefaultCities.initial + DefaultCities.beijing) { city, result ->
                assertTrue(result.isSuccess)
                results += city
            }
        }
        started.receive(); started.receive()
        assertNull(withTimeoutOrNull(50) { started.receive() })
        repeat(DefaultCities.initial.size) { releases.send(Unit) }
        pending.join()
        assertEquals(2, maximum)
        assertEquals(0, active)
        assertEquals(DefaultCities.initial.toSet(), results.toSet())
        assertEquals(DefaultCities.initial.size, results.size)
    }

    @Test(timeout = 5000) fun oneFailedCityDoesNotPreventOtherCardsFromLoading() = runBlocking {
        val results = mutableMapOf<String, Result<CityWeatherSummary>>()
        CitySummaryLoader({ city ->
            if (city == DefaultCities.shanghai) throw WeatherServiceException("unavailable")
            card(city)
        }).load(DefaultCities.initial) { city, result -> results[city.location] = result }
        assertEquals(3, results.size)
        assertTrue(results.getValue(DefaultCities.shanghai.location).isFailure)
        assertTrue(results.getValue(DefaultCities.beijing.location).isSuccess)
        assertTrue(results.getValue(DefaultCities.hangzhou.location).isSuccess)
    }

    @Test(timeout = 5000) fun cancellingThePageCancelsActiveAndQueuedRequestsWithoutDeliveringResults() = runBlocking {
        val started = Channel<Unit>(Channel.UNLIMITED)
        var active = 0
        var delivered = 0
        val loader = CitySummaryLoader({
            active++
            started.send(Unit)
            try { awaitCancellation() } finally { active-- }
        })
        val pending = launch { loader.load(DefaultCities.initial) { _, _ -> delivered++ } }
        started.receive(); started.receive()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertEquals(0, active)
        assertEquals(0, delivered)
        assertNull(withTimeoutOrNull(50) { started.receive() })
    }

    private fun card(city: City, time: Long = 100_000) =
        CityWeatherSummary(city, "20", "晴", "100", time)
}
