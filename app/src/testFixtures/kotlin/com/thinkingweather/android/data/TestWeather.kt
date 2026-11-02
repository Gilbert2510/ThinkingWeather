package com.thinkingweather.android.data

import com.thinkingweather.android.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TestWeatherScenario(
    val city: City,
    val icon: String,
    val text: String,
    val temperature: Int,
    val night: Boolean = false,
) {
    val category: WeatherCategory get() = weatherCategory(icon, text)
}

/** Deterministic test fixtures. This source set is excluded from both production APK variants. */
object TestWeather {
    val catalog: List<TestWeatherScenario> = listOf(
        TestWeatherScenario(DefaultCities.beijing, "100", "晴", 16),
        TestWeatherScenario(DefaultCities.shanghai, "104", "阴", 21),
        TestWeatherScenario(DefaultCities.hangzhou, "101", "多云", 20),
        TestWeatherScenario(DefaultCities.guangzhou, "306", "中雨", 27),
        TestWeatherScenario(DefaultCities.shenzhen, "300", "阵雨", 28),
        TestWeatherScenario(DefaultCities.chengdu, "305", "小雨", 18),
        TestWeatherScenario(DefaultCities.tianjin, "150", "晴", 16, night = true),
        TestWeatherScenario(DefaultCities.nanjing, "307", "大雨", 20),
        TestWeatherScenario(DefaultCities.fuzhou, "310", "暴雨", 24),
        TestWeatherScenario(DefaultCities.xiamen, "302", "雷阵雨", 26),
        TestWeatherScenario(DefaultCities.haikou, "303", "强雷阵雨", 27),
        TestWeatherScenario(DefaultCities.wuhan, "304", "雷阵雨伴有冰雹", 18),
        TestWeatherScenario(DefaultCities.taiyuan, "400", "小雪", -1),
        TestWeatherScenario(DefaultCities.shenyang, "401", "中雪", -4),
        TestWeatherScenario(DefaultCities.harbin, "402", "大雪", -9),
        TestWeatherScenario(DefaultCities.changchun, "403", "暴雪", -8),
        TestWeatherScenario(DefaultCities.xining, "404", "雨夹雪", 2),
        TestWeatherScenario(DefaultCities.guiyang, "313", "冻雨", 0),
        TestWeatherScenario(DefaultCities.chongqing, "501", "雾", 18),
        TestWeatherScenario(DefaultCities.shijiazhuang, "502", "霾", 19),
        TestWeatherScenario(DefaultCities.hohhot, "507", "沙尘暴", 10),
        TestWeatherScenario(DefaultCities.lanzhou, "504", "浮尘", 13),
        // Wind is classified by text; it does not pretend to be an upstream wind icon code.
        TestWeatherScenario(DefaultCities.urumqi, "999", "大风", 7),
        TestWeatherScenario(DefaultCities.suzhou, "999", "未知天气", 18),
    )

    fun scenario(city: City): TestWeatherScenario = catalog.firstOrNull { it.city.location == city.location }
        ?: TestWeatherScenario(city, "999", "未知天气", 16)

    fun create(city: City, nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot {
        val sample = scenario(city)
        val temperature = sample.temperature
        val category = sample.category
        val wet = category in setOf(WeatherCategory.RAIN, WeatherCategory.THUNDERSTORM, WeatherCategory.HAIL, WeatherCategory.SLEET)
        val snow = category == WeatherCategory.SNOW
        val humidity = when {
            category == WeatherCategory.FOG -> 93
            wet -> 84
            snow -> 73
            category == WeatherCategory.DUST -> 24
            category == WeatherCategory.WIND -> 31
            category == WeatherCategory.CLEAR -> 43
            else -> 64
        }
        // The chosen scene stays in daylight (or explicit night) regardless of wall-clock hour.
        val local = Instant.ofEpochMilli(nowMillis).atZone(runCatching { ZoneId.of(city.tz) }
            .getOrDefault(ZoneId.of("Asia/Shanghai"))).withHour(if (sample.night) 21 else 11)
            .withMinute(0).withSecond(0).withNano(0)
        val precipitation = when {
            sample.icon in listOf("307", "310") -> "8.5"
            wet -> "0.8"
            snow -> "0.4"
            else -> "0.0"
        }
        fun nightIcon(): String = when (sample.icon) {
            "100", "150" -> "150"
            "101", "102", "103" -> "151"
            "300" -> "350"
            "404" -> "456"
            else -> sample.icon
        }
        val dayIcon = if (sample.icon == "150") "100" else sample.icon
        val hourly = (0 until 24).map { index ->
            val time = local.plusHours(index.toLong())
            val difference = listOf(0, 1, 2, 2, 1, 0, -1, -2, -2, -3, -3, -2)[index % 12]
            val night = time.hour < 6 || time.hour >= 18
            HourlyForecast(time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), (temperature + difference).toString(),
                sample.text, if (night) nightIcon() else dayIcon,
                if (wet || snow) (60 + index % 4 * 10).toString() else (index % 3 * 5).toString(),
                precipitation, if (category == WeatherCategory.WIND) "36" else "9", humidity.toString(),
                windDir = "东北风", windScale = if (category == WeatherCategory.WIND) "5-6" else "2-3",
                pressure = "1015", vis = "18", cloud = if (category == WeatherCategory.CLEAR) "8" else "82",
                dew = (temperature - 8).toString(), uvIndex = if (night) "0" else if (wet || snow) "2" else "4")
        }
        val daily = (0 until 7).map { index ->
            DailyForecast(local.toLocalDate().plusDays(index.toLong()).toString(), (temperature + 4 + index % 3).toString(),
                (temperature - 5 + index % 2).toString(), sample.text, dayIcon, sample.text, nightIcon(),
                "06:18", "17:32", "亏凸月", "805", if (wet || snow) "2" else "4", humidity.toString(),
                if (wet || snow) "80" else "10", precipitation, "东北风", if (category == WeatherCategory.WIND) "5-6" else "2-3",
                windSpeedDay = if (category == WeatherCategory.WIND) "36" else "9", windDirNight = "东北风",
                windScaleNight = "2-3", windSpeedNight = "9", moonrise = "19:42", moonset = "08:15")
        }
        val obscured = category in setOf(WeatherCategory.FOG, WeatherCategory.HAZE, WeatherCategory.DUST)
        return WeatherSnapshot(city, CurrentWeather(local.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), temperature.toString(),
            (temperature - 1).toString(), sample.text, sample.icon, "东北风", if (category == WeatherCategory.WIND) "5-6" else "2",
            if (category == WeatherCategory.WIND) "36" else "9", humidity.toString(), precipitation,
            "1015", if (obscured) "2" else "18", if (category == WeatherCategory.CLEAR) "8" else "82",
            dew = (temperature - 8).toString(), uvIndex = if (sample.night) "0" else if (wet || snow) "2" else "4", wind360 = "45"), hourly, daily,
            air = AirQuality(if (obscured) "156" else "36", if (obscured) "中度污染" else "优", if (obscured) "PM2.5" else "NA",
                if (obscured) "118" else "18", "31", "12", "4", "0.4", "56", pm2p5Unit = "μg/m³", pm10Unit = "μg/m³",
                no2Unit = "μg/m³", so2Unit = "μg/m³", coUnit = "mg/m³", o3Unit = "μg/m³",
                indexCode = "cn-mee", indexName = "中国 AQI",
                generalAdvice = "示例建议：关注本地空气质量，合理安排户外活动。"),
            fetchedAt = nowMillis, sourceUrl = "https://www.qweather.com/", sources = listOf("自动化测试数据"), licenses = emptyList(),
            indices = listOf(
                LifeIndex(local.toLocalDate().toString(), "3", "穿衣指数", if (temperature < 10) "2" else "4",
                    if (temperature < 10) "冷" else "较舒适", "示例建议：早晚适当增添外套，按体感调整衣物。"),
                LifeIndex(local.toLocalDate().toString(), "5", "紫外线指数", if (wet || snow) "2" else "3",
                    if (wet || snow) "弱" else "中等", "示例建议：白天外出时注意遮阳。"),
                LifeIndex(local.toLocalDate().toString(), "6", "旅游指数", if (wet || snow) "4" else "2",
                    if (wet || snow) "较不宜" else "较适宜", "示例建议：根据天气变化安排出游，留意临近预报。"),
                LifeIndex(local.toLocalDate().toString(), "16", "防晒指数", "3", "中等", "示例建议：户外活动可使用遮阳帽或遮阳伞。"),
            ))
    }
}
