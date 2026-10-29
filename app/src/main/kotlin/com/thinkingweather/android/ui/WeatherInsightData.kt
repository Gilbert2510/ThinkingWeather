package com.thinkingweather.android.ui

import com.thinkingweather.android.model.TemperatureUnit
import com.thinkingweather.android.model.WeatherSnapshot
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class InsightSection(val key: String, val title: String)

/** Invalid provider values remain gaps; zero is a valid measurement, never a missing-value sentinel. */
internal fun insightNumber(value: String, min: Float? = null, max: Float? = null): Float? =
    value.toFloatOrNull()?.takeIf { it.isFinite() && (min == null || it >= min) && (max == null || it <= max) }

internal fun insightTemperature(value: String, unit: TemperatureUnit): Float? =
    insightNumber(value)?.let { if (unit == TemperatureUnit.CELSIUS) it else it * 9f / 5f + 32f }

internal data class InsightDomain(val low: Float, val high: Float) {
    fun fraction(value: Float): Float = ((value.toDouble() - low) / (high.toDouble() - low)).toFloat().coerceIn(0f, 1f)
}

internal fun insightDomain(values: List<Float?>, zeroBaseline: Boolean = false): InsightDomain? {
    val valid = values.filterNotNull().filter(Float::isFinite)
    if (valid.isEmpty()) return null
    var low = if (zeroBaseline) 0.0 else valid.min().toDouble()
    var high = valid.max().toDouble()
    if (high <= low) {
        val padding = maxOf(1.0, kotlin.math.abs(low) * .05)
        if (zeroBaseline) high = low + padding else { low -= padding; high += padding }
    } else if (!zeroBaseline) {
        val padding = (high - low) * .15
        low -= padding
        high += padding
    }
    val limit = Float.MAX_VALUE.toDouble()
    return InsightDomain(low.coerceIn(-limit, limit).toFloat(), high.coerceIn(-limit, limit).toFloat())
}

internal fun insightTime(value: String, zone: String, withDate: Boolean = false): String = runCatching {
    val supplied = OffsetDateTime.parse(value)
    val local = runCatching { supplied.atZoneSameInstant(ZoneId.of(zone)) }.getOrNull()
    val pattern = DateTimeFormatter.ofPattern(if (withDate) "M/d HH:mm" else "HH:mm", Locale.CHINA)
    if (local != null) local.format(pattern) else supplied.format(pattern)
}.getOrDefault("—")

internal fun insightDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
}.getOrDefault("日期未提供")

internal fun insightTarget(requested: String, available: List<InsightSection>): String {
    val canonical = when (requested) {
        "wind", "humidity", "pressure", "visibility", "precip", "cloud" -> "indices"
        else -> requested
    }
    return canonical.takeIf { key -> available.any { it.key == key } } ?: available.firstOrNull()?.key.orEmpty()
}

internal fun availableInsightSections(snapshot: WeatherSnapshot): List<InsightSection> = buildList {
    add(InsightSection("current", "实况"))
    if (snapshot.hourly.isNotEmpty()) add(InsightSection("hourly", "逐小时"))
    if (snapshot.daily.isNotEmpty()) add(InsightSection("daily", "单日"))
    val now = snapshot.now
    if (listOf(now.humidity, now.windSpeed, now.precip, now.pressure, now.vis, now.cloud, now.dew).any { insightNumber(it) != null })
        add(InsightSection("indices", "天气指数"))
    if (snapshot.air != null) add(InsightSection("air", "空气"))
    if (snapshot.daily.any { insightNumber(it.uvIndex, min = 0f) != null } || insightNumber(now.uvIndex, min = 0f) != null)
        add(InsightSection("uv", "紫外线"))
    if (snapshot.daily.any { it.sunrise.isNotBlank() || it.sunset.isNotBlank() || it.moonPhase.isNotBlank() })
        add(InsightSection("sun", "日月"))
    if (snapshot.indices.any { it.name.isNotBlank() && (it.category.isNotBlank() || it.text.isNotBlank()) })
        add(InsightSection("life", "生活指数"))
}
