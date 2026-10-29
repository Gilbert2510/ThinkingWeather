package com.thinkingweather.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinkingweather.android.model.*
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class InsightPalette(val background: Color, val surface: Color, val text: Color, val muted: Color, val blue: Color)

@Composable
private fun insightPalette(): InsightPalette = if (isSystemInDarkTheme())
    InsightPalette(Color(0xFF102432), Color(0xFF193548), Color(0xFFE4EFF8), Color(0xFFA8C2D6), Color(0xFF9ACFFA))
else InsightPalette(Color(0xFFF0F6FC), Color(0xFFFCFEFF), Color(0xFF213B51), Color(0xFF627C92), Color(0xFF317CB7))

/** Each entry has one lazy-list item, so links from the home page land at the requested heading. */
@Composable
internal fun WeatherInsightsPage(snapshot: WeatherSnapshot, unit: TemperatureUnit, section: String, onBack: () -> Unit) {
    val palette = insightPalette()
    val sections = availableInsightSections(snapshot)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val target = insightTarget(section, sections)
    var selectedDay by rememberSaveable(snapshot.city.location) { mutableIntStateOf(0) }
    LaunchedEffect(snapshot.city.location, target) {
        listState.scrollToItem(sections.indexOfFirst { it.key == target }.coerceAtLeast(0))
    }
    val activeKey by remember(listState, sections) { derivedStateOf { sections.getOrNull(listState.firstVisibleItemIndex)?.key } }
    Surface(Modifier.fillMaxSize().testTag("weather-insights"), color = palette.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.padding(horizontal = 22.dp)) {
                val observation = insightTime(snapshot.now.obsTime, snapshot.city.tz)
                PageHeader("天气详情", snapshot.city.name + when {
                    snapshot.isDemo -> " · 示例数据"
                    observation != "—" -> " · 当地 $observation 观测"
                    else -> " · 天气概览"
                }, onBack)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEachIndexed { index, item ->
                    val intoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(activeKey) { if (activeKey == item.key) intoView.bringIntoView() }
                    FilterChip(selected = activeKey == item.key, onClick = { scope.launch { listState.animateScrollToItem(index) } },
                        label = { Text(item.title, fontSize = 12.sp) }, modifier = Modifier.testTag("insight-tab-${item.key}").bringIntoViewRequester(intoView),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.blue.copy(alpha = .15f), selectedLabelColor = palette.blue,
                            containerColor = palette.surface, labelColor = palette.muted), border = null)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("insights-scroll"),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sections.forEach { entry -> item(key = entry.key) {
                    Box(Modifier.widthIn(max = 900.dp).fillMaxWidth().testTag("insight-${entry.key}")) {
                        when (entry.key) {
                            "current" -> CurrentInsight(snapshot, unit, palette)
                            "hourly" -> HourlyInsight(snapshot, unit, palette)
                            "daily" -> DailyInsight(snapshot, unit, selectedDay, { selectedDay = it }, palette)
                            "indices" -> MeteorologyInsight(snapshot, unit, palette)
                            "air" -> snapshot.air?.let { AirInsight(it, palette) }
                            "uv" -> UltravioletInsight(snapshot, palette)
                            "sun" -> SolarInsight(snapshot, palette)
                            "life" -> LifeInsight(snapshot, palette)
                        }
                    }
                } }
                item { Text("数据由和风天气提供" + if (snapshot.isDemo) " · 当前为示例数据" else if (snapshot.isStale) " · 当前为缓存数据" else "",
                    color = palette.muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) }
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, caption: String? = null, palette: InsightPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(palette.surface)
        .border(.6.dp, palette.blue.copy(alpha = .1f), RoundedCornerShape(27.dp)).padding(20.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.semantics { heading() })
        caption?.takeIf(String::isNotBlank)?.let { Text(it, color = palette.muted, fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp)) }
        Spacer(Modifier.height(21.dp))
        content()
    }
}

@Composable
private fun CurrentInsight(snapshot: WeatherSnapshot, unit: TemperatureUnit, palette: InsightPalette) {
    val now = snapshot.now
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF367FB4), Color(0xFF28597D)))).padding(24.dp)) {
            Text("实况天气", color = SkyMuted, fontSize = 13.sp, modifier = Modifier.semantics { heading() })
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                val temperatureSize = minOf(48f, (maxWidth.value - 82f) / (unit.format(now.temp, true).length * .58f * fontScale)).coerceAtLeast(24f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(unit.format(now.temp, true), color = SkyWhite, fontSize = temperatureSize.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        if (now.text.isNotBlank()) Text(now.text, color = SkyWhite, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (now.text.isNotBlank()) WeatherSymbol(now.icon, now.text, Modifier.size(70.dp), night = now.icon in setOf("150", "151", "152", "153", "350", "351", "456", "457"))
                }
            }
            val observed = insightTime(now.obsTime, snapshot.city.tz, true)
            val acquired = insightTime(java.time.Instant.ofEpochMilli(snapshot.fetchedAt).toString(), snapshot.city.tz, true)
            Text(if (snapshot.isDemo) "示例预览，非实时天气" else
                (if (observed != "—") "$observed · 当地观测时间" else "获取于 $acquired · 当地时间") + if (snapshot.isStale) " · 缓存" else "",
                color = SkyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
        }
        val values = buildList {
            if (insightNumber(now.feelsLike) != null) add("体感温度" to unit.format(now.feelsLike, true))
            if (now.windDir.isNotBlank()) add("风向" to displayWindDirection(now.windDir))
            if (insightNumber(now.windSpeed, 0f) != null) add("风速" to "${now.windSpeed} km/h")
            if (now.windScale.isNotBlank()) add("风力" to "${now.windScale} 级")
            if (insightNumber(now.humidity, 0f, 100f) != null) add("相对湿度" to "${now.humidity}%")
            if (insightNumber(now.pressure, 0f) != null) add("气压" to "${now.pressure} hPa")
            if (insightNumber(now.vis, 0f) != null) add("能见度" to "${now.vis} km")
            if (insightNumber(now.precip, 0f) != null) add("过去一小时降水" to "${now.precip} mm")
        }
        if (values.isNotEmpty()) InsightCard("观测数据", "记录实况，不代表全天平均", palette) { InsightValueGrid(values, palette) }
    }
}

@Composable
private fun HourlyInsight(snapshot: WeatherSnapshot, unit: TemperatureUnit, palette: InsightPalette) {
    val hours = snapshot.hourly
    val labels = hours.map { insightTime(it.fxTime, snapshot.city.tz) }
    val temperatures = hours.map { insightTemperature(it.temp, unit) }
    val precip = hours.map { insightNumber(it.precip, 0f) }
    val probability = hours.map { insightNumber(it.pop, 0f, 100f) }
    val cardWidth = (94 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    var selectedHour by rememberSaveable(snapshot.city.location) { mutableIntStateOf(0) }
    val selected = selectedHour.coerceIn(0, hours.lastIndex)
    InsightCard("逐小时预报", "${hours.size} 个时点 · 采用 ${snapshot.city.tz} 当地时间", palette) {
        if (temperatures.any { it != null }) {
            DetailLabel("气温走势", palette)
            InsightLineChart("气温走势", temperatures, labels, unit.symbol, palette.blue, palette.muted, Modifier.padding(top = 12.dp))
        }
        if (precip.any { it != null }) {
            DetailLabel("小时降水量 · mm", palette, Modifier.padding(top = 26.dp, bottom = 6.dp))
            InsightBarChart("小时降水量", precip, labels, " mm", Color(0xFF58A7CE), palette.muted)
        } else if (probability.any { it != null }) {
            DetailLabel("降水概率 · %", palette, Modifier.padding(top = 26.dp, bottom = 6.dp))
            InsightBarChart("降水概率", probability, labels, "%", Color(0xFF58A7CE), palette.muted)
        }
        Text("图中断点表示该时点未提供数值。向左滑动下方数据卡可查看全部时点。", color = palette.muted, fontSize = 10.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 17.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            hours.forEachIndexed { index, hour ->
                Column(Modifier.width(cardWidth).clip(RoundedCornerShape(18.dp)).background(palette.blue.copy(alpha = if (index == selected) .13f else .045f))
                    .clickable { selectedHour = index }.semantics { this.selected = index == selected }.padding(horizontal = 11.dp, vertical = 13.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(insightTime(hour.fxTime, snapshot.city.tz), color = palette.muted, fontSize = 11.sp)
                    if (hour.text.isNotBlank()) WeatherSymbol(hour.icon, hour.text, Modifier.padding(vertical = 9.dp).size(31.dp), night = hour.icon in setOf("150", "151", "152", "153", "350", "351", "456", "457"))
                    if (insightNumber(hour.temp) != null) Text(unit.format(hour.temp), color = palette.text, fontSize = 19.sp, maxLines = 1)
                    if (hour.text.isNotBlank()) Text(hour.text, color = palette.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                    if (insightNumber(hour.pop, 0f, 100f) != null) Text("降水 ${hour.pop}%", color = palette.blue, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    if (insightNumber(hour.precip, 0f) != null) Text("${hour.precip} mm", color = palette.muted, fontSize = 10.sp)
                    if (insightNumber(hour.windSpeed, 0f) != null) Text("${hour.windSpeed} km/h", color = palette.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                    if (insightNumber(hour.humidity, 0f, 100f) != null) Text("湿度 ${hour.humidity}%", color = palette.muted, fontSize = 10.sp)
                }
            }
        }
        val hour = hours[selected]
        DetailLabel("${insightTime(hour.fxTime, snapshot.city.tz, true)} 时点详情", palette, Modifier.padding(top = 23.dp, bottom = 15.dp))
        InsightValueGrid(buildList {
            if (insightNumber(hour.temp) != null) add("气温" to unit.format(hour.temp, true))
            if (insightNumber(hour.humidity, 0f, 100f) != null) add("相对湿度" to "${hour.humidity}%")
            if (hour.windDir.isNotBlank()) add("风向" to displayWindDirection(hour.windDir))
            if (hour.windScale.isNotBlank()) add("风力" to "${hour.windScale} 级")
            if (insightNumber(hour.windSpeed, 0f) != null) add("风速" to "${hour.windSpeed} km/h")
            if (insightNumber(hour.pressure, 0f) != null) add("气压" to "${hour.pressure} hPa")
            if (insightNumber(hour.vis, 0f) != null) add("能见度" to "${hour.vis} km")
            if (insightNumber(hour.cloud, 0f, 100f) != null) add("云量" to "${hour.cloud}%")
            if (insightNumber(hour.dew) != null) add("露点温度" to unit.format(hour.dew, true))
            if (insightNumber(hour.uvIndex, 0f) != null) add("紫外线指数" to hour.uvIndex)
        }, palette)
    }
}

@Composable
private fun DailyInsight(snapshot: WeatherSnapshot, unit: TemperatureUnit, index: Int, onDay: (Int) -> Unit, palette: InsightPalette) {
    val days = snapshot.daily
    val selected = index.coerceIn(0, days.lastIndex)
    val day = days[selected]
    InsightCard("单日天气", "点击日期查看白昼、夜间与当天指标", palette) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            days.forEachIndexed { i, entry -> FilterChip(selected = selected == i, onClick = { onDay(i) }, label = { Text(insightDate(entry.fxDate), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.blue.copy(alpha = .15f), selectedLabelColor = palette.blue),
                modifier = Modifier.testTag("insight-day-$i")) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (day.textDay.isNotBlank() || insightNumber(day.tempMax) != null) DayPhase("白昼", day.iconDay, day.textDay, day.tempMax, unit, palette, Modifier.weight(1f))
            if (day.textNight.isNotBlank() || insightNumber(day.tempMin) != null) DayPhase("夜间", day.iconNight, day.textNight, day.tempMin, unit, palette, Modifier.weight(1f))
        }
        val values = buildList {
            if (insightNumber(day.pop, 0f, 100f) != null) add("降水概率" to "${day.pop}%")
            if (insightNumber(day.precip, 0f) != null) add("预报降水量" to "${day.precip} mm")
            if (insightNumber(day.humidity, 0f, 100f) != null) add("相对湿度" to "${day.humidity}%")
            if (insightNumber(day.uvIndex, 0f) != null) add("最高紫外线指数" to day.uvIndex)
            if (day.windDirDay.isNotBlank()) add("白天风向" to displayWindDirection(day.windDirDay))
            if (day.windScaleDay.isNotBlank()) add("白天风力" to "${day.windScaleDay} 级")
            if (insightNumber(day.windSpeedDay, 0f) != null) add("白天风速" to "${day.windSpeedDay} km/h")
            if (day.windDirNight.isNotBlank()) add("夜间风向" to displayWindDirection(day.windDirNight))
            if (day.windScaleNight.isNotBlank()) add("夜间风力" to "${day.windScaleNight} 级")
            if (insightNumber(day.windSpeedNight, 0f) != null) add("夜间风速" to "${day.windSpeedNight} km/h")
            if (day.sunrise.isNotBlank()) add("日出" to day.sunrise)
            if (day.sunset.isNotBlank()) add("日落" to day.sunset)
        }
        InsightValueGrid(values, palette)
        if (days.any { insightNumber(it.tempMin) != null || insightNumber(it.tempMax) != null }) {
            HorizontalDivider(color = palette.blue.copy(alpha = .1f), modifier = Modifier.padding(vertical = 21.dp))
            DetailLabel("未来每日气温", palette, Modifier.padding(bottom = 14.dp))
            DailyTemperatureRanges(days, unit, palette)
        }
    }
}

@Composable
private fun DayPhase(label: String, icon: String, weather: String, temp: String, unit: TemperatureUnit, palette: InsightPalette, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(palette.blue.copy(alpha = .055f)).padding(14.dp)) {
        Text(label, color = palette.muted, fontSize = 11.sp)
        if (weather.isNotBlank()) WeatherSymbol(icon, weather, Modifier.padding(vertical = 9.dp).size(38.dp), night = label == "夜间")
        if (insightNumber(temp) != null) Text(unit.format(temp), color = palette.text, fontSize = 23.sp)
        if (weather.isNotBlank()) Text(weather, color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DailyTemperatureRanges(days: List<DailyForecast>, unit: TemperatureUnit, palette: InsightPalette) {
    val domain = insightDomain(days.flatMap { listOf(insightTemperature(it.tempMin, unit), insightTemperature(it.tempMax, unit)) }) ?: return
    days.forEach { day ->
        val low = insightTemperature(day.tempMin, unit)
        val high = insightTemperature(day.tempMax, unit)
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(shortDate(day.fxDate), fontSize = 11.sp, color = palette.muted, modifier = Modifier.width(36.dp))
            Text(if (low != null) unit.format(day.tempMin) else "—", fontSize = 12.sp, color = palette.muted)
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(7.dp)) {
                val y = size.height / 2f
                drawLine(palette.blue.copy(alpha = .09f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), size.height, androidx.compose.ui.graphics.StrokeCap.Round)
                if (low != null && high != null && high >= low) {
                    val left = size.width * domain.fraction(low)
                    val right = size.width * domain.fraction(high)
                    drawLine(Brush.horizontalGradient(listOf(Color(0xFF7CC7DF), palette.blue)), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(maxOf(left + 1, right), y), size.height, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            Text(if (high != null) unit.format(day.tempMax) else "—", fontSize = 12.sp, color = palette.text)
        }
    }
}

@Composable
private fun MeteorologyInsight(snapshot: WeatherSnapshot, unit: TemperatureUnit, palette: InsightPalette) {
    val now = snapshot.now
    InsightCard("天气指数", "来自实况的湿度、云量与其他气象指标", palette) {
        val humidity = insightNumber(now.humidity, 0f, 100f)
        val cloud = insightNumber(now.cloud, 0f, 100f)
        if (humidity != null || cloud != null) Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            humidity?.let { InsightPercentageRing("相对湿度", it, palette.blue, palette.muted) }
            cloud?.let { InsightPercentageRing("云量", it, Color(0xFF5FA6AD), palette.muted) }
        }
        InsightValueGrid(buildList {
            if (insightNumber(now.dew) != null) add("露点温度" to unit.format(now.dew, true))
            if (insightNumber(now.pressure, 0f) != null) add("气压" to "${now.pressure} hPa")
            if (insightNumber(now.vis, 0f) != null) add("能见度" to "${now.vis} km")
            if (insightNumber(now.precip, 0f) != null) add("过去一小时降水" to "${now.precip} mm")
            if (now.windDir.isNotBlank()) add("风向" to displayWindDirection(now.windDir))
            if (insightNumber(now.windSpeed, 0f) != null) add("风速" to "${now.windSpeed} km/h")
            if (now.windScale.isNotBlank()) add("风力" to "${now.windScale} 级")
            if (insightNumber(now.wind360, 0f, 360f) != null) add("风向角度" to "${now.wind360}°")
        }, palette)
    }
}

@Composable
private fun AirInsight(air: AirQuality, palette: InsightPalette) {
    InsightCard("空气质量", listOf(air.indexName, air.indexCode).filter(String::isNotBlank).distinct().joinToString(" · ").ifBlank { "AQI 等级与浓度单位沿用数据源口径" }, palette) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            if (insightNumber(air.aqi, 0f) != null) Text(air.aqi, color = palette.blue, fontSize = 48.sp, fontWeight = FontWeight.Light)
            Column {
                Text(air.category.ifBlank { "空气质量指数" }, fontSize = 19.sp, color = palette.text)
                air.primary.takeIf { it.isNotBlank() && !it.equals("NA", true) }?.let {
                    Text("首要污染物 $it", color = palette.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }
        val pollutants = listOf(Triple("PM₂.₅", air.pm2p5, air.pm2p5Unit), Triple("PM₁₀", air.pm10, air.pm10Unit),
            Triple("O₃", air.o3, air.o3Unit), Triple("NO₂", air.no2, air.no2Unit), Triple("SO₂", air.so2, air.so2Unit), Triple("CO", air.co, air.coUnit))
            .filter { insightNumber(it.second, 0f) != null }
        if (pollutants.isNotEmpty()) {
            HorizontalDivider(color = palette.blue.copy(alpha = .1f), modifier = Modifier.padding(vertical = 21.dp))
            InsightValueGrid(pollutants.map { it.first to "${it.second}${if (it.third.isNotBlank()) " ${it.third}" else "（单位未提供）"}" }, palette)
            Text("各污染物单位可能不同，独立列示浓度。AQI 以数据源采用的标准为准。", color = palette.muted, fontSize = 10.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 15.dp))
        }
        listOf("健康影响" to air.healthEffect, "一般人群" to air.generalAdvice, "敏感人群" to air.sensitiveAdvice).filter { it.second.isNotBlank() }.forEach { (label, text) ->
            DetailLabel(label, palette, Modifier.padding(top = 20.dp, bottom = 7.dp))
            Text(text, color = palette.muted, fontSize = 13.sp, lineHeight = 23.sp)
        }
    }
}

@Composable
private fun UltravioletInsight(snapshot: WeatherSnapshot, palette: InsightPalette) {
    val daily = snapshot.daily.filter { insightNumber(it.uvIndex, 0f) != null }
    InsightCard("紫外线", "指数值来自天气服务，实况与每日最高值分别展示", palette) {
        if (insightNumber(snapshot.now.uvIndex, 0f) != null) {
            DetailLabel("实况紫外线指数", palette)
            Text(snapshot.now.uvIndex, color = palette.blue, fontSize = 40.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        }
        if (daily.isNotEmpty()) {
            DetailLabel("每日最高紫外线指数", palette, Modifier.padding(bottom = 14.dp))
            InsightLineChart("每日最高紫外线指数", snapshot.daily.map { insightNumber(it.uvIndex, 0f) }, snapshot.daily.map { shortDate(it.fxDate) }, "", palette.blue, palette.muted)
            daily.forEach { day -> DataLine(insightDate(day.fxDate), day.uvIndex, palette, Modifier.padding(top = 12.dp)) }
        }
        snapshot.indices.filter { it.type == "5" && it.text.isNotBlank() }.forEach {
            Text(it.text, color = palette.muted, fontSize = 13.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun SolarInsight(snapshot: WeatherSnapshot, palette: InsightPalette) {
    val day = snapshot.daily.firstOrNull { it.sunrise.isNotBlank() || it.sunset.isNotBlank() || it.moonPhase.isNotBlank() } ?: return
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(60_000) } }
    val time = if (snapshot.isDemo) runCatching { OffsetDateTime.parse(snapshot.now.obsTime).toInstant().toEpochMilli() }.getOrDefault(now) else now
    val solar = solarWindow(day.sunrise, day.sunset, day.fxDate, snapshot.city.tz, time)
    InsightCard("日出日落与月相", "${insightDate(day.fxDate)} · ${snapshot.city.tz} 当地时间", palette) {
        if (day.sunrise.isNotBlank() || day.sunset.isNotBlank()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2E658A), Color(0xFF21465F)))).padding(horizontal = 15.dp, vertical = 14.dp)) {
            SunPath(solar.progress)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (day.sunrise.isNotBlank()) Text("日出 ${day.sunrise}", color = SkyWhite, fontSize = 13.sp)
                if (day.sunset.isNotBlank()) Text("日落 ${day.sunset}", color = SkyWhite, fontSize = 13.sp)
            }
        }
        if (day.moonPhase.isNotBlank() || day.moonrise.isNotBlank() || day.moonset.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            InsightValueGrid(buildList {
                if (day.moonPhase.isNotBlank()) add("月相" to day.moonPhase)
                if (day.moonrise.isNotBlank()) add("月出" to day.moonrise)
                if (day.moonset.isNotBlank()) add("月落" to day.moonset)
            }, palette)
        }
        Text("太阳轨迹为示意，太阳仅在已知白昼时段显示。", color = palette.muted, fontSize = 10.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun LifeInsight(snapshot: WeatherSnapshot, palette: InsightPalette) {
    InsightCard("生活指数", "由和风天气提供的当日生活建议", palette) {
        snapshot.indices.filter { it.name.isNotBlank() && (it.category.isNotBlank() || it.text.isNotBlank()) }.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = palette.blue.copy(alpha = .1f), modifier = Modifier.padding(vertical = 20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (entry.category.isNotBlank()) Text(entry.category, color = palette.blue, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(palette.blue.copy(alpha = .085f)).padding(horizontal = 11.dp, vertical = 6.dp))
            }
            if (entry.date.isNotBlank()) Text(insightDate(entry.date), color = palette.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            if (entry.text.isNotBlank()) Text(entry.text, color = palette.muted, fontSize = 13.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun InsightValueGrid(values: List<Pair<String, String>>, palette: InsightPalette) {
    val columns = if (LocalDensity.current.fontScale > 1.4f) 1 else 2
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        values.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (label, text) -> Column(Modifier.weight(1f)) {
                    Text(label, color = palette.muted, fontSize = 11.sp)
                    Text(text, color = palette.text, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 5.dp))
                } }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailLabel(text: String, palette: InsightPalette, modifier: Modifier = Modifier) {
    Text(text, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

@Composable
private fun DataLine(label: String, text: String, palette: InsightPalette, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.muted, fontSize = 12.sp)
        Text(text, color = palette.text, fontSize = 14.sp)
    }
}
