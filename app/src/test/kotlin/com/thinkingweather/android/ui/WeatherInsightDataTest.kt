package com.thinkingweather.android.ui

import com.thinkingweather.android.model.TemperatureUnit
import com.thinkingweather.android.model.CurrentWeather
import com.thinkingweather.android.model.DefaultCities
import com.thinkingweather.android.model.LifeIndex
import com.thinkingweather.android.model.WeatherSnapshot
import org.junit.Assert.*
import org.junit.Test

class WeatherInsightDataTest {
    @Test fun absentAndInvalidMeasurementsDoNotBecomeZero() {
        for (value in listOf("", "—", "NaN", "Infinity", "oops")) assertNull(insightNumber(value))
        assertEquals(0f, insightNumber("0")!!, 0f)
        assertNull(insightNumber("-1", min = 0f))
        assertNull(insightNumber("101", min = 0f, max = 100f))
    }

    @Test fun temperatureChartUsesTheSelectedUnitWithoutRoundingAwayVariation() {
        assertEquals(32.9f, insightTemperature("0.5", TemperatureUnit.FAHRENHEIT)!!, .001f)
        assertEquals(-10.2f, insightTemperature("-10.2", TemperatureUnit.CELSIUS)!!, .001f)
        assertNull(insightTemperature("NaN", TemperatureUnit.CELSIUS))
    }

    @Test fun constantSeriesIsCenteredAndEmptySeriesIsNotDrawn() {
        assertNull(insightDomain(listOf(null, Float.NaN)))
        val domain = insightDomain(listOf(17f, null, 17f))!!
        assertEquals(.5f, domain.fraction(17f), .001f)
        assertTrue(domain.high > domain.low)
    }

    @Test fun zeroPrecipitationHasAValidZeroBasedChartDomain() {
        val domain = insightDomain(listOf(0f, null, 0f), zeroBaseline = true)!!
        assertEquals(0f, domain.low, 0f)
        assertEquals(0f, domain.fraction(0f), 0f)
        assertEquals(1f, insightDomain(listOf(0f, 1f), true)!!.high, 0f)
    }

    @Test fun extremeFiniteValuesStillProduceAFiniteChartDomain() {
        val domain = insightDomain(listOf(-Float.MAX_VALUE, Float.MAX_VALUE))!!
        assertTrue(domain.low.isFinite())
        assertTrue(domain.high.isFinite())
        assertEquals(.5f, domain.fraction(0f), .001f)
        assertEquals(.5f, insightDomain(listOf(1e20f, 1e20f))!!.fraction(1e20f), .001f)
    }

    @Test fun localLabelsUseTheCityTimeZoneAcrossDateBoundaries() {
        assertEquals("10/28 20:00", insightTime("2026-10-29T08:00:00+08:00", "America/New_York", true))
        assertEquals("09:00", insightTime("2026-10-29T00:00:00Z", "Asia/Tokyo"))
        assertEquals("08:00", insightTime("2026-10-29T08:00:00+08:00", "unknown"))
        assertEquals("—", insightTime("broken", "Asia/Shanghai"))
    }

    @Test fun metricLinksResolveToTheirSectionAndMissingSectionsHaveASafeFallback() {
        val sections = listOf(InsightSection("current", "实况"), InsightSection("indices", "天气指数"))
        assertEquals("indices", insightTarget("humidity", sections))
        assertEquals("indices", insightTarget("wind", sections))
        assertEquals("current", insightTarget("air", sections))
        assertEquals("", insightTarget("hourly", emptyList()))
    }

    @Test fun missingFeedsAndOrdinalLifeLevelsDoNotInventAMeasuredUvSection() {
        val snapshot = WeatherSnapshot(DefaultCities.beijing, CurrentWeather(temp = "12"), fetchedAt = 0L, isDemo = true)
        assertEquals(listOf("current"), availableInsightSections(snapshot).map { it.key })
        val lifeOnly = snapshot.copy(indices = listOf(LifeIndex(type = "5", name = "紫外线", level = "5", category = "很强", text = "测试生活建议")))
        assertEquals(listOf("current", "life"), availableInsightSections(lifeOnly).map { it.key })
    }
}
