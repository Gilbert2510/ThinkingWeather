package com.thinkingweather.android.data

import com.thinkingweather.android.model.City
import com.thinkingweather.android.model.WeatherSnapshot
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal open class WeatherServiceException(message: String, cause: Throwable? = null) : IOException(message, cause)
internal class WeatherOfflineException : WeatherServiceException("当前未连接网络")
internal class WeatherHttpException(val status: Int) : WeatherServiceException(when (status) {
    429 -> "天气服务繁忙，已暂停请求，请稍后重试。"
    401, 403 -> "天气服务认证或权限异常，请联系服务管理员。"
    404 -> "天气服务暂未提供此地区的数据。"
    in 500..599 -> "天气服务暂时不可用，请稍后重试。"
    else -> "天气查询失败（$status）。"
})

/** Production data boundary; deterministic substitutes live exclusively in test source sets. */
internal interface WeatherDataSource : AutoCloseable {
    val networkUnavailable: Boolean
    suspend fun search(query: String): List<City>
    suspend fun weather(city: City): WeatherSnapshot
    suspend fun currentSummary(city: City): CityWeatherSummary
    override fun close()
}

/** Calls the public FC relay using provider routes. No provider credentials or localhost exceptions. */
internal class WeatherRepository(
    baseUrl: String,
    private val hasInternet: () -> Boolean = { true },
    callFactory: Call.Factory? = null,
    private val timeMillis: () -> Long = System::currentTimeMillis,
) : WeatherDataSource {
    private val base = validateBaseUrl(baseUrl)
    private val ownedClient = if (callFactory == null) OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build() else null
    private val calls: Call.Factory = callFactory ?: ownedClient!!
    private val permits = Semaphore(2)
    private val guard = Any()
    private data class Record(val body: String, val savedAt: Long)
    private data class Flight(val mutex: Mutex = Mutex(), var users: Int = 0)
    private data class Failure(val until: Long, val error: IOException)
    private val records = LinkedHashMap<String, Record>(16, .75f, true)
    private val flights = HashMap<String, Flight>()
    private val failures = LinkedHashMap<String, Failure>()
    private var cachedBytes = 0L
    private var retryAfter = 0L
    @Volatile private var legacyWeather = false
    override val networkUnavailable get() = !hasInternet()

    override suspend fun search(query: String): List<City> {
        val text = query.trim()
        require(text.length in 1..80 && text.none(Char::isISOControl)) { "城市查询内容无效。" }
        val result = resource("geo/v2/city/lookup", mapOf("location" to text, "number" to "10"), 86_400_000, 604_800_000, emptyGeo = true)
        val json = JSONObject(result.body)
        val entries = json.optJSONArray("location") ?: if (json.optString("code") == "204") JSONArray() else throw JSONException("缺少城市列表")
        json.put("location", JSONArray().apply {
            for (index in 0 until entries.length()) entries.optJSONObject(index)?.let { put(AdministrativeNames.enrich(it)) }
        })
        return WeatherJson.cities(json.toString())
    }

    override suspend fun currentSummary(city: City): CityWeatherSummary {
        val response = current(city, coordinates(city))
        val normalized = JSONObject().put("now", currentData(response)).put("refer", QWeatherMapping.attribution(listOf(JSONObject(response.reply.body))))
            .put("fetchedAt", response.reply.fetchedAt).put("stale", response.reply.stale)
        return WeatherJson.currentSummary(normalized.toString(), city, timeMillis())
    }

    override suspend fun weather(city: City): WeatherSnapshot = coroutineScope {
        val started = System.nanoTime()
        val coordinates = coordinates(city)
        // An unavailable/rate-limited current feed terminates before other billable requests begin.
        val current = current(city, coordinates)
        val now = currentData(current)
        if (now.optString("temp").toDoubleOrNull()?.isFinite() != true) throw JSONException("实时温度无效")
        val parts = linkedMapOf("now" to current.reply)
        val unavailable = mutableListOf<String>()
        suspend fun phase(specs: List<Feed>) = coroutineScope {
            val work = specs.map { spec -> async {
                val result = try { Result.success(resource(spec.path, spec.query, spec.ttl, spec.stale)) }
                    catch (error: CancellationException) { throw error }
                    catch (error: IOException) { Result.failure(error) }
                    catch (error: JSONException) { Result.failure(error) }
                spec.name to result
            } }
            work.forEach { deferred ->
                val (name, result) = deferred.await()
                result.onSuccess { parts[name] = it }.onFailure { unavailable += "${name}_unavailable" }
            }
        }
        val legacy = current.legacy
        val enrichmentBudget = (30_000 - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
        withTimeoutOrNull(enrichmentBudget) {
          phase(listOf(
            Feed("hourly", if (legacy) "v7/weather/24h" else "weather/v1/hourly/${coordinates.path}", if (legacy) mapOf("location" to city.location) else mapOf("localTime" to "true", "hours" to "24"), 600_000, 1_800_000),
            Feed("daily", if (legacy) "v7/weather/7d" else "weather/v1/daily/${coordinates.path}", if (legacy) mapOf("location" to city.location) else mapOf("localTime" to "true", "days" to "7"), 1_800_000, 3_600_000),
        ))
        phase(listOf(
            Feed("air", "airquality/v1/current/${coordinates.path}", emptyMap(), 600_000, 900_000),
            Feed("warnings", "weatheralert/v1/current/${coordinates.path}", mapOf("localTime" to "true"), 60_000, 0),
            Feed("indices", "v7/indices/1d", mapOf("location" to city.location, "type" to "0"), 3_600_000, 3_600_000),
        ))
        } ?: listOf("hourly", "daily", "air", "warnings", "indices").filterNot(parts::containsKey).forEach { unavailable += "${it}_unavailable" }
        val jsonParts = parts.mapValues { JSONObject(it.value.body) }
        fun mapped(name: String, convert: (JSONObject) -> JSONArray): JSONArray {
            val value = jsonParts[name] ?: return JSONArray()
            return convert(value).also { if (it.length() == 0 && value.optString("code") != "204") unavailable += "${name}_unavailable" }
        }
        val daily = mapped("daily") { if (legacy) it.optJSONArray("daily") ?: JSONArray() else QWeatherMapping.daily(it) }
        val hourly = mapped("hourly") { if (legacy) it.optJSONArray("hourly") ?: JSONArray() else QWeatherMapping.hourly(it) }
        val air = jsonParts["air"]?.takeIf { it.optJSONArray("indexes")?.length()?.let { size -> size > 0 } == true }
        if (jsonParts.containsKey("air") && air == null) unavailable += "air_unavailable"
        val warnings = jsonParts["warnings"]?.let { value ->
            if (value.optJSONArray("alerts") == null && value.optJSONObject("metadata")?.optBoolean("zeroResult") != true) unavailable += "warnings_unavailable"
            QWeatherMapping.warnings(value)
        } ?: JSONArray()
        val forecastDate = runCatching {
            java.time.Instant.ofEpochMilli(timeMillis()).atZone(java.time.ZoneId.of(city.tz)).toLocalDate().toString()
        }.getOrNull()
        val indices = jsonParts["indices"]?.let { QWeatherMapping.indices(it, forecastDate) } ?: JSONArray()
        parts.filterValues { it.stale }.keys.forEach { unavailable += "${it}_stale" }
        val oldest = parts.values.minOf { it.fetchedAt }
        val normalized = JSONObject().put("now", now).put("hourly", hourly).put("daily", daily)
            .put("air", air ?: JSONObject.NULL).put("warnings", warnings).put("indices", indices)
            .put("refer", QWeatherMapping.attribution(jsonParts.values)).put("partialFailures", JSONArray(unavailable.distinct()))
            .put("stale", parts.values.any { it.stale }).put("cacheAgeSeconds", ((timeMillis() - oldest).coerceAtLeast(0) / 1000))
            .put("fetchedAt", oldest).put("fxLink", jsonParts["now"]?.optString("fxLink")?.takeIf(String::isNotBlank) ?: "https://www.qweather.com/")
        WeatherJson.snapshot(normalized.toString(), city, timeMillis())
    }

    private data class Feed(val name: String, val path: String, val query: Map<String, String>, val ttl: Long, val stale: Long)
    private data class CurrentReply(val reply: Reply, val legacy: Boolean)
    private suspend fun current(city: City, coordinates: Coordinates): CurrentReply {
        if (!legacyWeather) try {
            return CurrentReply(resource("weather/v1/current/${coordinates.path}", mapOf("localTime" to "true"), 120_000, 900_000), false)
        } catch (error: WeatherHttpException) {
            // Plan/retirement compatibility only; never retry 429, invalid credentials or transport failures.
            if (error.status !in setOf(403, 404, 410, 501)) throw error
        }
        val response = resource("v7/weather/now", mapOf("location" to city.location), 120_000, 900_000)
        if (JSONObject(response.body).optJSONObject("now")?.optString("temp")?.toDoubleOrNull()?.isFinite() != true) throw JSONException("实时温度无效")
        legacyWeather = true
        return CurrentReply(response, true)
    }
    private fun currentData(response: CurrentReply): JSONObject = JSONObject(response.reply.body).let {
        if (response.legacy) it.optJSONObject("now") ?: throw JSONException("缺少实时天气") else QWeatherMapping.current(it)
    }

    private data class Coordinates(val latitude: String, val longitude: String) { val path get() = "$latitude/$longitude" }
    private suspend fun coordinates(city: City): Coordinates {
        fun parse(lon: String, lat: String): Coordinates? {
            val longitude = lon.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
            val latitude = lat.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
            fun rounded(number: Double) = BigDecimal.valueOf(number).setScale(2, RoundingMode.HALF_UP).toPlainString()
            return Coordinates(rounded(latitude), rounded(longitude))
        }
        parse(city.lon, city.lat)?.let { return it }
        val split = city.location.split(',')
        if (split.size == 2) parse(split[0], split[1])?.let { return it }
        return search(city.location).firstOrNull()?.let { parse(it.lon, it.lat) }
            ?: throw WeatherServiceException("无法识别城市坐标，请重新搜索添加此城市。")
    }

    private data class Reply(val body: String, val fetchedAt: Long, val stale: Boolean = false)
    private suspend fun resource(path: String, query: Map<String, String>, ttl: Long, stale: Long, emptyGeo: Boolean = false): Reply {
        val url = base.newBuilder().addPathSegments(path).apply {
            (query + ("lang" to "zh")).toSortedMap().forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val key = url.toString()
        fun fresh(): Reply? = synchronized(guard) { records[key]?.takeIf { timeMillis() - it.savedAt in 0 until ttl }?.let { Reply(it.body, it.savedAt) } }
        fresh()?.let { return it }
        val flight = synchronized(guard) {
            if (flights.size >= 128 && key !in flights) throw WeatherServiceException("天气查询繁忙，请稍后重试。")
            flights.getOrPut(key) { Flight() }.also { it.users++ }
        }
        try { return flight.mutex.withLock {
            fresh()?.let { return@withLock it }
            try {
                val payload = permits.withPermit {
                    val blocked = synchronized(guard) {
                        if (timeMillis() < retryAfter) WeatherHttpException(429)
                        else failures[key]?.takeIf { timeMillis() < it.until }?.error
                    }
                    blocked?.let { throw it }
                    if (networkUnavailable) throw WeatherOfflineException()
                    fetch(url, emptyGeo)
                }
                val savedAt = payload.fetchedAt
                val body = payload.body
                synchronized(guard) {
                    failures.remove(key)
                    cachedBytes -= (records.remove(key)?.body?.length ?: 0) * 2L
                    records[key] = Record(body, savedAt)
                    cachedBytes += body.length * 2L
                    while (records.size > 96 || cachedBytes > 4 * 1024 * 1024L) {
                        val iterator = records.entries.iterator()
                        cachedBytes -= iterator.next().value.body.length * 2L
                        iterator.remove()
                    }
                }
                Reply(body, savedAt)
            } catch (error: CancellationException) { throw error }
            catch (error: IOException) {
                synchronized(guard) {
                    if (error !is WeatherOfflineException) failures[key] = Failure(timeMillis() + 15_000, error)
                    while (failures.size > 96) failures.remove(failures.keys.first())
                    records[key]?.takeIf { stale > 0 && timeMillis() - it.savedAt in 0 until ttl + stale }
                        ?.let { return@withLock Reply(it.body, it.savedAt, true) }
                }
                throw error
            }
        } } finally {
            synchronized(guard) { if (--flight.users == 0) flights.remove(key) }
        }
    }

    private suspend fun fetch(url: HttpUrl, emptyGeo: Boolean): Reply = suspendCancellableCoroutine { continuation ->
        val call = calls.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (!it.isSuccessful && !(emptyGeo && it.code == 404)) {
                        if (it.code == 429) coolDown(it.header("Retry-After"))
                        throw WeatherHttpException(it.code)
                    }
                    if (it.code == 204) return@use Reply(JSONObject().put("code", "204").put("location", JSONArray()).toString(), timeMillis())
                    val body = it.body ?: throw WeatherServiceException("天气服务未返回内容。")
                    val source = body.source()
                    source.request(MAX_RESPONSE_BYTES + 1)
                    if (source.buffer.size > MAX_RESPONSE_BYTES) throw WeatherServiceException("天气服务响应过大。")
                    val text = source.readUtf8()
                    if (text.isBlank()) throw WeatherServiceException("天气服务返回了空内容。")
                    val json = try { JSONObject(text) } catch (_: JSONException) { throw WeatherServiceException("天气服务数据格式有误，请稍后重试。") }
                    // Older FC relays return HTTP 200 even when the provider reports an error.
                    val rawCode = json.optString("code").takeIf(String::isNotBlank)
                    val code = rawCode?.toIntOrNull()
                    if (rawCode != null && code == null) throw WeatherHttpException(502)
                    val problem = json.optInt("status").takeIf { value -> value in 400..599 }
                        ?: json.optJSONObject("error")?.optInt("status")?.takeIf { value -> value in 400..599 }
                    val failure = code?.takeIf { value -> value != 200 && value != 204 } ?: problem
                    if (emptyGeo && failure == 404) return@use Reply(JSONObject().put("code", "204").put("location", JSONArray()).toString(), timeMillis())
                    if (!it.isSuccessful) throw WeatherHttpException(it.code)
                    if (failure != null || json.has("error")) {
                        val status = failure ?: 502
                        if (status == 429) coolDown(it.header("Retry-After"))
                        throw WeatherHttpException(status)
                    }
                    validatePayload(url.encodedPath, json)
                    val ageMillis = it.header("Age")?.toLongOrNull()?.coerceIn(0, 604_800)?.times(1000) ?: 0L
                    Reply(text, timeMillis() - ageMillis)
                } }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }

    private fun validatePayload(path: String, value: JSONObject) {
        val valid = when {
            "/weather/v1/current/" in path -> QWeatherMapping.current(value).optString("temp").toDoubleOrNull()?.isFinite() == true
            path.endsWith("/v7/weather/now") -> value.optJSONObject("now")?.optString("temp")?.toDoubleOrNull()?.isFinite() == true
            "/weather/v1/hourly/" in path -> value.optJSONArray("hours") != null
            "/weather/v1/daily/" in path -> value.optJSONArray("days") != null
            path.endsWith("/v7/weather/24h") -> value.optJSONArray("hourly") != null
            path.endsWith("/v7/weather/7d") || path.endsWith("/v7/indices/1d") -> value.optJSONArray("daily") != null || value.optString("code") == "204"
            path.endsWith("/geo/v2/city/lookup") -> value.optJSONArray("location") != null || value.optString("code") == "204"
            "/weatheralert/v1/current/" in path -> value.optJSONArray("alerts") != null || value.optJSONObject("metadata")?.optBoolean("zeroResult") == true
            "/airquality/v1/current/" in path -> value.optJSONArray("indexes") != null
            else -> false
        }
        if (!valid) throw WeatherServiceException("天气服务数据格式有误，请稍后重试。")
    }

    private fun coolDown(header: String?) {
        val now = timeMillis()
        val seconds = header?.toLongOrNull() ?: header?.let { value -> runCatching {
            (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now) / 1000
        }.getOrNull() } ?: 30L
        synchronized(guard) { retryAfter = maxOf(retryAfter, now + seconds.coerceIn(1, 86_400) * 1000) }
    }

    override fun close() {
        ownedClient?.let { it.dispatcher.cancelAll(); it.connectionPool.evictAll(); it.dispatcher.executorService.shutdown() }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        internal fun validateBaseUrl(raw: String): HttpUrl {
            val url = (raw.trim().trimEnd('/') + "/").toHttpUrlOrNull() ?: throw IllegalArgumentException("天气服务地址必须是有效 URL。")
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "天气服务必须使用不含账号、查询参数或片段的 HTTPS 地址。"
            }
            return url
        }
    }
}
