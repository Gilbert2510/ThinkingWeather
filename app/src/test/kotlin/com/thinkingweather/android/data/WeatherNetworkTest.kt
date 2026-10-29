package com.thinkingweather.android.data

import com.thinkingweather.android.model.DefaultCities
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class WeatherNetworkTest {
    @Test(timeout = 2000) fun physicalPhoneRejectsEmulatorAliasBeforeConnecting() = runBlocking {
        val repository = WeatherRepository("http://10.0.2.2:8080", true, isEmulator = { false })
        try {
            try {
                repository.weather(DefaultCities.beijing, false)
                fail("An emulator-only gateway cannot be used by a physical handset")
            } catch (error: WeatherServiceException) {
                assertTrue(error.message.orEmpty().contains("adb reverse"))
            }
        } finally { repository.close() }
    }

    @Test(timeout = 2000) fun offlinePublicGatewayFailsBeforeDnsOrConnection() = runBlocking {
        val repository = WeatherRepository("https://weather.example.invalid", false, hasInternet = { false })
        try {
            try {
                repository.weather(DefaultCities.beijing, false)
                fail("A disconnected handset must fail immediately")
            } catch (error: WeatherServiceException) {
                assertEquals("当前未连接网络", error.message)
            }
            assertTrue(repository.weather(DefaultCities.beijing, true).isDemo)
        } finally { repository.close() }
    }

    @Test(timeout = 10000) fun usbLoopbackGatewayWorksWithoutHandsetInternet() = runBlocking {
        withGateway(hasInternet = { false }) { server, repository ->
            server.enqueue(MockResponse().setBody("""{"now":{"temp":"13","text":"晴"}}"""))
            assertEquals("13", repository.weather(DefaultCities.beijing, false).now.temp)
            assertEquals("/v1/weather?location=101010100", server.takeRequest().path)
        }
    }

    @Test(timeout = 2000) fun loopbackDevelopmentNetworkPermissionIsChecked() = runBlocking {
        val repository = WeatherRepository("http://127.0.0.1:8080", true) { false }
        try {
            try {
                repository.weather(DefaultCities.beijing, false)
                fail("Local development access must wait for permission")
            } catch (error: WeatherServiceException) {
                assertTrue(error.message.orEmpty().contains("附近设备权限"))
            }
        } finally { repository.close() }
    }

    @Test(timeout = 2000) fun deniedEmulatorNetworkPermissionFailsBeforeOpeningConnection() = runBlocking {
        var permissionChecked = false
        val repository = WeatherRepository("http://10.0.2.2:8080", true, isEmulator = { true }) {
            permissionChecked = true
            false
        }
        try {
            try {
                repository.weather(DefaultCities.beijing, false)
                fail("Local network access must wait for permission")
            } catch (error: WeatherServiceException) {
                assertTrue(permissionChecked)
                assertTrue(error.message.orEmpty().contains("附近设备权限"))
            }
        } finally { repository.close() }
    }

    @Test(timeout = 10000) fun liveRequestUsesGatewayContractAndPreservesPartialFailures() = runBlocking {
        withGateway { server, repository ->
            server.enqueue(MockResponse().setBody("""{"now":{"temp":"13","text":"晴"},"partialFailures":["air"],"fetchedAt":1793152800000}"""))
            val snapshot = repository.weather(DefaultCities.beijing, false)
            assertFalse(snapshot.isDemo)
            assertEquals("13", snapshot.now.temp)
            assertEquals(listOf("air"), snapshot.partialFailures)
            assertEquals(1793152800000L, snapshot.fetchedAt)
            assertEquals("/v1/weather?location=101010100", server.takeRequest().path)
        }
    }

    @Test(timeout = 10000) fun cityCardsUseTheCurrentOnlyEndpointWithoutForecastRequests() = runBlocking {
        withGateway { server, repository ->
            server.enqueue(MockResponse().setBody("""{"now":{"temp":"21","text":"阴","icon":"104"},"fetchedAt":"2026-10-29T02:00:00Z"}"""))
            val card = repository.currentSummary(DefaultCities.shanghai)
            assertEquals("21", card.temp)
            assertEquals(DefaultCities.shanghai.location, card.city.location)
            assertEquals("/v1/current?location=101020100", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        }
    }

    @Test(timeout = 10000) fun failedLiveServiceNeverFallsBackToDemoAndDoesNotExposeServerBody() = runBlocking {
        withGateway { server, repository ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("private-upstream-diagnostic"))
            try {
                repository.weather(DefaultCities.beijing, false)
                fail("Live failure must remain an error")
            } catch (error: WeatherServiceException) {
                assertFalse(error.message.orEmpty().contains("private-upstream-diagnostic"))
            }
        }
    }

    @Test(timeout = 10000) fun coroutineCancellationStopsAnInFlightRequest() = runBlocking {
        withGateway { server, repository ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val pending = async(Dispatchers.IO) { repository.weather(DefaultCities.beijing, false) }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
            server.enqueue(MockResponse().setBody("""{"now":{"temp":"21"}}"""))
            assertEquals("21", repository.weather(DefaultCities.shanghai, false).now.temp)
        }
    }

    @Test(timeout = 10000) fun cityQueryIsEncodedAndParsed() = runBlocking {
        withGateway { server, repository ->
            server.enqueue(MockResponse().setBody("""{"location":[{"id":"101210101","name":"杭州"}]}"""))
            assertEquals("杭州", repository.search("杭州", false).single().name)
            val request = server.takeRequest()
            assertEquals("杭州", request.requestUrl?.queryParameter("q"))
            assertEquals("/v1/cities", request.requestUrl?.encodedPath)
        }
    }

    private suspend fun withGateway(hasInternet: () -> Boolean = { true }, block: suspend (MockWebServer, WeatherRepository) -> Unit) {
        val server = MockWebServer()
        var repository: WeatherRepository? = null
        try {
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val baseUrl = server.url("/").newBuilder().host("127.0.0.1").build().toString()
            val activeRepository = WeatherRepository(baseUrl, true, hasInternet = hasInternet)
            repository = activeRepository
            block(server, activeRepository)
        } finally {
            try { repository?.close() } finally { server.shutdown() }
        }
    }
}
