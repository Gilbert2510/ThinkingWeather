package com.thinkingweather.gateway

import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WeatherService(
    private val source: WeatherSource,
    private val weatherApi: String = "v1",
    private val enableAir: Boolean = true,
    private val enableWarnings: Boolean = true,
    private val clock: Clock = Clock.systemUTC(),
    private val enableIndices: Boolean = true,
) : AutoCloseable {
    private val workers = ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64), ThreadPoolExecutor.AbortPolicy())
    private val chinese = mapOf("lang" to "zh")

    fun cities(rawQuery: String): JsonObject {
        val query = Validation.query(rawQuery).let { if (',' in it) Validation.coordinates(it).location else it }
        val response = lookup(query)
        if (response.value["location"] !is JsonArray && response.value.text("code") != "204") throw GatewayException(502, "invalid_upstream_response")
        return buildJsonObject {
            put("location", JsonArray(response.value.arrayAt("location").map { element ->
                if (element is JsonObject) AdministrativeNames.enrich(element) else element
            }))
            put("refer", WeatherMapping.attribution(listOf(response.value)))
            put("stale", response.stale)
        }
    }

    private fun lookup(query: String): Cached<JsonObject> = try {
        source.get("/geo/v2/city/lookup", chinese + mapOf("location" to query, "number" to "10"), 86_400_000, 604_800_000)
    } catch (error: GatewayException) {
        // GeoAPI uses 404 for an ordinary search with no matching location.
        if (error.code != "upstream_not_found") throw error
        Cached(buildJsonObject { put("code", "204"); put("location", JsonArray(emptyList())) }, false, 0)
    }

    private fun resolveCoordinates(location: String): Coordinates =
        if (',' in location) Validation.coordinates(location) else {
            val city = lookup(location).value.arrayAt("location").firstOrNull() as? JsonObject
                ?: throw GatewayException(404, "location_not_found")
            try { Validation.coordinates("${city.text("lon")},${city.text("lat")}") }
            catch (_: GatewayException) { throw GatewayException(502, "invalid_upstream_response") }
        }

    /** City cards need current conditions only; do not multiply full forecast requests per saved city. */
    fun current(rawLocation: String): JsonObject {
        val location = Validation.location(rawLocation)
        val v1 = weatherApi == "v1"
        val response = if (v1) {
            source.get("/weather/v1/current/${resolveCoordinates(location).path}", chinese + mapOf("localTime" to "true"), 120_000, 900_000)
        } else {
            source.get("/v7/weather/now", chinese + ("location" to location), 120_000, 900_000)
        }
        val now = if (v1) WeatherMapping.current(response.value) else response.value.objectAt("now")
        if (now.text("temp")?.toDoubleOrNull()?.isFinite() != true) throw GatewayException(502, "invalid_upstream_response")
        return buildJsonObject {
            put("now", now)
            put("refer", WeatherMapping.attribution(listOf(response.value)))
            put("stale", response.stale)
            put("cacheAgeSeconds", response.ageSeconds)
            put("fetchedAt", clock.instant().minusSeconds(response.ageSeconds).toString())
        }
    }

    fun weather(rawLocation: String): JsonObject {
        val location = Validation.location(rawLocation)
        val coordinates = resolveCoordinates(location)
        val parameters = chinese + mapOf("localTime" to "true")
        val requests = linkedMapOf<String, CompletableFuture<Cached<JsonObject>>>()
        fun submit(name: String, path: String, query: Map<String, String>, ttl: Long, stale: Long) {
            try { requests[name] = CompletableFuture.supplyAsync({ source.get(path, query, ttl, stale) }, workers) }
            catch (_: RejectedExecutionException) { throw GatewayException(503, "gateway_busy") }
        }
        val v1 = weatherApi == "v1"
        if (v1) {
            submit("now", "/weather/v1/current/${coordinates.path}", parameters, 120_000, 900_000)
            submit("hourly", "/weather/v1/hourly/${coordinates.path}", parameters + ("hours" to "24"), 600_000, 1_800_000)
            submit("daily", "/weather/v1/daily/${coordinates.path}", parameters + ("days" to "7"), 1_800_000, 3_600_000)
        } else {
            val legacy = chinese + ("location" to location)
            submit("now", "/v7/weather/now", legacy, 120_000, 900_000)
            submit("hourly", "/v7/weather/24h", legacy, 600_000, 1_800_000)
            submit("daily", "/v7/weather/7d", legacy, 1_800_000, 3_600_000)
        }
        // Alerts intentionally never use stale-on-error: stale safety messages can be misleading.
        if (enableWarnings) submit("warnings", "/weatheralert/v1/current/${coordinates.path}", parameters, 60_000, 0)
        if (enableAir) submit("air", "/airquality/v1/current/${coordinates.path}", chinese, 600_000, 900_000)
        // One optional call obtains every supported type, including the subset available overseas.
        // Hourly caching avoids multiplying billable requests when users open detail cards.
        if (enableIndices) submit("indices", "/v7/indices/1d", chinese + mapOf("location" to location, "type" to "0"), 3_600_000, 3_600_000)
        val parts = linkedMapOf<String, Cached<JsonObject>>()
        val failures = mutableListOf<String>()
        requests.forEach { (name, future) ->
            try { parts[name] = future.join() }
            catch (exception: CompletionException) {
                if (name in setOf("now", "hourly", "daily")) throw (exception.cause as? GatewayException ?: GatewayException(502, "upstream_unavailable"))
                failures += "${name}_unavailable"
            }
        }
        if (!enableWarnings) failures += "warnings_disabled"
        if (!enableAir) failures += "air_disabled"
        if (!enableIndices) failures += "indices_disabled"
        val nowData = parts.getValue("now").value
        val hourlyData = parts.getValue("hourly").value
        val dailyData = parts.getValue("daily").value
        val now = if (v1) WeatherMapping.current(nowData) else nowData.objectAt("now")
        val hourly = if (v1) WeatherMapping.hourly(hourlyData) else hourlyData.arrayAt("hourly")
        val daily = if (v1) WeatherMapping.daily(dailyData) else dailyData.arrayAt("daily")
        if (now.text("temp")?.toDoubleOrNull() == null || hourly.isEmpty() || daily.isEmpty()) throw GatewayException(502, "invalid_upstream_response")
        if (hourly.any { it !is JsonObject || it.text("fxTime") == null || it.text("temp")?.toDoubleOrNull() == null } ||
            daily.any { it !is JsonObject || it.text("fxDate") == null || it.text("tempMin")?.toDoubleOrNull() == null || it.text("tempMax")?.toDoubleOrNull() == null }) {
            throw GatewayException(502, "invalid_upstream_response")
        }
        val warnings = parts["warnings"]?.value?.let { data ->
            if (data["alerts"] !is JsonArray && (data.objectAt("metadata")["zeroResult"] as? JsonPrimitive)?.booleanOrNull != true) {
                failures += "warnings_unavailable"
                JsonArray(emptyList())
            } else WeatherMapping.warnings(data)
        } ?: JsonArray(emptyList())
        val air = parts["air"]?.value?.takeIf { it["indexes"] is JsonArray && it.arrayAt("indexes").isNotEmpty() }
        if (enableAir && parts.containsKey("air") && air == null) failures += "air_unavailable"
        val indices = parts["indices"]?.value?.let { data ->
            if (data["daily"] !is JsonArray && data.text("code") != "204") {
                failures += "indices_unavailable"
                JsonArray(emptyList())
            } else WeatherMapping.indices(data, (daily.firstOrNull() as? JsonObject)?.text("fxDate"))
        } ?: JsonArray(emptyList())
        parts.filterValues { it.stale }.keys.forEach { failures += "${it}_stale" }
        val age = parts.values.maxOfOrNull { it.ageSeconds } ?: 0
        return buildJsonObject {
            put("now", now)
            put("hourly", hourly)
            put("daily", daily)
            put("warnings", warnings)
            put("air", air ?: JsonNull)
            put("indices", indices)
            put("refer", WeatherMapping.attribution(parts.values.map { it.value }))
            put("partialFailures", JsonArray(failures.distinct().map(::JsonPrimitive)))
            put("stale", parts.values.any { it.stale })
            put("cacheAgeSeconds", age)
            put("fetchedAt", clock.instant().minusSeconds(age).toString())
        }
    }

    override fun close() { workers.shutdownNow() }
}
