package com.thinkingweather.android.data

import com.thinkingweather.android.model.DefaultCities
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class WeatherNetworkTest {
    @Test fun offlineServiceFailsBeforeOpeningAConnection() = runBlocking {
        val repository = WeatherRepository("https://weather.example.invalid", hasInternet = { false })
        try { try { repository.weather(DefaultCities.beijing); fail("Offline must be an explicit error") }
            catch (error: WeatherOfflineException) { assertEquals("当前未连接网络", error.message) }
        } finally { repository.close() }
    }

    @Test(timeout = 10000) fun rawV1FeedsAreNormalizedAndOptionalFailureDoesNotReplaceRealWeather() = runBlocking {
        withService(dispatch = { request -> if ("airquality" in request.path.orEmpty()) MockResponse().setResponseCode(403) else fixture(request) }) { server, repository ->
            val result = repository.weather(DefaultCities.beijing)
            assertEquals("13", result.now.temp)
            assertEquals("18", result.now.windSpeed)
            assertEquals(1, result.hourly.size)
            assertEquals("2026-11-02", result.daily.single().fxDate)
            assertTrue(result.partialFailures.contains("air_unavailable"))
            assertNull(result.air)
            assertEquals(1, result.indices.size)
            assertEquals(6, server.requestCount)
        }
    }

    @Test(timeout = 10000) fun cityCardsShareOneCurrentOnlyFlightAndReuseItsCache() = runBlocking {
        withService { server, repository ->
            val results = (1..8).map { async(Dispatchers.IO) { repository.currentSummary(DefaultCities.beijing) } }.awaitAll()
            assertTrue(results.all { it.temp == "13" })
            assertEquals(1, server.requestCount)
            assertTrue(server.takeRequest().path.orEmpty().startsWith("/weather/v1/current/39.90/116.41?"))
            repository.currentSummary(DefaultCities.beijing)
            assertEquals(1, server.requestCount)
        }
    }

    @Test(timeout = 10000) fun alertsWorkWithDeployedLangOnlyAllowlistAndEmptyResultsAreNotFailures() = runBlocking {
        withService(dispatch = { request ->
            if ("/weatheralert/" in request.path.orEmpty() && request.requestUrl?.queryParameterNames != setOf("lang"))
                MockResponse().setResponseCode(400).setBody("""{"error":"invalid_query"}""")
            else fixture(request)
        }) { server, repository ->
            val result = repository.weather(DefaultCities.beijing)
            assertTrue(result.warnings.isEmpty())
            assertFalse(result.partialFailures.contains("warnings_unavailable"))
            val requests = (1..server.requestCount).map { server.takeRequest() }
            assertEquals(setOf("lang"), requests.single { "/weatheralert/" in it.path.orEmpty() }.requestUrl?.queryParameterNames)
        }
    }

    @Test(timeout = 10000) fun HTTP429StopsOtherResourcesAndHonorsRetryAfter() = runBlocking {
        val clock = AtomicLong(1_800_000_000_000)
        val limited = AtomicInteger()
        withService(clock = clock, dispatch = { request -> if (limited.getAndIncrement() == 0) MockResponse().setResponseCode(429).setHeader("Retry-After", "60") else fixture(request) }) { server, repository ->
            try { repository.weather(DefaultCities.beijing); fail("429 must be explicit") } catch (error: WeatherHttpException) { assertEquals(429, error.status) }
            try { repository.currentSummary(DefaultCities.shanghai); fail("Shared cooldown must block new resources") } catch (error: WeatherHttpException) { assertEquals(429, error.status) }
            assertEquals(1, server.requestCount)
            clock.addAndGet(60_001)
            assertEquals("13", repository.currentSummary(DefaultCities.shanghai).temp)
            assertEquals(2, server.requestCount)
        }
    }

    @Test(timeout = 10000) fun business429InsideHTTP200IsNotTreatedAsWeather() = runBlocking {
        withService(dispatch = { MockResponse().setBody("""{"code":"429","now":{"temp":"25"}}""") }) { server, repository ->
            try { repository.weather(DefaultCities.beijing); fail("Provider business errors must be inspected") } catch (error: WeatherHttpException) { assertEquals(429, error.status) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test(timeout = 10000) fun v1PlanCompatibilityCanUseV7WithoutRetryingOnRateLimits() = runBlocking {
        withService(dispatch = { request -> if ("/weather/v1/current/" in request.path.orEmpty()) MockResponse().setResponseCode(403)
            else if (request.requestUrl?.encodedPath == "/v7/weather/now") MockResponse().setBody("""{"code":"200","now":{"temp":"21","text":"阴"}}""") else fixture(request) }) { server, repository ->
            assertEquals("21", repository.currentSummary(DefaultCities.beijing).temp)
            assertEquals("21", repository.currentSummary(DefaultCities.beijing).temp)
            assertEquals(2, server.requestCount)
        }
    }

    @Test(timeout = 10000) fun failedExpiredResourceCanUseOnlyBoundedStaleData() = runBlocking {
        val clock = AtomicLong(1_800_000_000_000)
        val count = AtomicInteger()
        withService(clock = clock, dispatch = { request -> if (count.getAndIncrement() == 0) fixture(request) else MockResponse().setResponseCode(503) }) { _, repository ->
            val fresh = repository.currentSummary(DefaultCities.beijing)
            clock.addAndGet(120_001)
            val stale = repository.currentSummary(DefaultCities.beijing)
            assertTrue(stale.isStale)
            assertEquals(fresh.fetchedAt, stale.fetchedAt)
            clock.addAndGet(900_001)
            try { repository.currentSummary(DefaultCities.beijing); fail("Old observations must expire") } catch (error: WeatherHttpException) { assertEquals(503, error.status) }
        }
    }

    @Test(timeout = 10000) fun serverAgePreservesTheActualFetchAge() = runBlocking {
        val clock = AtomicLong(1_800_000_000_000)
        withService(clock = clock, dispatch = { request -> fixture(request).setHeader("Age", "60") }) { _, repository ->
            assertEquals(clock.get() - 60_000, repository.currentSummary(DefaultCities.beijing).fetchedAt)
        }
    }

    @Test(timeout = 10000) fun cityQueryIsEncodedAndAdministrativeNameIsCompleted() = runBlocking {
        withService { server, repository ->
            assertEquals("沙坪坝区", repository.search("沙坪坝").single().name)
            val request = server.takeRequest()
            assertEquals("/geo/v2/city/lookup", request.requestUrl?.encodedPath)
            assertEquals("沙坪坝", request.requestUrl?.queryParameter("location"))
            assertNull(request.getHeader("X-QW-Api-Key"))
        }
    }

    @Test(timeout = 10000) fun noCityMatchAndPrivateDiagnosticsStaySeparate() = runBlocking {
        withService(dispatch = { MockResponse().setBody("""{"code":"404","private":"private-diagnostic"}""") }) { _, repository ->
            assertTrue(repository.search("UnknownCity").isEmpty())
            try { repository.currentSummary(DefaultCities.beijing); fail("Missing current is an error") } catch (error: WeatherHttpException) {
                assertFalse(error.message.orEmpty().contains("private-diagnostic"))
            }
        }
    }

    @Test(timeout = 10000) fun cancellationClosesTheCallAndDoesNotPoisonTheNextFlight() = runBlocking {
        val count = AtomicInteger()
        withService(dispatch = { request -> if (count.getAndIncrement() == 0) MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) else fixture(request) }) { server, repository ->
            val pending = async(Dispatchers.IO) { repository.currentSummary(DefaultCities.beijing) }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertEquals("13", repository.currentSummary(DefaultCities.beijing).temp)
        }
    }

    @Test(timeout = 10000) fun simultaneousSnapshotsNeverExceedTwoNetworkRequests() = runBlocking {
        val active = AtomicInteger(); val peak = AtomicInteger()
        withService(dispatch = { request ->
            val current = active.incrementAndGet(); peak.updateAndGet { maxOf(it, current) }
            try { Thread.sleep(30); fixture(request) } finally { active.decrementAndGet() }
        }) { _, repository ->
            listOf(DefaultCities.beijing, DefaultCities.shanghai, DefaultCities.hangzhou).map { async(Dispatchers.IO) { repository.weather(it) } }.awaitAll()
            assertTrue(peak.get() <= 2)
        }
    }

    private suspend fun withService(
        clock: AtomicLong = AtomicLong(java.time.Instant.parse("2026-11-02T04:00:00Z").toEpochMilli()), dispatch: (RecordedRequest) -> MockResponse = ::fixture,
        block: suspend (MockWebServer, WeatherRepository) -> Unit,
    ) {
        val server = MockWebServer()
        val client = OkHttpClient.Builder().build()
        val handler = dispatch
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = handler(request) }
        server.start()
        // Test source only: production still validates and constructs HTTPS URLs with no loopback exception.
        val factory = Call.Factory { request -> client.newCall(request.newBuilder().url(request.url.newBuilder()
            .scheme("http").host(server.hostName).port(server.port).build()).build()) }
        val repository = WeatherRepository("https://relay.example.invalid", callFactory = factory, timeMillis = clock::get)
        try { block(server, repository) } finally {
            repository.close(); client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.shutdown()
        }
    }

    companion object {
        private fun fixture(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath.orEmpty()
            val body = when {
                "/weather/v1/current/" in path -> CURRENT
                "/weather/v1/hourly/" in path -> """{"hours":[{"forecastTime":"2026-11-02T12:00:00+08:00","temperature":{"value":14,"unit":"°C"}}]}"""
                "/weather/v1/daily/" in path -> """{"days":[{"forecastStartTime":"2026-11-02T00:00:00+08:00","temperatureMax":{"value":20,"unit":"°C"},"temperatureMin":{"value":10,"unit":"°C"}}]}"""
                "/airquality/" in path -> """{"indexes":[{"code":"cn-mee","aqiDisplay":"30","category":"优"}]}"""
                "/weatheralert/" in path -> """{"metadata":{"zeroResult":true},"alerts":[]}"""
                path == "/v7/indices/1d" -> """{"code":"200","daily":[{"date":"2026-11-02","type":"3","name":"穿衣指数","level":"2","category":"较冷","text":"适当添加衣物"}]}"""
                path == "/geo/v2/city/lookup" -> """{"code":"200","location":[{"id":"101040106","name":"沙坪坝","adm1":"重庆市","adm2":"重庆","lat":"29.54","lon":"106.45"}]}"""
                else -> """{"code":"404"}"""
            }
            return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        }
        private const val CURRENT = """{"temperature":{"value":13,"unit":"°C"},"wind":{"speed":{"value":5,"unit":"m/s"}},"condition":{"text":"晴","code":"100"}}"""
    }
}
