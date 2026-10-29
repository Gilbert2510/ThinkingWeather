package com.thinkingweather.gateway

import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class CurrentWeatherTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    private val validCurrent = """{"temperature":{"value":20,"unit":"°C"},"condition":{"code":"101","text":"多云"},"metadata":{"attributions":["https://example.org/source"]}}"""

    @Test fun `all sixteen compass descriptions and calm variable directions are readable`() {
        val expected = linkedMapOf(
            "n" to "北风", "nne" to "东北偏北风", "ne" to "东北风", "ene" to "东北偏东风",
            "e" to "东风", "ese" to "东南偏东风", "se" to "东南风", "sse" to "东南偏南风",
            "s" to "南风", "ssw" to "西南偏南风", "sw" to "西南风", "wsw" to "西南偏西风",
            "w" to "西风", "wnw" to "西北偏西风", "nw" to "西北风", "nnw" to "西北偏北风",
            "none" to "静风", "vrb" to "风向不定", "WSW" to "西南偏西风",
        )
        expected.forEach { (code, description) ->
            assertEquals(description, WeatherMapping.current(json("""{"wind":{"direction":{"compass":"$code"}}}""")).text("windDir"))
        }
    }

    @Test fun `current cards request only the cached current feed and preserve freshness and attribution`() {
        val calls = mutableListOf<String>()
        val source = object : WeatherSource {
            override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> {
                calls += path
                assertEquals(mapOf("lang" to "zh", "localTime" to "true"), parameters)
                assertEquals(120_000L, ttlMillis)
                assertEquals(900_000L, staleMillis)
                return Cached(json(validCurrent), true, 90)
            }
        }
        val clock = Clock.fixed(Instant.parse("2026-10-29T04:00:00Z"), ZoneOffset.UTC)
        WeatherService(source, clock = clock).use { service ->
            val result = service.current("116.405,39.917")
            assertEquals(listOf("/weather/v1/current/39.92/116.41"), calls)
            assertEquals("20", result.objectAt("now").text("temp"))
            assertEquals(true, result["stale"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).booleanOrNull })
            assertEquals("90", result.text("cacheAgeSeconds"))
            assertEquals("2026-10-29T03:58:30Z", result.text("fetchedAt"))
            assertEquals(1, result.objectAt("refer").arrayAt("sources").size)
            assertNull(result["hourly"])
            assertNull(result["daily"])
        }
    }

    @Test fun `v7 current card does not query GeoAPI or optional weather feeds`() {
        val calls = mutableListOf<String>()
        val source = object : WeatherSource {
            override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> {
                calls += path
                assertEquals("101010100", parameters["location"])
                return Cached(json("""{"now":{"temp":"17","text":"阴","icon":"104"}}"""), false, 0)
            }
        }
        WeatherService(source, weatherApi = "v7").use { service ->
            assertEquals("17", service.current("101010100").objectAt("now").text("temp"))
            assertEquals(listOf("/v7/weather/now"), calls)
        }
    }

    @Test fun `unusable current response fails instead of manufacturing a temperature`() {
        val source = object : WeatherSource {
            override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long) = Cached(json("{}"), false, 0)
        }
        WeatherService(source).use { service ->
            val error = assertThrows(GatewayException::class.java) { service.current("116.41,39.92") }
            assertEquals(502, error.status)
            assertEquals("invalid_upstream_response", error.code)
        }
    }

    @Test fun `current HTTP route enforces normal query validation and shared limits`() {
        val source = object : WeatherSource {
            override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long) = Cached(json(validCurrent), false, 0)
        }
        GatewayServer(Config(), RateLimiter(perPeerLimit = 2), source, InetSocketAddress("127.0.0.1", 0)).use { server ->
            server.start()
            val client = HttpClient.newHttpClient()
            fun get(query: String) = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/current?$query")).GET().build(), HttpResponse.BodyHandlers.ofString())
            val response = get("location=116.41%2C39.92")
            assertEquals(200, response.statusCode())
            assertEquals("20", json(response.body()).objectAt("now").text("temp"))
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(400, get("location=116.41%2C39.92&location=0%2C0").statusCode())
            assertEquals(429, get("location=116.41%2C39.92").statusCode())
        }
        GatewayServer(Config(), addressOverride = InetSocketAddress("127.0.0.1", 0)).use { server ->
            server.start()
            val result = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/current?location=116.41%2C39.92")).GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(503, result.statusCode())
            assertEquals("provider_not_configured", json(result.body()).text("error"))
        }
    }
}
