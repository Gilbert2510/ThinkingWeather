package com.thinkingweather.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QWeatherMappingTest {
    @Test fun knownUnitsAreConvertedAndUnknownUnitsAreNotSilentlyRelabelled() {
        val current = QWeatherMapping.current(JSONObject("""{"temperature":{"value":25.25,"unit":"°C"},"humidity":0.54,"wind":{"direction":{"compass":"wsw"},"speed":{"value":5,"unit":"m/s"}},"visibility":{"value":8500,"unit":"m"},"cloudCover":0.2}"""))
        assertEquals("25.3", current.getString("temp"))
        assertEquals("54", current.getString("humidity"))
        assertEquals("18", current.getString("windSpeed"))
        assertEquals("8.5", current.getString("vis"))
        assertEquals("西南偏西风", current.getString("windDir"))
        assertFalse(current.has("obsTime"))
        assertFalse(QWeatherMapping.current(JSONObject("""{"temperature":{"value":90,"unit":"°F"},"humidity":9}""")).has("temp"))
        assertFalse(QWeatherMapping.current(JSONObject("""{"humidity":9}""")).has("humidity"))
    }

    @Test fun malformedForecastsStayOutOfChartsAndProbabilityIsConvertedToPercent() {
        val hours = QWeatherMapping.hourly(JSONObject("""{"hours":[{"forecastTime":"broken","temperature":{"value":20,"unit":"°C"}},{"forecastTime":"2026-11-02T10:00:00+08:00"},{"forecastTime":"2026-11-02T11:00:00+08:00","temperature":{"value":21,"unit":"°C"},"precipitation":{"probability":0.8}}]}"""))
        assertEquals(1, hours.length())
        assertEquals("80", hours.getJSONObject(0).getString("pop"))
        assertEquals("2026-11-02T11:00:00+08:00", hours.getJSONObject(0).getString("fxTime"))
    }

    @Test fun dailyPeriodsRetainLocalDateAstronomyAndFullDayPrecipitation() {
        val days = QWeatherMapping.daily(JSONObject("""{"days":[{"forecastStartTime":"2026-11-02T00:00:00+08:00","temperatureMax":{"value":21,"unit":"°C"},"temperatureMin":{"value":12,"unit":"°C"},"astro":{"sunrise":"2026-11-02T06:20:00+08:00","sunset":"2026-11-02T17:30:00+08:00","moonPhase":"first-quarter"},"daytime":{"precipitation":{"amount":{"value":4,"unit":"mm"}}},"nighttime":{"wind":{"direction":{"compass":"ne"},"speed":{"value":2,"unit":"m/s"}},"precipitation":{"amount":{"value":2,"unit":"mm"}}}}]}"""))
        val day = days.getJSONObject(0)
        assertEquals("2026-11-02", day.getString("fxDate"))
        assertEquals("06:20", day.getString("sunrise"))
        assertEquals("6", day.getString("precip"))
        assertEquals("7.2", day.getString("windSpeedNight"))
        assertEquals("东北风", day.getString("windDirNight"))
        assertEquals("上弦月", day.getString("moonPhase"))
    }

    @Test fun cancelledAndSupersededAlertsCannotAppearAsActiveWarnings() {
        val warnings = QWeatherMapping.warnings(JSONObject("""{"alerts":[{"id":"old","headline":"被替换"},{"id":"cancel","messageType":{"code":"cancel","supersedes":["old"]}},{"id":"active","headline":"大风","description":"注意大风","instruction":"关闭窗户","color":{"code":"orange"},"expireTime":"2026-11-02T18:00:00+08:00"}]}"""))
        assertEquals(1, warnings.length())
        assertEquals("active", warnings.getJSONObject(0).getString("id"))
        assertEquals("注意大风\n\n关闭窗户", warnings.getJSONObject(0).getString("text"))
        assertEquals("orange", warnings.getJSONObject(0).getString("severityColor"))
    }

    @Test fun indicesDoNotReuseYesterdayAdviceOrTurnOrdinalLevelsIntoUvMeasurements() {
        val indices = QWeatherMapping.indices(JSONObject("""{"daily":[{"date":"2026-11-01","type":"3","name":"旧建议","text":"旧"},{"date":"2026-11-02","type":"5","name":"紫外线指数","level":"4","category":"强"},{"date":"2026-11-02","type":"5","name":"重复","text":"重复"},{"date":"2026-11-02","type":"99","name":"未知","text":"无"}]}"""), "2026-11-02")
        assertEquals(1, indices.length())
        assertEquals("4", indices.getJSONObject(0).getString("level"))
        assertFalse(indices.getJSONObject(0).has("uvIndex"))
    }

    @Test fun metadataAndLegacyAttributionAreMergedWithoutDroppingLicenses() {
        val refer = QWeatherMapping.attribution(listOf(JSONObject("""{"metadata":{"attributions":["https://source.example"]},"refer":{"sources":["https://source.example","https://source2.example"],"license":["License A"]}}""")))
        assertEquals(2, refer.getJSONArray("sources").length())
        assertEquals("License A", refer.getJSONArray("license").getString(0))
    }
}
