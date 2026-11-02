package com.thinkingweather.android.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val nightWeatherIcons = setOf("150", "151", "152", "153", "350", "351", "456", "457")

/** Weather v1 can use daytime condition codes at night and does not supply an observation time. */
fun currentWeatherIsNight(snapshot: WeatherSnapshot, nowMillis: Long = System.currentTimeMillis()): Boolean =
    weatherIsNightAt(snapshot.now.icon, snapshot.city.tz, snapshot.daily, nowMillis)

/** Forecasts use their own instant and city time zone, never the handset's time or today's phase. */
fun hourlyWeatherIsNight(hour: HourlyForecast, city: City, days: List<DailyForecast>): Boolean {
    if (hour.icon in nightWeatherIcons) return true
    val forecast = runCatching { OffsetDateTime.parse(hour.fxTime) }.getOrNull() ?: return false
    val zone = runCatching { ZoneId.of(city.tz) }.getOrDefault(forecast.offset)
    return weatherIsNightAt(hour.icon, zone.id, days, forecast.toInstant().toEpochMilli())
}

private fun weatherIsNightAt(icon: String, cityZone: String, days: List<DailyForecast>, timeMillis: Long): Boolean {
    // Retain explicit upstream night artwork, including provisional or cached observations.
    if (icon in nightWeatherIcons) return true
    val zone = runCatching { ZoneId.of(cityZone) }.getOrDefault(ZoneOffset.UTC)
    val local = runCatching { Instant.ofEpochMilli(timeMillis).atZone(zone) }.getOrNull() ?: return false
    val day = days.firstOrNull { it.fxDate == local.toLocalDate().toString() }
    if (day != null) {
        when (solarWindow(day.sunrise, day.sunset, day.fxDate, zone.id, timeMillis).phase) {
            SolarPhase.BEFORE_SUNRISE, SolarPhase.AFTER_SUNSET -> return true
            SolarPhase.DAYLIGHT -> return false
            SolarPhase.UNAVAILABLE -> Unit
        }
    }
    // Missing or polar/invalid astro data cannot provide a real day length; use a modest local-time fallback.
    return local.hour < 6 || local.hour >= 18
}
