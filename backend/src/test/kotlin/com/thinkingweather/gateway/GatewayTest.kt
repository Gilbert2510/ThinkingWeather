package com.thinkingweather.gateway

import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class GatewayTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    @Test fun `JWT has current required claims and verifiable Ed25519 signature`() {
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val auth = JwtAuth(key.private, "key123", "Qdev123456", "project123", Clock.fixed(Instant.ofEpochSecond(1_800_000_000), ZoneOffset.UTC))
        val (name, bearer) = auth.header()
        assertEquals("Authorization", name)
        val parts = bearer.removePrefix("Bearer ").split('.')
        assertEquals(3, parts.size)
        assertTrue(parts.none { '=' in it })
        val decoder = Base64.getUrlDecoder()
        val header = json(String(decoder.decode(parts[0])))
        val payload = json(String(decoder.decode(parts[1])))
        assertEquals("EdDSA", header.text("alg"))
        assertEquals("key123", header.text("kid"))
        assertEquals("Qdev123456", payload.text("iss"))
        assertEquals("project123", payload.text("sub"))
        assertEquals("1799999970", payload.text("iat"))
        assertEquals("1800000870", payload.text("exp"))
        assertTrue(Signature.getInstance("Ed25519").apply { initVerify(key.public); update("${parts[0]}.${parts[1]}".toByteArray()) }.verify(decoder.decode(parts[2])))
        assertEquals(bearer, auth.header().second)
    }

    @Test fun `host and location validation reject SSRF and out of range coordinates`() {
        assertEquals("abc.xy.qweatherapi.com", validateApiHost("ABC.xy.qweatherapi.com"))
        listOf("localhost", "api.qweather.com", "qweatherapi.com", "evilqweatherapi.com", "abc.qweatherapi.com.evil.org", "https://abc.qweatherapi.com", "abc.qweatherapi.com:443", "abc.qweatherapi.com/path", "user@abc.qweatherapi.com").forEach {
            assertThrows(IllegalArgumentException::class.java) { validateApiHost(it) }
        }
        assertEquals("116.41,39.92", Validation.location("116.405285,39.91755"))
        assertEquals("101010100", Validation.location("101010100"))
        listOf("181,20", "120,91", "NaN,20", "1e2,30", "0,0,0", "../../admin", "100?url=x").forEach {
            assertThrows(GatewayException::class.java) { Validation.location(it) }
        }
        assertEquals("北京", Validation.parameters("q=%E5%8C%97%E4%BA%AC", "q"))
        assertThrows(GatewayException::class.java) { Validation.parameters("q=a&q=b", "q") }
        assertThrows(GatewayException::class.java) { Validation.parameters("q=%zz", "q") }
        assertThrows(GatewayException::class.java) { Validation.query("a\nInjected") }
    }

    @Test fun `configuration can start unconfigured but rejects partial and mixed credentials`() {
        assertFalse(Config.fromEnvironment(emptyMap()).ready)
        assertThrows(IllegalArgumentException::class.java) { Config.fromEnvironment(mapOf("QWEATHER_API_HOST" to "abc.qweatherapi.com")) }
        assertThrows(IllegalArgumentException::class.java) { Config.fromEnvironment(mapOf("QWEATHER_KEY_ID" to "key123")) }
        assertThrows(IllegalArgumentException::class.java) { Config.fromEnvironment(mapOf("QWEATHER_KEY_ID" to "key123", "QWEATHER_API_KEY" to "abcdefgh")) }
        assertTrue(Config.fromEnvironment(mapOf("QWEATHER_API_HOST" to "abc.qweatherapi.com", "QWEATHER_API_KEY" to "abcdefgh")).ready)
    }

    @Test fun `cache coalesces simultaneous misses and stays bounded`() {
        val cache = BoundedCache<String>(maximumEntries = 2)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..4).map { pool.submit<Cached<String>> { cache.get("same", 100_000) { calls.incrementAndGet(); started.countDown(); release.await(3, TimeUnit.SECONDS); "one" } } }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(futures.all { it.get(3, TimeUnit.SECONDS).value == "one" })
            assertEquals(1, calls.get())
            cache.get("two", 100_000) { "two" }
            cache.get("three", 100_000) { "three" }
            assertEquals(2, cache.size())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun `cache serves bounded stale on errors and cools down failures`() {
        var time = 0L
        val cache = BoundedCache<String>(failureCooldownMillis = 10, timeMillis = { time })
        assertFalse(cache.get("x", 100, 200) { "real" }.stale)
        time = 101
        assertTrue(cache.get("x", 100, 200) { throw GatewayException(502, "upstream_unavailable") }.stale)
        assertTrue(cache.get("x", 100, 200) { error("must not retry in cooldown") }.stale)
        time = 301
        assertThrows(GatewayException::class.java) { cache.get("x", 100, 200) { throw GatewayException(502, "upstream_unavailable") } }
        assertThrows(GatewayException::class.java) { cache.get("x", 100, 200) { "must not retry" } }
        time = 312
        assertEquals("recovered", cache.get("x", 100, 200) { "recovered" }.value)
    }

    @Test fun `rate limits cannot be bypassed by churning a full peer store`() {
        var time = 0L
        val limiter = RateLimiter(perPeerLimit = 1, globalLimit = 10, maximumPeers = 2, windowMillis = 100, timeMillis = { time })
        assertTrue(limiter.allow("a"))
        assertFalse(limiter.allow("a"))
        assertTrue(limiter.allow("b"))
        assertFalse(limiter.allow("c"))
        assertEquals(2, limiter.size())
        time = 101
        assertTrue(limiter.allow("c"))
        assertTrue(limiter.allow("d"))
        assertFalse(limiter.allow("e"))
        assertTrue(limiter.size() <= 2)
        val global = RateLimiter(perPeerLimit = 10, globalLimit = 2, maximumPeers = 20, timeMillis = { time })
        assertTrue(global.allow("a"))
        assertTrue(global.allow("b"))
        assertFalse(global.allow("c"))
    }

    @Test fun `v1 mapping preserves units missing values and actual forecast dates`() {
        val current = WeatherMapping.current(json(CURRENT))
        assertEquals("25.3", current.text("temp"))
        assertEquals("54", current.text("humidity"))
        assertEquals("18", current.text("windSpeed"))
        assertEquals("8.5", current.text("vis"))
        assertEquals("西南风", current.text("windDir"))
        assertNull(current["obsTime"])
        assertNull(WeatherMapping.current(json("{}"))["temp"])
        assertNull(WeatherMapping.current(json("""{"temperature":{"value":90,"unit":"°F"}}"""))["temp"])
        val daily = WeatherMapping.daily(json(DAILY))[0].jsonObject
        assertEquals("2026-10-28", daily.text("fxDate"))
        assertEquals("06:22", daily.text("sunrise"))
        assertEquals("80", daily.text("pop"))
    }

    @Test fun `optional provider failures leave real weather available and explicit`() {
        WeatherService(fakeSource(optionalFailure = true)).use { service ->
            val result = service.weather("101010100")
            assertEquals("25.3", result.objectAt("now").text("temp"))
            assertTrue(result.arrayAt("partialFailures").contains(JsonPrimitive("air_unavailable")))
            assertTrue(result.arrayAt("partialFailures").contains(JsonPrimitive("warnings_unavailable")))
            assertTrue(result.arrayAt("warnings").isEmpty())
            assertEquals("https://example.org/attribution", (result.objectAt("refer").arrayAt("sources")[0] as JsonPrimitive).content)
        }
        val broken = object : WeatherSource {
            override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> = throw GatewayException(502, "upstream_credentials_or_plan")
        }
        WeatherService(broken).use { service -> assertThrows(GatewayException::class.java) { service.weather("116.41,39.92") } }
    }

    @Test fun `cancelled alerts are not presented as active alerts and attribution is retained`() {
        val warnings = WeatherMapping.warnings(json("""{"alerts":[{"id":"old","headline":"Old wind","messageType":{"code":"alert"}},{"headline":"Cancelled","messageType":{"code":"cancel","supersedes":["old"]}},{"id":"active","headline":"Strong wind","description":"Official description","instruction":"Secure loose objects","messageType":{"code":"alert"},"color":{"code":"orange"},"effectiveTime":"2026-10-28T03:00Z","expireTime":"2026-10-28T09:00Z","senderName":"Official agency"}]}"""))
        assertEquals(1, warnings.size)
        assertEquals("Strong wind", warnings[0].jsonObject.text("title"))
        assertEquals("Official description\n\nSecure loose objects", warnings[0].jsonObject.text("text"))
        assertEquals("orange", warnings[0].jsonObject.text("severityColor"))
        assertEquals("2026-10-28T03:00Z", warnings[0].jsonObject.text("startTime"))
        assertEquals("2026-10-28T09:00Z", warnings[0].jsonObject.text("endTime"))
        assertEquals("Official agency", warnings[0].jsonObject.text("sender"))
    }

    @Test fun `GeoAPI no match becomes empty city search and unknown ID returns404`() {
        val missing = object : WeatherSource {
            override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> = throw GatewayException(502, "upstream_not_found")
        }
        WeatherService(missing).use { service ->
            assertTrue(service.cities("Unknown city").arrayAt("location").isEmpty())
            val error = assertThrows(GatewayException::class.java) { service.weather("101010100") }
            assertEquals(404, error.status)
            assertEquals("location_not_found", error.code)
        }
    }

    @Test fun `HTTP gateway supports real routes rejects methods and ignores forwarded peer spoofing`() {
        GatewayServer(Config(), RateLimiter(perPeerLimit = 1), fakeSource(), InetSocketAddress("127.0.0.1", 0)).use { server ->
            server.start()
            val client = HttpClient.newHttpClient()
            fun get(path: String, spoof: String = "a") = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path")).header("X-Forwarded-For", spoof).GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, get("/health").statusCode())
            assertEquals(404, get("/health/extra").statusCode())
            val city = get("/v1/cities?q=116.41%2C39.92")
            assertEquals(200, city.statusCode())
            assertEquals("101010100", json(city.body()).arrayAt("location")[0].jsonObject.text("id"))
            assertEquals("no-store", city.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(429, get("/v1/cities?q=Beijing", "b").statusCode())
            val post = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/weather")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(405, post.statusCode())
        }
    }

    @Test fun `unconfigured HTTP service never pretends to provide weather`() {
        GatewayServer(Config(), addressOverride = InetSocketAddress("127.0.0.1", 0)).use { server ->
            server.start()
            val client = HttpClient.newHttpClient()
            val health = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/health")).GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(false, (json(health.body())["configured"] as JsonPrimitive).booleanOrNull)
            val weather = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/weather?location=101010100")).GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(503, weather.statusCode())
            assertEquals("provider_not_configured", json(weather.body()).text("error"))
        }
    }

    @Test fun `body subscriber cancels when compressed limit is exceeded`() {
        var cancelled = false
        val subscriber = BoundedBodySubscriber(4)
        subscriber.onSubscribe(object : Flow.Subscription {
            override fun request(n: Long) {}
            override fun cancel() { cancelled = true }
        })
        subscriber.onNext(mutableListOf(ByteBuffer.wrap(ByteArray(5))))
        assertTrue(cancelled)
        assertTrue(subscriber.body.toCompletableFuture().isCompletedExceptionally)
    }

    private fun fakeSource(optionalFailure: Boolean = false) = object : WeatherSource {
        override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> {
            val body = when {
                path.startsWith("/geo/") -> """{"code":"200","location":[{"id":"101010100","name":"北京","lon":"116.41","lat":"39.92"}]}"""
                path.startsWith("/weather/v1/current/") -> CURRENT
                path.startsWith("/weather/v1/hourly/") -> """{"hours":[{"forecastTime":"2026-10-28T12:00+08:00","temperature":{"value":26,"unit":"°C"},"condition":{"code":"100","text":"晴"}}]}"""
                path.startsWith("/weather/v1/daily/") -> DAILY
                path.startsWith("/weatheralert/") -> if (optionalFailure) throw GatewayException(502, "upstream_unavailable") else """{"metadata":{"zeroResult":true},"alerts":[]}"""
                path.startsWith("/airquality/") -> if (optionalFailure) throw GatewayException(502, "upstream_unavailable") else """{"indexes":[{"code":"cn-mee","aqi":42,"category":"优"}]}"""
                path == "/v7/indices/1d" -> if (optionalFailure) throw GatewayException(502, "upstream_unavailable") else """{"code":"204","daily":[]}"""
                else -> error("Unexpected path")
            }
            return Cached(json(body), false, 0)
        }
    }

    companion object {
        private const val CURRENT = """{"metadata":{"attributions":["https://example.org/attribution"]},"temperature":{"value":25.25,"unit":"°C"},"feelsLike":{"value":24,"unit":"°C"},"humidity":0.54,"condition":{"code":"100","text":"晴"},"wind":{"direction":{"degree":225,"compass":"sw"},"speed":{"value":5,"unit":"m/s"},"scale":3},"visibility":{"value":8500,"unit":"m"}}"""
        private const val DAILY = """{"days":[{"forecastStartTime":"2026-10-28T06:00+08:00","temperatureMax":{"value":27,"unit":"°C"},"temperatureMin":{"value":17,"unit":"°C"},"astro":{"sunrise":"2026-10-28T06:22+08:00","sunset":"2026-10-28T17:20+08:00"},"daytime":{"condition":{"code":"100","text":"晴"},"precipitation":{"probability":0.8}}}]}"""
    }
}
