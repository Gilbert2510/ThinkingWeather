package com.thinkingweather.android.model

import java.util.Locale
import kotlin.math.roundToInt

data class City(
    val id: String,
    val name: String,
    val adm1: String = "",
    val adm2: String = "",
    val lat: String = "",
    val lon: String = "",
    val tz: String = "Asia/Shanghai",
) {
    val location: String get() = id.ifBlank { if (lon.isNotBlank() && lat.isNotBlank()) "$lon,$lat" else "" }
    val description: String get() = listOf(adm2, adm1).filter { it.isNotBlank() && it != name }.distinct().joinToString(" · ")
}

internal const val MAX_SAVED_CITIES = 30

/** One bound is used for both loading old preferences and saving new selections. */
internal fun normalizeSavedCities(cities: List<City>): List<City> = cities
    .filter { it.name.isNotBlank() && it.location.isNotBlank() }
    .distinctBy(City::location)
    .take(MAX_SAVED_CITIES)

/** Shared by the illustration and background, including unsupported-code fallback. */
enum class WeatherCategory { CLEAR, CLOUDY, OVERCAST, RAIN, THUNDERSTORM, HAIL, SNOW, SLEET, FOG, HAZE, DUST, WIND, UNKNOWN }

fun weatherCategory(icon: String, text: String): WeatherCategory {
    val code = icon.toIntOrNull() ?: -1
    val description = text.trim().lowercase(Locale.ROOT)
    return when {
        description.contains("雹") || code == 304 -> WeatherCategory.HAIL
        description.contains("雷") || code in listOf(302, 303) -> WeatherCategory.THUNDERSTORM
        description.contains("雨夹雪") || description.contains("雨雪") || description.contains("雪夹雨") ||
            description.contains("冻雨") || code in listOf(313, 404, 405, 406, 456) -> WeatherCategory.SLEET
        description.contains("雪") || code in 400..499 -> WeatherCategory.SNOW
        description.contains("雨") || code in 300..399 -> WeatherCategory.RAIN
        description.contains("沙") || description.contains("尘") || code in listOf(503, 504, 507, 508) -> WeatherCategory.DUST
        description.contains("雾") || code in listOf(501, 509, 510, 514, 515) -> WeatherCategory.FOG
        description.contains("霾") || code in listOf(502, 511, 512, 513) -> WeatherCategory.HAZE
        description.contains("大风") || description.contains("强风") || description.contains("飓风") ||
            description.contains("龙卷风") || description.contains("wind") -> WeatherCategory.WIND
        description.contains("阴") || code == 104 -> WeatherCategory.OVERCAST
        description.contains("云") || code in listOf(101, 102, 103, 151, 152, 153) -> WeatherCategory.CLOUDY
        description == "晴" || code in listOf(100, 150) -> WeatherCategory.CLEAR
        else -> WeatherCategory.UNKNOWN
    }
}

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT;

    fun format(celsius: String, includeUnit: Boolean = false): String {
        val value = celsius.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return "—"
        val temperature = (if (this == FAHRENHEIT) value * 9 / 5 + 32 else value).roundToInt()
        val suffix = if (includeUnit) (if (this == FAHRENHEIT) "°F" else "°C") else "°"
        return "$temperature$suffix"
    }
}

data class CurrentWeather(
    val obsTime: String = "", val temp: String = "", val feelsLike: String = "",
    val text: String = "", val icon: String = "100", val windDir: String = "",
    val windScale: String = "", val windSpeed: String = "", val humidity: String = "",
    val precip: String = "", val pressure: String = "", val vis: String = "", val cloud: String = "",
)

data class HourlyForecast(
    val fxTime: String = "", val temp: String = "", val text: String = "", val icon: String = "100",
    val pop: String = "", val precip: String = "", val windSpeed: String = "", val humidity: String = "",
)

data class DailyForecast(
    val fxDate: String = "", val tempMax: String = "", val tempMin: String = "",
    val textDay: String = "", val iconDay: String = "100", val textNight: String = "", val iconNight: String = "150",
    val sunrise: String = "", val sunset: String = "", val moonPhase: String = "", val moonPhaseIcon: String = "",
    val uvIndex: String = "", val humidity: String = "", val pop: String = "", val precip: String = "",
    val windDirDay: String = "", val windScaleDay: String = "",
)

data class WeatherWarning(
    val id: String = "", val title: String = "", val text: String = "", val typeName: String = "",
    val severityColor: String = "", val startTime: String = "", val endTime: String = "", val sender: String = "",
)

data class AirQuality(
    val aqi: String = "", val category: String = "", val primary: String = "",
    val pm2p5: String = "", val pm10: String = "", val no2: String = "", val so2: String = "",
    val co: String = "", val o3: String = "",
    val pm2p5Unit: String = "", val pm10Unit: String = "", val no2Unit: String = "",
    val so2Unit: String = "", val coUnit: String = "", val o3Unit: String = "",
)

data class WeatherSnapshot(
    val city: City,
    val now: CurrentWeather,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val warnings: List<WeatherWarning> = emptyList(),
    val air: AirQuality? = null,
    val fetchedAt: Long,
    val isDemo: Boolean,
    val sourceUrl: String = "https://www.qweather.com/",
    val partialFailures: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val licenses: List<String> = emptyList(),
    val isStale: Boolean = false,
    val serverCacheAgeSeconds: Long = 0,
)

object DefaultCities {
    val beijing = City("101010100", "北京", "北京市", "北京", "39.90499", "116.40528")
    val shanghai = City("101020100", "上海", "上海市", "上海", "31.23171", "121.47264")
    val hangzhou = City("101210101", "杭州", "浙江省", "杭州", "30.28745", "120.15358")
    val guangzhou = City("101280101", "广州", "广东省", "广州", "23.12518", "113.28064")
    val shenzhen = City("101280601", "深圳", "广东省", "深圳", "22.547", "114.08595")
    val chengdu = City("101270101", "成都", "四川省", "成都", "30.65946", "104.06574")
    private fun sampleCity(name: String, adm1: String, lat: String, lon: String) = City("$lon,$lat", name, adm1, name, lat, lon)
    val tianjin = sampleCity("天津", "天津市", "39.12", "117.20")
    val nanjing = sampleCity("南京", "江苏省", "32.06", "118.80")
    val fuzhou = sampleCity("福州", "福建省", "26.07", "119.30")
    val xiamen = sampleCity("厦门", "福建省", "24.48", "118.09")
    val haikou = sampleCity("海口", "海南省", "20.03", "110.33")
    val wuhan = sampleCity("武汉", "湖北省", "30.59", "114.30")
    val taiyuan = sampleCity("太原", "山西省", "37.87", "112.55")
    val shenyang = sampleCity("沈阳", "辽宁省", "41.81", "123.43")
    val harbin = sampleCity("哈尔滨", "黑龙江省", "45.80", "126.54")
    val changchun = sampleCity("长春", "吉林省", "43.82", "125.32")
    val xining = sampleCity("西宁", "青海省", "36.62", "101.78")
    val guiyang = sampleCity("贵阳", "贵州省", "26.65", "106.63")
    val chongqing = sampleCity("重庆", "重庆市", "29.56", "106.55")
    val shijiazhuang = sampleCity("石家庄", "河北省", "38.04", "114.51")
    val hohhot = sampleCity("呼和浩特", "内蒙古自治区", "40.84", "111.75")
    val lanzhou = sampleCity("兰州", "甘肃省", "36.06", "103.83")
    val urumqi = sampleCity("乌鲁木齐", "新疆维吾尔自治区", "43.82", "87.62")
    val suzhou = sampleCity("苏州", "江苏省", "31.30", "120.58")
    val demoCatalog = listOf(beijing, shanghai, hangzhou, guangzhou, shenzhen, chengdu, tianjin,
        nanjing, fuzhou, xiamen, haikou, wuhan, taiyuan, shenyang, harbin, changchun,
        xining, guiyang, chongqing, shijiazhuang, hohhot, lanzhou, urumqi, suzhou)
    val all = demoCatalog
    val initial = listOf(beijing, shanghai, hangzhou)

    fun search(query: String): List<City> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val english = listOf("beijing", "shanghai", "hangzhou", "guangzhou", "shenzhen", "chengdu", "tianjin",
            "nanjing", "fuzhou", "xiamen", "haikou", "wuhan", "taiyuan", "shenyang", "haerbin harbin", "changchun",
            "xining", "guiyang", "chongqing", "shijiazhuang", "huhehaote hohhot", "lanzhou", "wulumuqi urumqi", "suzhou")
        return all.filterIndexed { index, city ->
            needle.isEmpty() || city.name.contains(needle) || city.adm1.contains(needle) || english[index].contains(needle)
        }
    }
}
