package com.thinkingweather.android.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WeatherDaylightTest {
    private val date = "2026-11-02"
    private fun day(date: String = this.date, rise: String = "06:30", set: String = "17:00") =
        DailyForecast(fxDate = date, sunrise = rise, sunset = set)
    private fun snapshot(city: City = DefaultCities.beijing, days: List<DailyForecast> = listOf(day()), icon: String = "100") =
        WeatherSnapshot(city, CurrentWeather(temp = "9", icon = icon), daily = days, fetchedAt = 0L)
    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun currentV1ConditionWithoutObservationTimeUsesTheCityClock() {
        val instant = at("2026-11-02T12:30:00Z")
        assertTrue(currentWeatherIsNight(snapshot(), instant)) // Beijing 20:30, even on a UTC handset.
        assertFalse(currentWeatherIsNight(snapshot(DefaultCities.beijing.copy(tz = "Europe/London")), instant))
    }

    @Test fun matchingDailyAstronomyTakesPrecedenceOverFixedHours() {
        assertTrue(currentWeatherIsNight(snapshot(), at("2026-11-02T09:30:00Z"))) // 17:30, after a 17:00 sunset.
        assertTrue(currentWeatherIsNight(snapshot(), at("2026-11-01T22:15:00Z"))) // 06:15, before 06:30 sunrise.
        assertFalse(currentWeatherIsNight(snapshot(), at("2026-11-01T22:30:00Z")))
    }

    @Test fun anotherForecastDatesSunTimesAreNeverAppliedToToday() {
        val days = listOf(day("2026-11-01", set = "16:00"), day(set = "18:00"))
        assertFalse(currentWeatherIsNight(snapshot(days = days), at("2026-11-02T08:30:00Z")))
    }

    @Test fun hourlyUnifiedDayCodesUseEachForecastInstantAndCityZone() {
        val hour = HourlyForecast(fxTime = "2026-11-03T10:00:00+08:00", icon = "100")
        assertFalse(hourlyWeatherIsNight(hour, DefaultCities.beijing, listOf(day("2026-11-03"))))
        assertTrue(hourlyWeatherIsNight(hour, DefaultCities.beijing.copy(tz = "Europe/London"), emptyList()))
        assertTrue(hourlyWeatherIsNight(hour.copy(fxTime = "2026-11-03T02:00:00+08:00"), DefaultCities.beijing, emptyList()))
    }

    @Test fun missingAndInvalidAstronomyFallsBackToLocalHours() {
        val incomplete = snapshot(days = listOf(day(rise = "", set = "invalid")))
        assertTrue(currentWeatherIsNight(incomplete, at("2026-11-02T10:00:00Z")))
        assertFalse(currentWeatherIsNight(incomplete, at("2026-11-02T04:00:00Z")))
        assertTrue(currentWeatherIsNight(snapshot(days = emptyList()), at("2026-11-01T21:00:00Z")))
        assertFalse(currentWeatherIsNight(snapshot(DefaultCities.beijing.copy(tz = "unknown")), at("2026-11-02T12:00:00Z")))
    }

    @Test fun explicitNightIconsRemainNightWithoutRequiringValidForecastTime() {
        assertTrue(currentWeatherIsNight(snapshot(icon = "150"), at("2026-11-02T04:00:00Z")))
        assertTrue(hourlyWeatherIsNight(HourlyForecast(icon = "151"), DefaultCities.beijing, emptyList()))
        assertFalse(hourlyWeatherIsNight(HourlyForecast(icon = "100", fxTime = "invalid"), DefaultCities.beijing, emptyList()))
    }
}
