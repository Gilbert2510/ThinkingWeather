package com.thinkingweather.gateway

import java.util.Collections
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class WeatherIndicesTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    private data class Request(val path: String, val query: Map<String, String>, val ttl: Long, val stale: Long)
    private class Source(private val indices: String = INDICES, private val failIndices: Boolean = false) : WeatherSource {
        val calls: MutableList<Request> = Collections.synchronizedList(mutableListOf())
        override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> {
            calls += Request(path, parameters, ttlMillis, staleMillis)
            val body = when {
                path.startsWith("/weather/v1/current/") -> """{"temperature":{"value":20,"unit":"°C"}}"""
                path.startsWith("/weather/v1/hourly/") -> """{"hours":[{"forecastTime":"2026-10-29T12:00+08:00","temperature":{"value":21,"unit":"°C"}}]}"""
                path.startsWith("/weather/v1/daily/") -> """{"days":[{"forecastStartTime":"2026-10-29T07:00+08:00","temperatureMax":{"value":23,"unit":"°C"},"temperatureMin":{"value":14,"unit":"°C"}}]}"""
                path == "/v7/indices/1d" -> {
                    if (failIndices) throw GatewayException(502, "upstream_credentials_or_plan")
                    indices
                }
                else -> throw AssertionError("Unexpected endpoint")
            }
            return Cached(Json.parseToJsonElement(body).jsonObject, false, 0)
        }
    }

    @Test fun `one cached optional call gets supported indices and preserves attribution`() {
        val source = Source()
        WeatherService(source, enableAir = false, enableWarnings = false).use { service ->
            val result = service.weather("116.405,39.917")
            val call = source.calls.single { it.path == "/v7/indices/1d" }
            assertEquals(mapOf("lang" to "zh", "location" to "116.41,39.92", "type" to "0"), call.query)
            assertEquals(3_600_000L, call.ttl)
            assertEquals(3_600_000L, call.stale)
            assertEquals(4, source.calls.size)
            assertEquals(2, result.arrayAt("indices").size)
            assertEquals("3", result.arrayAt("indices").first().jsonObject.text("type"))
            assertTrue(result.objectAt("refer").arrayAt("license").contains(JsonPrimitive("QWeather Developers License")))
            assertFalse(result.arrayAt("partialFailures").contains(JsonPrimitive("indices_unavailable")))
        }
    }

    @Test fun `indices permission failure never blocks current and forecast data`() {
        WeatherService(Source(failIndices = true), enableAir = false, enableWarnings = false).use { service ->
            val result = service.weather("116.41,39.92")
            assertEquals("20", result.objectAt("now").text("temp"))
            assertEquals(1, result.arrayAt("hourly").size)
            assertEquals(1, result.arrayAt("daily").size)
            assertTrue(result.arrayAt("indices").isEmpty())
            assertTrue(result.arrayAt("partialFailures").contains(JsonPrimitive("indices_unavailable")))
        }
    }

    @Test fun `zero coverage and malformed optional data are distinct without fake advice`() {
        listOf("""{"code":"204"}""" to false, "{}" to true).forEach { (response, unavailable) ->
            WeatherService(Source(response), enableAir = false, enableWarnings = false).use { service ->
                val result = service.weather("116.41,39.92")
                assertTrue(result.arrayAt("indices").isEmpty())
                assertEquals(unavailable, result.arrayAt("partialFailures").contains(JsonPrimitive("indices_unavailable")))
            }
        }
    }

    @Test fun `disabled indices produce no request and configuration is explicit`() {
        val source = Source()
        WeatherService(source, enableAir = false, enableWarnings = false, enableIndices = false).use { service ->
            val result = service.weather("116.41,39.92")
            assertTrue(source.calls.none { it.path.contains("indices") })
            assertTrue(result.arrayAt("indices").isEmpty())
            assertTrue(result.arrayAt("partialFailures").contains(JsonPrimitive("indices_disabled")))
        }
        assertTrue(Config.fromEnvironment(emptyMap()).enableIndices)
        assertFalse(Config.fromEnvironment(mapOf("QWEATHER_ENABLE_INDICES" to "false")).enableIndices)
        assertThrows(IllegalStateException::class.java) {
            Config.fromEnvironment(mapOf("QWEATHER_ENABLE_INDICES" to "sometimes"))
        }
    }

    @Test fun `old day or incomplete advice is omitted even if upstream cache is still reusable`() {
        assertEquals(2, WeatherMapping.indices(json(INDICES), "2026-10-29").size)
        assertEquals(1, WeatherMapping.indices(json(INDICES), "2026-10-28").size)
        assertTrue(WeatherMapping.indices(json(INDICES), null).isEmpty())
        assertTrue(WeatherMapping.indices(json(INDICES), "2026-10-30").isEmpty())
    }

    @Test fun `daily mapping preserves night wind and moon times with full day precipitation only when known`() {
        val day = """{"forecastStartTime":"2026-10-29T07:00+08:00","astro":{"moonrise":"2026-10-29T19:42+08:00","moonset":"2026-10-29T08:15+08:00","moonPhase":"waning-gibbous"},"daytime":{"precipitation":{"amount":{"value":1.2,"unit":"mm"}}},"nighttime":{"wind":{"direction":{"compass":"wnw"},"scale":2,"speed":{"value":3,"unit":"m/s"}},"precipitation":{"amount":{"value":0.3,"unit":"mm"}}}}"""
        val mapped = WeatherMapping.daily(json("""{"days":[$day]}""")).single().jsonObject
        assertEquals("10.8", mapped.text("windSpeedNight"))
        assertEquals("西北偏西风", mapped.text("windDirNight"))
        assertEquals("19:42", mapped.text("moonrise"))
        assertEquals("亏凸月", mapped.text("moonPhase"))
        assertEquals("1.5", mapped.text("precip"))
        val missingNight = WeatherMapping.daily(json("""{"days":[{"daytime":{"precipitation":{"amount":{"value":1.2,"unit":"mm"}}}}]}""")).single().jsonObject
        assertNull(missingNight.text("precip"))
    }

    companion object {
        private const val INDICES = """{"code":"200","daily":[{"date":"2026-10-29","type":"3","name":"穿衣指数","level":"4","category":"较舒适","text":"外出时适当增减衣物"},{"date":"2026-10-29","type":"5","name":"紫外线指数","level":"3","category":"中等","text":""},{"date":"2026-10-29","type":"3","name":"重复记录","category":"重复"},{"date":"2026-10-28","type":"3","name":"穿衣指数","category":"冷"},{"date":"2026-10-29","type":"99","name":"未知","category":"未知"},{"date":"2026-10-29","type":"6","name":"旅游指数"}],"refer":{"sources":["https://example.org/indices"],"license":["QWeather Developers License"]}}"""
    }
}
