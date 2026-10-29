package com.thinkingweather.android.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SolarWindowTest {
    private fun at(time: String, zone: String = "Asia/Shanghai", date: String = "2026-10-29") =
        solarWindow("06:00", "18:00", date, zone, Instant.parse(time).toEpochMilli())

    @Test fun daylightMarkerUsesTheForecastCityZone() {
        assertEquals(.5f, at("2026-10-29T04:00:00Z").progress!!, .0001f)
        assertEquals(SolarPhase.BEFORE_SUNRISE, at("2026-10-29T04:00:00Z", "UTC").phase)
    }

    @Test fun beforeAndAfterDaylightDoNotInventASunAboveTheHorizon() {
        assertEquals(SolarPhase.BEFORE_SUNRISE, at("2026-10-28T21:00:00Z").phase)
        assertEquals(SolarPhase.AFTER_SUNSET, at("2026-10-29T12:00:00Z").phase)
        assertNull(at("2026-10-29T12:00:00Z").progress)
        assertEquals(0f, at("2026-10-28T22:00:00Z").progress!!, .0001f)
        assertEquals(1f, at("2026-10-29T10:00:00Z").progress!!, .0001f)
    }

    @Test fun missingInvalidOrDifferentDayTimesStayUnavailable() {
        val now = Instant.parse("2026-10-29T04:00:00Z").toEpochMilli()
        for ((rise, set) in listOf("" to "18:00", "24:80" to "18:00", "18:00" to "06:00")) {
            assertEquals(SolarPhase.UNAVAILABLE, solarWindow(rise, set, "2026-10-29", "Asia/Shanghai", now).phase)
        }
        assertEquals(SolarPhase.UNAVAILABLE, at("2026-10-29T04:00:00Z", date = "2026-10-28").phase)
        assertEquals(SolarPhase.UNAVAILABLE, at("2026-10-29T04:00:00Z", zone = "invalid-zone").phase)
    }
}
