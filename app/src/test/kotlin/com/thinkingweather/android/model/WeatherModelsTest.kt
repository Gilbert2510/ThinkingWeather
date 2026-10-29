package com.thinkingweather.android.model

import com.thinkingweather.android.data.DemoWeather
import java.time.OffsetDateTime
import org.junit.Assert.*
import org.junit.Test

class WeatherModelsTest {
    @Test fun legacyCachedWindDirectionsHaveReadableDescriptions() {
        assertEquals("西南偏西风", displayWindDirection("西西南风"))
        assertEquals("东北偏北风", displayWindDirection("NNE"))
        assertEquals("东南风", displayWindDirection("东南风"))
        assertEquals("风向不定", displayWindDirection("风向不定"))
    }

    @Test fun temperatureConvertsAndLabelsCorrectly() {
        assertEquals("32°F", TemperatureUnit.FAHRENHEIT.format("0", true))
        assertEquals("-40°", TemperatureUnit.FAHRENHEIT.format("-40"))
        assertEquals("21℃", TemperatureUnit.CELSIUS.format("20.6", true))
        assertEquals("21℃", TemperatureUnit.CELSIUS.format("20.6"))
        assertEquals("-10℃", TemperatureUnit.CELSIUS.format("-10"))
        assertEquals("21", TemperatureUnit.CELSIUS.number("20.6"))
        assertEquals("—", TemperatureUnit.CELSIUS.format(""))
        assertEquals("—", TemperatureUnit.CELSIUS.format("NaN"))
    }

    @Test fun demosAreExplicitReproducibleAndCitySpecific() {
        val time = 1793152800000L
        val beijing = DemoWeather.create(DefaultCities.beijing, time)
        assertEquals(beijing, DemoWeather.create(DefaultCities.beijing, time))
        assertTrue(beijing.isDemo)
        assertEquals(24, beijing.hourly.size)
        assertEquals(7, beijing.daily.size)
        assertNotEquals(beijing.now.temp, DemoWeather.create(DefaultCities.shenzhen, time).now.temp)
        assertEquals("", beijing.sourceUrl)
    }

    @Test fun demoSearchAcceptsChineseAndPinyin() {
        assertEquals(DefaultCities.hangzhou, DefaultCities.search("hangzhou").single())
        assertEquals(DefaultCities.shenzhen, DefaultCities.search(" 深圳 ").single())
        assertTrue(DefaultCities.search("doesnotexist").isEmpty())
    }

    @Test fun catalogContainsEverySupportedVisualCategoryAndKeepsCityMappingFixed() {
        assertEquals(DefaultCities.demoCatalog, DemoWeather.catalog.map { it.city })
        assertEquals(DemoWeather.catalog.size, DemoWeather.catalog.distinctBy { it.city.location }.size)
        assertEquals(WeatherCategory.entries.toSet(), DemoWeather.catalog.map { it.category }.toSet())
        assertEquals("晴", DemoWeather.scenario(DefaultCities.beijing).text)
        assertEquals("阴", DemoWeather.scenario(DefaultCities.shanghai).text)
        assertEquals(16, DemoWeather.scenario(DefaultCities.beijing).temperature)
        assertEquals(28, DemoWeather.scenario(DefaultCities.shenzhen).temperature)
        assertTrue(DemoWeather.catalog.any { it.text == "小雨" })
        assertTrue(DemoWeather.catalog.any { it.text == "大雪" })
    }

    @Test fun demoConditionsAndDaylightDoNotChangeWithClockOrSeason() {
        val times = listOf(1793152800000L, 1793199600000L, 1809000000000L)
        for (sample in DemoWeather.catalog) {
            for (time in times) {
                val snapshot = DemoWeather.create(sample.city, time)
                assertTrue(snapshot.isDemo)
                assertEquals(sample.icon, snapshot.now.icon)
                assertEquals(sample.text, snapshot.now.text)
                assertEquals(sample.temperature.toString(), snapshot.now.temp)
                assertEquals(if (sample.night) 21 else 11, OffsetDateTime.parse(snapshot.now.obsTime).hour)
                assertEquals(time, snapshot.fetchedAt)
                assertEquals(24, snapshot.hourly.size)
                assertEquals(7, snapshot.daily.size)
            }
        }
    }

    @Test fun weatherClassificationKeepsMixedAndSevereConditionsDistinct() {
        assertEquals(WeatherCategory.HAIL, weatherCategory("304", "雷阵雨伴有冰雹"))
        assertEquals(WeatherCategory.THUNDERSTORM, weatherCategory("302", "雷阵雨"))
        assertEquals(WeatherCategory.SLEET, weatherCategory("404", "雨夹雪"))
        assertEquals(WeatherCategory.SLEET, weatherCategory("313", "冻雨"))
        assertEquals(WeatherCategory.SNOW, weatherCategory("402", "大雪"))
        assertEquals(WeatherCategory.SNOW, weatherCategory("401", ""))
        assertEquals(WeatherCategory.RAIN, weatherCategory("350", "阵雨"))
        assertEquals(WeatherCategory.OVERCAST, weatherCategory("104", "阴"))
        assertEquals(WeatherCategory.WIND, weatherCategory("999", "大风"))
        assertEquals(WeatherCategory.UNKNOWN, weatherCategory("999", "未知天气"))
        assertEquals(WeatherCategory.UNKNOWN, weatherCategory("", ""))
    }

    @Test fun expandedDemoSearchFindsSnowAndNightCities() {
        assertEquals(DefaultCities.harbin, DefaultCities.search("haerbin").single())
        assertEquals(DefaultCities.tianjin, DefaultCities.search("tianjin").single())
        assertEquals(DefaultCities.urumqi, DefaultCities.search("乌鲁木齐").single())
    }

    @Test fun cityStorageLimitAllowsThirtyUniqueValidCitiesAndPreservesOrder() {
        val candidates = (1..35).map { City("$it", "城市$it") }
        val normalized = normalizeSavedCities(listOf(City("", "无位置"), City("bad", ""), candidates.first()) + candidates)
        assertEquals(30, normalized.size)
        assertEquals(candidates.take(30), normalized)
        assertEquals(candidates.take(30), normalizeSavedCities(candidates.take(30)))
    }
}
