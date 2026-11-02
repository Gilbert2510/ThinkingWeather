package com.thinkingweather.android.model

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

    @Test fun cityStorageLimitAllowsThirtyUniqueValidCitiesAndPreservesOrder() {
        val candidates = (1..35).map { City("$it", "城市$it") }
        val normalized = normalizeSavedCities(listOf(City("", "无位置"), City("bad", ""), candidates.first()) + candidates)
        assertEquals(30, normalized.size)
        assertEquals(candidates.take(30), normalized)
        assertEquals(candidates.take(30), normalizeSavedCities(candidates.take(30)))
    }
}
