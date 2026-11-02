package com.thinkingweather.android.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Translates provider v1 measures into the app's stable metric schema; unknown units stay missing. */
internal object QWeatherMapping {
    private fun text(data: JSONObject, key: String): String? = data.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
    private fun number(data: JSONObject, key: String): Double? = text(data, key)?.toDoubleOrNull()?.takeIf(Double::isFinite)
    private fun objectAt(data: JSONObject, key: String) = data.optJSONObject(key) ?: JSONObject()
    private fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else
        (0 until array.length()).mapNotNull(array::optJSONObject)
    private fun formatted(value: Double): String? = value.takeIf(Double::isFinite)?.let {
        BigDecimal.valueOf(it).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
    private fun measure(data: JSONObject, key: String, target: String): String? {
        val value = objectAt(data, key)
        val amount = number(value, "value") ?: return null
        return formatted(when (text(value, "unit")) {
            target -> amount
            "m/s" -> if (target == "km/h") amount * 3.6 else return null
            "m" -> if (target == "km") amount / 1000 else return null
            else -> return null
        })
    }
    private fun JSONObject.field(key: String, value: String?) { if (value != null) put(key, value) }

    fun current(data: JSONObject): JSONObject = JSONObject().apply {
        field("temp", measure(data, "temperature", "°C")); field("feelsLike", measure(data, "feelsLike", "°C"))
        field("text", text(objectAt(data, "condition"), "text")); field("icon", text(objectAt(data, "condition"), "code"))
        val wind = objectAt(data, "wind")
        field("windSpeed", measure(wind, "speed", "km/h")); field("windScale", text(wind, "scale"))
        field("windDir", text(objectAt(wind, "direction"), "compass")?.let(::windDirection))
        field("wind360", text(objectAt(wind, "direction"), "degree"))
        field("humidity", number(data, "humidity")?.takeIf { it in 0.0..1.0 }?.let { formatted(it * 100) })
        field("cloud", number(data, "cloudCover")?.takeIf { it in 0.0..1.0 }?.let { formatted(it * 100) })
        field("precip", measure(objectAt(data, "precipitation"), "amount", "mm"))
        field("pressure", measure(data, "pressure", "hPa")); field("vis", measure(data, "visibility", "km"))
        field("dew", measure(data, "dewPoint", "°C")); field("uvIndex", text(data, "uvIndex"))
        // Current v1 supplies no observation timestamp. Transport time must never become obsTime.
    }

    fun hourly(data: JSONObject): JSONArray = JSONArray(objects(data.optJSONArray("hours")).mapNotNull { hour ->
        val result = current(hour)
        val date = text(hour, "forecastTime") ?: return@mapNotNull null
        if (runCatching { OffsetDateTime.parse(date) }.isFailure || number(result, "temp") == null) return@mapNotNull null
        result.put("fxTime", date).apply {
            field("pop", number(objectAt(hour, "precipitation"), "probability")?.takeIf { it in 0.0..1.0 }?.let { formatted(it * 100) })
        }
    })

    fun daily(data: JSONObject): JSONArray = JSONArray(objects(data.optJSONArray("days")).mapNotNull { day ->
        val date = text(day, "forecastStartTime")?.take(10) ?: return@mapNotNull null
        val maximum = measure(day, "temperatureMax", "°C") ?: return@mapNotNull null
        val minimum = measure(day, "temperatureMin", "°C") ?: return@mapNotNull null
        if (runCatching { LocalDate.parse(date) }.isFailure) return@mapNotNull null
        val daytime = objectAt(day, "daytime"); val nighttime = objectAt(day, "nighttime"); val astro = objectAt(day, "astro")
        JSONObject().apply {
            put("fxDate", date); put("tempMax", maximum); put("tempMin", minimum)
            field("textDay", text(objectAt(daytime, "condition"), "text")); field("iconDay", text(objectAt(daytime, "condition"), "code"))
            field("textNight", text(objectAt(nighttime, "condition"), "text")); field("iconNight", text(objectAt(nighttime, "condition"), "code"))
            listOf("Day" to daytime, "Night" to nighttime).forEach { (suffix, period) ->
                val wind = objectAt(period, "wind")
                field("windDir$suffix", text(objectAt(wind, "direction"), "compass")?.let(::windDirection))
                field("windScale$suffix", text(wind, "scale")); field("windSpeed$suffix", measure(wind, "speed", "km/h"))
            }
            field("humidity", number(daytime, "humidity")?.takeIf { it in 0.0..1.0 }?.let { formatted(it * 100) })
            field("uvIndex", text(day, "uvIndexMax"))
            listOf("sunrise", "sunset", "moonrise", "moonset").forEach { field(it, text(astro, it)?.let(::clockTime)) }
            field("moonPhase", text(astro, "moonPhase")?.let(::moonPhase))
            field("pop", number(objectAt(daytime, "precipitation"), "probability")?.takeIf { it in 0.0..1.0 }?.let { formatted(it * 100) })
            val dayRain = measure(objectAt(daytime, "precipitation"), "amount", "mm")?.toDoubleOrNull()
            val nightRain = measure(objectAt(nighttime, "precipitation"), "amount", "mm")?.toDoubleOrNull()
            if (dayRain != null && nightRain != null) field("precip", formatted(dayRain + nightRain))
        }
    })

    fun warnings(data: JSONObject): JSONArray {
        val alerts = objects(data.optJSONArray("alerts"))
        val superseded = alerts.flatMap { alert ->
            val ids = objectAt(alert, "messageType").optJSONArray("supersedes") ?: JSONArray()
            (0 until ids.length()).map { ids.optString(it) }
        }.toSet()
        return JSONArray(alerts.mapNotNull { alert ->
            if (text(alert, "id") in superseded || text(objectAt(alert, "messageType"), "code") == "cancel") return@mapNotNull null
            JSONObject().apply {
                field("id", text(alert, "id")); field("title", text(alert, "headline"))
                val details = listOfNotNull(text(alert, "description"), text(alert, "instruction")).filter(String::isNotBlank).distinct()
                field("text", details.joinToString("\n\n")); field("typeName", text(objectAt(alert, "eventType"), "name"))
                field("severityColor", text(objectAt(alert, "color"), "code")); field("startTime", text(alert, "effectiveTime"))
                field("endTime", text(alert, "expireTime")); field("sender", text(alert, "senderName"))
            }
        })
    }

    fun indices(data: JSONObject, forecastDate: String?): JSONArray = JSONArray(objects(data.optJSONArray("daily"))
        .filter { entry -> text(entry, "date") == forecastDate && forecastDate != null && runCatching { LocalDate.parse(forecastDate) }.isSuccess &&
            (text(entry, "type")?.toIntOrNull() ?: -1) in 1..16 && !text(entry, "name").isNullOrBlank() &&
            (!text(entry, "category").isNullOrBlank() || !text(entry, "text").isNullOrBlank()) }
        .distinctBy { text(it, "date") to text(it, "type") }
        .map { entry -> JSONObject().apply { listOf("date", "type", "name", "level", "category", "text").forEach { key -> field(key, text(entry, key)) } } })

    fun attribution(parts: Collection<JSONObject>): JSONObject {
        val sources = linkedSetOf<String>(); val licenses = linkedSetOf<String>()
        fun collect(array: JSONArray?, target: MutableSet<String>) {
            if (array != null) for (index in 0 until array.length()) array.opt(index)?.takeUnless { it == JSONObject.NULL }?.toString()?.let(target::add)
        }
        parts.forEach { value ->
            collect(objectAt(value, "metadata").optJSONArray("attributions"), sources)
            collect(objectAt(value, "refer").optJSONArray("sources"), sources)
            collect(objectAt(value, "refer").optJSONArray("license"), licenses)
        }
        return JSONObject().put("sources", JSONArray(sources)).put("license", JSONArray(licenses))
    }

    private fun clockTime(value: String) = value.substringAfter('T', "").take(5).takeIf { Regex("\\d{2}:\\d{2}").matches(it) }
    private fun moonPhase(value: String) = mapOf("new-moon" to "新月", "waxing-crescent" to "蛾眉月", "first-quarter" to "上弦月",
        "waxing-gibbous" to "盈凸月", "full-moon" to "满月", "waning-gibbous" to "亏凸月", "last-quarter" to "下弦月", "waning-crescent" to "残月")[value] ?: value
    private fun windDirection(value: String) = mapOf("n" to "北风", "nne" to "东北偏北风", "ne" to "东北风", "ene" to "东北偏东风",
        "e" to "东风", "ese" to "东南偏东风", "se" to "东南风", "sse" to "东南偏南风", "s" to "南风", "ssw" to "西南偏南风",
        "sw" to "西南风", "wsw" to "西南偏西风", "w" to "西风", "wnw" to "西北偏西风", "nw" to "西北风", "nnw" to "西北偏北风",
        "none" to "静风", "vrb" to "风向不定")[value.lowercase(Locale.ROOT)] ?: value
}
