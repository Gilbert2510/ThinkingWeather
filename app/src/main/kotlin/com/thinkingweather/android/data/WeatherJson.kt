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

    fun currentSummary(json: String, city: City, fetchedAt: Long = System.currentTimeMillis()): CityWeatherSummary {
        val root = JSONObject(json)
        checkCode(root)
        val now = root.optJSONObject("now") ?: throw JSONException("缺少实时天气")
        val temp = now.string("temp")
        if (temp.toDoubleOrNull()?.isFinite() != true) throw JSONException("实时温度无效")
        val refer = root.optJSONObject("refer")
        val age = root.optLong("cacheAgeSeconds", 0).coerceIn(0, 604_800)
        return CityWeatherSummary(city, temp, now.string("text"), now.string("icon", "999"),
            root.string("fetchedAt").let { raw -> raw.toLongOrNull()
                ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                ?: fetchedAt - age * 1000 },
            root.optBoolean("stale", false), refer?.optJSONArray("sources").strings(),
            refer?.optJSONArray("license").strings())
    }

    fun encodeCurrentSummary(value: CityWeatherSummary): JSONObject = JSONObject()
        .put("city", encodeCity(value.city)).put("fetchedAt", value.fetchedAt).put("stale", value.isStale)
        .put("now", JSONObject().put("temp", value.temp).put("text", value.text).put("icon", value.icon))
        .put("refer", JSONObject().put("sources", JSONArray(value.sources)).put("license", JSONArray(value.licenses)))

    fun cachedCurrentSummary(value: JSONObject): CityWeatherSummary =
        currentSummary(value.toString(), city(value.getJSONObject("city")), value.getLong("fetchedAt"))

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
                current.string("vis"), current.string("cloud"), current.string("dew"), current.string("uvIndex"), current.string("wind360")),
            hourly = root.optJSONArray("hourly").objects().map {
                HourlyForecast(it.string("fxTime"), it.string("temp"), it.string("text"), it.string("icon", "100"),
                    it.string("pop"), it.string("precip"), it.string("windSpeed"), it.string("humidity"),
                    it.string("windDir"), it.string("windScale"), it.string("pressure"), it.string("vis"),
                    it.string("cloud"), it.string("dew"), it.string("uvIndex"))
            },
            daily = root.optJSONArray("daily").objects().map {
                DailyForecast(it.string("fxDate"), it.string("tempMax"), it.string("tempMin"), it.string("textDay"),
                    it.string("iconDay", "100"), it.string("textNight"), it.string("iconNight", "150"),
                    it.string("sunrise"), it.string("sunset"), it.string("moonPhase"), it.string("moonPhaseIcon"),
                    it.string("uvIndex"), it.string("humidity"), it.string("pop"), it.string("precip"),
                    it.string("windDirDay"), it.string("windScaleDay"), it.string("windSpeedDay"),
                    it.string("windDirNight"), it.string("windScaleNight"), it.string("windSpeedNight"),
                    it.string("moonrise"), it.string("moonset"))
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
            indices = root.optJSONArray("indices").objects().map {
                LifeIndex(it.string("date"), it.string("type"), it.string("name"), it.string("level"),
                    it.string("category"), it.string("text"))
            }.filter { it.name.isNotBlank() && (it.type.toIntOrNull() ?: -1) in 1..16 &&
                runCatching { java.time.LocalDate.parse(it.date) }.isSuccess &&
                (it.category.isNotBlank() || it.text.isNotBlank()) }
                .distinctBy { it.date to it.type },
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
                unit("pm2p5", "pm2.5"), unit("pm10"), unit("no2"), unit("so2"), unit("co"), unit("o3"),
                index.string("code"), index.string("name"), index.optJSONObject("health")?.string("effect").orEmpty(),
                index.optJSONObject("health")?.optJSONObject("advice")?.string("generalPopulation").orEmpty(),
                index.optJSONObject("health")?.optJSONObject("advice")?.string("sensitivePopulation").orEmpty())
        }
        if (root.string("aqi").isBlank()) return null
        return AirQuality(root.string("aqi"), root.string("category"), root.string("primary"), root.string("pm2p5"),
            root.string("pm10"), root.string("no2"), root.string("so2"), root.string("co"), root.string("o3"),
            root.string("pm2p5Unit"), root.string("pm10Unit"), root.string("no2Unit"), root.string("so2Unit"),
            root.string("coUnit"), root.string("o3Unit"), root.string("indexCode"), root.string("indexName"),
            root.string("healthEffect"), root.string("generalAdvice"), root.string("sensitiveAdvice"))
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
                .put("vis", now.vis).put("cloud", now.cloud).put("dew", now.dew).put("uvIndex", now.uvIndex).put("wind360", now.wind360))
            .put("hourly", JSONArray().apply { value.hourly.forEach { hour -> put(JSONObject()
                .put("fxTime", hour.fxTime).put("temp", hour.temp).put("text", hour.text).put("icon", hour.icon)
                .put("pop", hour.pop).put("precip", hour.precip).put("windSpeed", hour.windSpeed).put("humidity", hour.humidity)
                .put("windDir", hour.windDir).put("windScale", hour.windScale).put("pressure", hour.pressure).put("vis", hour.vis)
                .put("cloud", hour.cloud).put("dew", hour.dew).put("uvIndex", hour.uvIndex)) } })
            .put("daily", JSONArray().apply { value.daily.forEach { day -> put(JSONObject()
                .put("fxDate", day.fxDate).put("tempMax", day.tempMax).put("tempMin", day.tempMin).put("textDay", day.textDay)
                .put("iconDay", day.iconDay).put("textNight", day.textNight).put("iconNight", day.iconNight)
                .put("sunrise", day.sunrise).put("sunset", day.sunset).put("moonPhase", day.moonPhase).put("moonPhaseIcon", day.moonPhaseIcon)
                .put("uvIndex", day.uvIndex).put("humidity", day.humidity).put("pop", day.pop).put("precip", day.precip)
                .put("windDirDay", day.windDirDay).put("windScaleDay", day.windScaleDay).put("windSpeedDay", day.windSpeedDay)
                .put("windDirNight", day.windDirNight).put("windScaleNight", day.windScaleNight).put("windSpeedNight", day.windSpeedNight)
                .put("moonrise", day.moonrise).put("moonset", day.moonset)) } })
            .put("indices", JSONArray().apply { value.indices.forEach { index -> put(JSONObject()
                .put("date", index.date).put("type", index.type).put("name", index.name).put("level", index.level)
                .put("category", index.category).put("text", index.text)) } })
            .put("warnings", JSONArray().apply { value.warnings.forEach { warning -> put(JSONObject()
                .put("id", warning.id).put("title", warning.title).put("text", warning.text).put("typeName", warning.typeName)
                .put("severityColor", warning.severityColor).put("startTime", warning.startTime).put("endTime", warning.endTime).put("sender", warning.sender)) } })
            .apply { value.air?.let { air -> put("air", JSONObject().put("aqi", air.aqi).put("category", air.category)
                .put("primary", air.primary).put("pm2p5", air.pm2p5).put("pm10", air.pm10).put("no2", air.no2)
                .put("so2", air.so2).put("co", air.co).put("o3", air.o3).put("pm2p5Unit", air.pm2p5Unit)
                .put("pm10Unit", air.pm10Unit).put("no2Unit", air.no2Unit).put("so2Unit", air.so2Unit)
                .put("coUnit", air.coUnit).put("o3Unit", air.o3Unit).put("indexCode", air.indexCode).put("indexName", air.indexName)
                .put("healthEffect", air.healthEffect).put("generalAdvice", air.generalAdvice).put("sensitiveAdvice", air.sensitiveAdvice)) } }
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
