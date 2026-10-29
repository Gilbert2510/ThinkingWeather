package com.thinkingweather.android.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class SolarPhase { BEFORE_SUNRISE, DAYLIGHT, AFTER_SUNSET, UNAVAILABLE }

data class SolarWindow(val phase: SolarPhase, val progress: Float? = null)

/** The marker uses the forecast city's date and zone, never the phone's local clock. */
fun solarWindow(sunrise: String, sunset: String, date: String, zone: String, nowMillis: Long): SolarWindow = runCatching {
    val local = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of(zone))
    if (local.toLocalDate() != LocalDate.parse(date)) return@runCatching SolarWindow(SolarPhase.UNAVAILABLE)
    val start = LocalTime.parse(sunrise).toSecondOfDay()
    val end = LocalTime.parse(sunset).toSecondOfDay()
    if (end <= start) return@runCatching SolarWindow(SolarPhase.UNAVAILABLE)
    val now = local.toLocalTime().toSecondOfDay()
    when {
        now < start -> SolarWindow(SolarPhase.BEFORE_SUNRISE)
        now > end -> SolarWindow(SolarPhase.AFTER_SUNSET)
        else -> SolarWindow(SolarPhase.DAYLIGHT, (now - start).toFloat() / (end - start))
    }
}.getOrDefault(SolarWindow(SolarPhase.UNAVAILABLE))
