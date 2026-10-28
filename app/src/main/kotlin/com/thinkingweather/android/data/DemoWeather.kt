package com.thinkingweather.android.data

import com.thinkingweather.android.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DemoWeatherScenario(
    val city: City,
    val icon: String,
    val text: String,
    val temperature: Int,
    val night: Boolean = false,
) {
    val category: WeatherCategory get() = weatherCategory(icon, text)
}

/** Local, fixed weather scenarios. A failed live request never falls back to these values. */
object DemoWeather {
    val catalog: List<DemoWeatherScenario> = listOf(
        DemoWeatherScenario(DefaultCities.beijing, "100", "晴", 16),
        DemoWeatherScenario(DefaultCities.shanghai, "104", "阴", 21),
        DemoWeatherScenario(DefaultCities.hangzhou, "101", "多云", 20),
        DemoWeatherScenario(DefaultCities.guangzhou, "306", "中雨", 27),
        DemoWeatherScenario(DefaultCities.shenzhen, "300", "阵雨", 28),
        DemoWeatherScenario(DefaultCities.chengdu, "305", "小雨", 18),
        DemoWeatherScenario(DefaultCities.tianjin, "150", "晴", 16, night = true),
        DemoWeatherScenario(DefaultCities.nanjing, "307", "大雨", 20),
        DemoWeatherScenario(DefaultCities.fuzhou, "310", "暴雨", 24),
        DemoWeatherScenario(DefaultCities.xiamen, "302", "雷阵雨", 26),
        DemoWeatherScenario(DefaultCities.haikou, "303", "强雷阵雨", 27),
        DemoWeatherScenario(DefaultCities.wuhan, "304", "雷阵雨伴有冰雹", 18),
        DemoWeatherScenario(DefaultCities.taiyuan, "400", "小雪", -1),
        DemoWeatherScenario(DefaultCities.shenyang, "401", "中雪", -4),
        DemoWeatherScenario(DefaultCities.harbin, "402", "大雪", -9),
        DemoWeatherScenario(DefaultCities.changchun, "403", "暴雪", -8),
        DemoWeatherScenario(DefaultCities.xining, "404", "雨夹雪", 2),
        DemoWeatherScenario(DefaultCities.guiyang, "313", "冻雨", 0),
        DemoWeatherScenario(DefaultCities.chongqing, "501", "雾", 18),
        DemoWeatherScenario(DefaultCities.shijiazhuang, "502", "霾", 19),
        DemoWeatherScenario(DefaultCities.hohhot, "507", "沙尘暴", 10),
        DemoWeatherScenario(DefaultCities.lanzhou, "504", "浮尘", 13),
        // Wind is classified by text; it does not pretend to be an upstream wind icon code.
        DemoWeatherScenario(DefaultCities.urumqi, "999", "大风", 7),
        DemoWeatherScenario(DefaultCities.suzhou, "999", "未知天气", 18),
    )

    fun scenario(city: City): DemoWeatherScenario = catalog.firstOrNull { it.city.location == city.location }
        ?: DemoWeatherScenario(city, "999", "未知天气", 16)

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
                precipitation, if (category == WeatherCategory.WIND) "36" else "9", humidity.toString())
        }
        val daily = (0 until 7).map { index ->
            DailyForecast(local.toLocalDate().plusDays(index.toLong()).toString(), (temperature + 4 + index % 3).toString(),
                (temperature - 5 + index % 2).toString(), sample.text, dayIcon, sample.text, nightIcon(),
                "06:18", "17:32", "亏凸月", "805", if (wet || snow) "2" else "4", humidity.toString(),
                if (wet || snow) "80" else "10", precipitation, "东北风", if (category == WeatherCategory.WIND) "5-6" else "2-3")
        }
        val obscured = category in setOf(WeatherCategory.FOG, WeatherCategory.HAZE, WeatherCategory.DUST)
        return WeatherSnapshot(city, CurrentWeather(local.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), temperature.toString(),
            (temperature - 1).toString(), sample.text, sample.icon, "东北风", if (category == WeatherCategory.WIND) "5-6" else "2",
            if (category == WeatherCategory.WIND) "36" else "9", humidity.toString(), precipitation,
            "1015", if (obscured) "2" else "18", if (category == WeatherCategory.CLEAR) "8" else "82"), hourly, daily,
            air = AirQuality(if (obscured) "156" else "36", if (obscured) "中度污染" else "优", if (obscured) "PM2.5" else "NA",
                if (obscured) "118" else "18", "31", "12", "4", "0.4", "56", pm2p5Unit = "μg/m³", pm10Unit = "μg/m³",
                no2Unit = "μg/m³", so2Unit = "μg/m³", coUnit = "mg/m³", o3Unit = "μg/m³"),
            fetchedAt = nowMillis, isDemo = true, sourceUrl = "", sources = listOf("本地演示数据"), licenses = emptyList())
    }
}
