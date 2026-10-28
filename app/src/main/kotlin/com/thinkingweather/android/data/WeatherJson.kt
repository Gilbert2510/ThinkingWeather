package com.thinkingweather.android.data

import com.thinkingweather.android.model.*
import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Shared codec for gateway responses and the local cache. Missing optional feeds stay missing. */
object WeatherJson {
    fun cities(json: String): List<City> {
        val root = JSONObject(json)
        checkCode(root)
        val entries = root.optJSONArray("location") ?: throw JSONException("缺少城市列表")
        return entries.objects().map(::city).filter { it.name.isNotBlank() && it.location.isNotBlank() }.distinctBy(City::location)
    }

    fun city(value: JSONObject): City = City(
        id = value.string("id"), name = value.string("name"), adm1 = value.string("adm1"),
        adm2 = value.string("adm2"), lat = value.string("lat"), lon = value.string("lon"),
        tz = value.string("tz").ifBlank { "Asia/Shanghai" },
    )

    fun snapshot(json: String, city: City, fetchedAt: Long = System.currentTimeMillis(), isDemo: Boolean = false): WeatherSnapshot {
        val root = JSONObject(json)
        checkCode(root)
        val current = root.optJSONObject("now") ?: throw JSONException("缺少实时天气")
        val temperature = current.string("temp").toDoubleOrNull()
        if (temperature == null || !temperature.isFinite()) throw JSONException("实时温度无效")
        val refer = root.optJSONObject("refer")
        val sourceUrl = if (isDemo) "" else root.string("fxLink").ifBlank { "https://www.qweather.com/" }
        return WeatherSnapshot(
            city = city,
            now = CurrentWeather(current.string("obsTime"), current.string("temp"), current.string("feelsLike"),
                current.string("text"), current.string("icon", "100"), current.string("windDir"), current.string("windScale"),
                current.string("windSpeed"), current.string("humidity"), current.string("precip"), current.string("pressure"),
                current.string("vis"), current.string("cloud")),
            hourly = root.optJSONArray("hourly").objects().map {
                HourlyForecast(it.string("fxTime"), it.string("temp"), it.string("text"), it.string("icon", "100"),
                    it.string("pop"), it.string("precip"), it.string("windSpeed"), it.string("humidity"))
            },
            daily = root.optJSONArray("daily").objects().map {
                DailyForecast(it.string("fxDate"), it.string("tempMax"), it.string("tempMin"), it.string("textDay"),
                    it.string("iconDay", "100"), it.string("textNight"), it.string("iconNight", "150"),
                    it.string("sunrise"), it.string("sunset"), it.string("moonPhase"), it.string("moonPhaseIcon"),
                    it.string("uvIndex"), it.string("humidity"), it.string("pop"), it.string("precip"),
                    it.string("windDirDay"), it.string("windScaleDay"))
            },
            warnings = root.optJSONArray("warnings").objects().map {
                WeatherWarning(it.string("id"), it.string("title"), it.string("text"), it.string("typeName"),
                    it.string("severityColor").ifBlank { it.string("level") }, it.string("startTime"), it.string("endTime"), it.string("sender"))
            },
            air = root.optJSONObject("air")?.let(::air),
            fetchedAt = root.string("fetchedAt").let { raw -> raw.toLongOrNull()
                ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
                ?: fetchedAt }, isDemo = isDemo, sourceUrl = sourceUrl,
            partialFailures = root.optJSONArray("partialFailures").strings(),
            sources = refer?.optJSONArray("sources").strings(), licenses = refer?.optJSONArray("license").strings(),
            isStale = root.optBoolean("stale", false), serverCacheAgeSeconds = root.optLong("cacheAgeSeconds", 0),
        )
    }

    private fun air(json: JSONObject): AirQuality? {
        // Accept both the legacy now feed and the current QWeather air-quality v1 schema.
        val root = json.optJSONObject("now") ?: json
        val indexes = root.optJSONArray("indexes").objects()
        val index = indexes.firstOrNull {
            it.string("code") == "cn-mee" || it.string("code") == "chn-mee"
        } ?: indexes.firstOrNull { it.string("code") == "us-epa" }
        if (index != null) {
            val pollutants = root.optJSONArray("pollutants").objects()
            fun concentration(vararg codes: String): JSONObject? = pollutants.firstOrNull { it.string("code") in codes }
                ?.optJSONObject("concentration")
            fun pollutant(vararg codes: String): String = concentration(*codes)?.string("value").orEmpty()
            fun unit(vararg codes: String): String = concentration(*codes)?.string("unit").orEmpty()
            return AirQuality(index.string("aqiDisplay").ifBlank { index.string("aqi") }, index.string("category"),
                index.optJSONObject("primaryPollutant")?.string("name").orEmpty(), pollutant("pm2p5", "pm2.5"),
                pollutant("pm10"), pollutant("no2"), pollutant("so2"), pollutant("co"), pollutant("o3"),
                unit("pm2p5", "pm2.5"), unit("pm10"), unit("no2"), unit("so2"), unit("co"), unit("o3"))
        }
        if (root.string("aqi").isBlank()) return null
        return AirQuality(root.string("aqi"), root.string("category"), root.string("primary"), root.string("pm2p5"),
            root.string("pm10"), root.string("no2"), root.string("so2"), root.string("co"), root.string("o3"),
            root.string("pm2p5Unit"), root.string("pm10Unit"), root.string("no2Unit"), root.string("so2Unit"),
            root.string("coUnit"), root.string("o3Unit"))
    }

    fun encodeCity(city: City): JSONObject = JSONObject().put("id", city.id).put("name", city.name)
        .put("adm1", city.adm1).put("adm2", city.adm2).put("lat", city.lat).put("lon", city.lon).put("tz", city.tz)

    fun encodeSnapshot(value: WeatherSnapshot): JSONObject {
        val now = value.now
        return JSONObject().put("city", encodeCity(value.city)).put("fetchedAt", value.fetchedAt).put("isDemo", value.isDemo)
            .put("stale", value.isStale).put("cacheAgeSeconds", value.serverCacheAgeSeconds)
            .put("fxLink", value.sourceUrl).put("partialFailures", JSONArray(value.partialFailures))
            .put("refer", JSONObject().put("sources", JSONArray(value.sources)).put("license", JSONArray(value.licenses)))
            .put("now", JSONObject().put("obsTime", now.obsTime).put("temp", now.temp).put("feelsLike", now.feelsLike)
                .put("text", now.text).put("icon", now.icon).put("windDir", now.windDir).put("windScale", now.windScale)
                .put("windSpeed", now.windSpeed).put("humidity", now.humidity).put("precip", now.precip).put("pressure", now.pressure)
                .put("vis", now.vis).put("cloud", now.cloud))
            .put("hourly", JSONArray().apply { value.hourly.forEach { hour -> put(JSONObject()
                .put("fxTime", hour.fxTime).put("temp", hour.temp).put("text", hour.text).put("icon", hour.icon)
                .put("pop", hour.pop).put("precip", hour.precip).put("windSpeed", hour.windSpeed).put("humidity", hour.humidity)) } })
            .put("daily", JSONArray().apply { value.daily.forEach { day -> put(JSONObject()
                .put("fxDate", day.fxDate).put("tempMax", day.tempMax).put("tempMin", day.tempMin).put("textDay", day.textDay)
                .put("iconDay", day.iconDay).put("textNight", day.textNight).put("iconNight", day.iconNight)
                .put("sunrise", day.sunrise).put("sunset", day.sunset).put("moonPhase", day.moonPhase).put("moonPhaseIcon", day.moonPhaseIcon)
                .put("uvIndex", day.uvIndex).put("humidity", day.humidity).put("pop", day.pop).put("precip", day.precip)
                .put("windDirDay", day.windDirDay).put("windScaleDay", day.windScaleDay)) } })
            .put("warnings", JSONArray().apply { value.warnings.forEach { warning -> put(JSONObject()
                .put("id", warning.id).put("title", warning.title).put("text", warning.text).put("typeName", warning.typeName)
                .put("severityColor", warning.severityColor).put("startTime", warning.startTime).put("endTime", warning.endTime).put("sender", warning.sender)) } })
            .apply { value.air?.let { air -> put("air", JSONObject().put("aqi", air.aqi).put("category", air.category)
                .put("primary", air.primary).put("pm2p5", air.pm2p5).put("pm10", air.pm10).put("no2", air.no2)
                .put("so2", air.so2).put("co", air.co).put("o3", air.o3).put("pm2p5Unit", air.pm2p5Unit)
                .put("pm10Unit", air.pm10Unit).put("no2Unit", air.no2Unit).put("so2Unit", air.so2Unit)
                .put("coUnit", air.coUnit).put("o3Unit", air.o3Unit)) } }
    }

    fun cachedSnapshot(value: JSONObject): WeatherSnapshot = snapshot(
        value.toString(), city(value.getJSONObject("city")), value.getLong("fetchedAt"), value.optBoolean("isDemo", false),
    )

    private fun checkCode(value: JSONObject) {
        val code = value.string("code")
        if (code.isNotEmpty() && code != "200" && code != "204") throw JSONException("天气服务返回 $code")
    }

    private fun JSONObject.string(key: String, fallback: String = ""): String =
        if (isNull(key)) fallback else optString(key, fallback)

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { index -> opt(index)?.takeIf { it != JSONObject.NULL }?.toString() }
}
