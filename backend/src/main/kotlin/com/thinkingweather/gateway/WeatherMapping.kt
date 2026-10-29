package com.thinkingweather.gateway

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.objectAt(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.arrayAt(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())

/** Stable app schema. V1 fractions, m/s and meters are converted to %, km/h and km. Missing stays missing. */
object WeatherMapping {
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
    private fun format(number: Double) = BigDecimal.valueOf(number).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    private fun measure(data: JsonObject, key: String, target: String): String? {
        val value = data.objectAt(key)
        val number = value.number("value") ?: return null
        val converted = when (value.text("unit")) {
            target -> number
            "m/s" if target == "km/h" -> number * 3.6
            "m" if target == "km" -> number / 1000
            // Unknown upstream units must never be silently mislabeled by the app.
            else -> return null
        }
        return format(converted)
    }

    fun current(data: JsonObject): JsonObject = buildJsonObject {
        fun field(name: String, value: String?) { if (value != null) put(name, value) }
        field("temp", measure(data, "temperature", "°C"))
        field("feelsLike", measure(data, "feelsLike", "°C"))
        field("icon", data.objectAt("condition").text("code"))
        field("text", data.objectAt("condition").text("text"))
        val wind = data.objectAt("wind")
        field("windSpeed", measure(wind, "speed", "km/h"))
        field("windDir", wind.objectAt("direction").text("compass")?.let(::windDirection))
        field("wind360", wind.objectAt("direction").text("degree"))
        field("windScale", wind.text("scale"))
        field("humidity", data.number("humidity")?.let { format(it * 100) })
        field("cloud", data.number("cloudCover")?.let { format(it * 100) })
        field("precip", measure(data.objectAt("precipitation"), "amount", "mm"))
        field("pressure", measure(data, "pressure", "hPa"))
        field("vis", measure(data, "visibility", "km"))
        field("dew", measure(data, "dewPoint", "°C"))
        field("uvIndex", data.text("uvIndex"))
        // Current v1 publishes no observation timestamp. fetchedAt is transport time, never obsTime.
    }

    fun hourly(data: JsonObject): JsonArray = JsonArray(data.arrayAt("hours").mapNotNull { element ->
        val hour = element as? JsonObject ?: return@mapNotNull null
        JsonObject(current(hour) + buildJsonObject {
            hour.text("forecastTime")?.let { put("fxTime", it) }
            hour.objectAt("precipitation").number("probability")?.let { put("pop", format(it * 100)) }
        })
    })

    fun daily(data: JsonObject): JsonArray = JsonArray(data.arrayAt("days").mapNotNull { element ->
        val day = element as? JsonObject ?: return@mapNotNull null
        val daytime = day.objectAt("daytime")
        val nighttime = day.objectAt("nighttime")
        buildJsonObject {
            fun field(name: String, value: String?) { if (value != null) put(name, value) }
            // localTime=true makes the forecast start date the requested location's calendar date.
            field("fxDate", day.text("forecastStartTime")?.take(10))
            field("tempMax", measure(day, "temperatureMax", "°C"))
            field("tempMin", measure(day, "temperatureMin", "°C"))
            field("iconDay", daytime.objectAt("condition").text("code"))
            field("textDay", daytime.objectAt("condition").text("text"))
            field("iconNight", nighttime.objectAt("condition").text("code"))
            field("textNight", nighttime.objectAt("condition").text("text"))
            field("windDirDay", daytime.objectAt("wind").objectAt("direction").text("compass")?.let(::windDirection))
            field("windScaleDay", daytime.objectAt("wind").text("scale"))
            field("windSpeedDay", measure(daytime.objectAt("wind"), "speed", "km/h"))
            field("windDirNight", nighttime.objectAt("wind").objectAt("direction").text("compass")?.let(::windDirection))
            field("windScaleNight", nighttime.objectAt("wind").text("scale"))
            field("windSpeedNight", measure(nighttime.objectAt("wind"), "speed", "km/h"))
            field("humidity", daytime.number("humidity")?.let { format(it * 100) })
            field("uvIndex", day.text("uvIndexMax"))
            field("sunrise", day.objectAt("astro").text("sunrise")?.let(::clockTime))
            field("sunset", day.objectAt("astro").text("sunset")?.let(::clockTime))
            field("sunriseTime", day.objectAt("astro").text("sunrise"))
            field("sunsetTime", day.objectAt("astro").text("sunset"))
            field("moonrise", day.objectAt("astro").text("moonrise")?.let(::clockTime))
            field("moonset", day.objectAt("astro").text("moonset")?.let(::clockTime))
            field("moonPhase", day.objectAt("astro").text("moonPhase")?.let(::moonPhase))
            field("pop", daytime.objectAt("precipitation").number("probability")?.let { format(it * 100) })
            val dayRain = measure(daytime.objectAt("precipitation"), "amount", "mm")?.toDoubleOrNull()
            val nightRain = measure(nighttime.objectAt("precipitation"), "amount", "mm")?.toDoubleOrNull()
            if (dayRain != null && nightRain != null) field("precip", format(dayRain + nightRain))
        }
    })

    /** Reject incomplete entries and old-day cached advice instead of relabelling it as today's. */
    fun indices(data: JsonObject, forecastDate: String?): JsonArray = JsonArray(data.arrayAt("daily")
        .filterIsInstance<JsonObject>()
        .filter { entry ->
            val date = entry.text("date")
            val validDate = date != null && runCatching { java.time.LocalDate.parse(date) }.isSuccess
            validDate && forecastDate != null && date == forecastDate &&
                (entry.text("type")?.toIntOrNull() ?: -1) in 1..16 && !entry.text("name").isNullOrBlank() &&
                (!entry.text("category").isNullOrBlank() || !entry.text("text").isNullOrBlank())
        }
        .distinctBy { it.text("date") to it.text("type") }
        .map { entry -> buildJsonObject {
            listOf("date", "type", "name", "level", "category", "text").forEach { key ->
                entry.text(key)?.let { put(key, it) }
            }
        } })

    fun warnings(data: JsonObject): JsonArray {
        val alerts = data.arrayAt("alerts")
        val superseded = alerts.filterIsInstance<JsonObject>().flatMap { it.objectAt("messageType").arrayAt("supersedes") }.toSet()
        return JsonArray(alerts.mapNotNull { element ->
            val alert = element as? JsonObject ?: return@mapNotNull null
            // A cancel/update can refer to an earlier message in the same response.
            if (alert["id"] in superseded || alert.objectAt("messageType").text("code") == "cancel") return@mapNotNull null
            JsonObject(alert + buildJsonObject {
                alert.text("headline")?.let { put("title", it) }
                val details = listOfNotNull(alert.text("description"), alert.text("instruction")).filter(String::isNotBlank).distinct()
                if (details.isNotEmpty()) put("text", details.joinToString("\n\n"))
                alert.text("issuedTime")?.let { put("pubTime", it) }
                alert.objectAt("eventType").text("name")?.let { put("typeName", it) }
                alert.text("severity")?.let { put("severity", it) }
                alert.objectAt("color").text("code")?.let { put("severityColor", it) }
                alert.text("effectiveTime")?.let { put("startTime", it) }
                alert.text("expireTime")?.let { put("endTime", it) }
                alert.text("senderName")?.let { put("sender", it) }
            })
        })
    }

    fun attribution(data: Iterable<JsonObject>): JsonObject {
        val sources = linkedSetOf<JsonElement>()
        val licenses = linkedSetOf<JsonElement>()
        data.forEach { response ->
            sources.addAll(response.objectAt("metadata").arrayAt("attributions"))
            sources.addAll(response.objectAt("refer").arrayAt("sources"))
            licenses.addAll(response.objectAt("refer").arrayAt("license"))
        }
        return buildJsonObject { put("sources", JsonArray(sources.toList())); put("license", JsonArray(licenses.toList())) }
    }

    private fun clockTime(iso: String) = iso.substringAfter('T', "").take(5).takeIf { Regex("\\d{2}:\\d{2}").matches(it) }
    private fun moonPhase(value: String) = mapOf(
        "new-moon" to "新月", "waxing-crescent" to "蛾眉月", "first-quarter" to "上弦月", "waxing-gibbous" to "盈凸月",
        "full-moon" to "满月", "waning-gibbous" to "亏凸月", "last-quarter" to "下弦月", "waning-crescent" to "残月",
    )[value] ?: value
    private fun windDirection(code: String) = mapOf(
        "n" to "北风", "nne" to "东北偏北风", "ne" to "东北风", "ene" to "东北偏东风", "e" to "东风", "ese" to "东南偏东风",
        "se" to "东南风", "sse" to "东南偏南风", "s" to "南风", "ssw" to "西南偏南风", "sw" to "西南风", "wsw" to "西南偏西风",
        "w" to "西风", "wnw" to "西北偏西风", "nw" to "西北风", "nnw" to "西北偏北风", "none" to "静风", "vrb" to "风向不定",
    )[code.lowercase()] ?: code
}
