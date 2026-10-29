package com.thinkingweather.android.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thinkingweather.android.WeatherUiState
import com.thinkingweather.android.WeatherViewModel
import com.thinkingweather.android.model.*
import com.thinkingweather.android.data.DemoWeather
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlinx.coroutines.delay

/** The complete weather experience. Every weather value is supplied by the repository. */
@Composable
fun WeatherApp(vm: WeatherViewModel, onRequestLocation: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
    val homeListState = rememberLazyListState()
    LaunchedEffect(state.selectedCity.location) { homeListState.scrollToItem(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val goBack: () -> Unit = { keyboard?.hide(); page = "home" }
    val clockMillis by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000) }
    }
    ThinkingWeatherTheme {
        PageSystemBars(useDarkIcons = page != "home" && !androidx.compose.foundation.isSystemInDarkTheme())
        BackHandler(enabled = page != "home", onBack = goBack)
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = targetState != "home"
                (slideInHorizontally(tween(300)) { if (forward) it else -it / 5 } +
                    fadeIn(tween(260)) + scaleIn(tween(300), initialScale = .985f)) togetherWith
                    (slideOutHorizontally(tween(300)) { if (forward) -it / 5 else it } + fadeOut(tween(220)))
            },
            label = "weather-pages",
        ) { destination ->
            when (destination) {
                "cities" -> CitiesPage(state, vm, onRequestLocation, onBack = goBack)
                "settings" -> SettingsPage(state, vm, onBack = goBack)
                else -> WeatherHome(state, vm, onRequestLocation, clockMillis, homeListState,
                    onCities = { page = "cities" }, onSettings = { page = "settings" })
            }
        }
    }
}

@Composable
private fun PageSystemBars(useDarkIcons: Boolean) {
    val view = LocalView.current
    val activity = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(view, activity, useDarkIcons) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = useDarkIcons
        controller?.isAppearanceLightNavigationBars = useDarkIcons
        onDispose {
            if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

@Composable
private fun WeatherHome(
    state: WeatherUiState, vm: WeatherViewModel, onRequestLocation: () -> Unit,
    clockMillis: Long, homeListState: LazyListState, onCities: () -> Unit, onSettings: () -> Unit,
) {
    val systemNight = androidx.compose.foundation.isSystemInDarkTheme()
    var previousVisual by remember { mutableStateOf(WeatherCategory.CLEAR to systemNight) }
    val night = state.snapshot?.let(::isNight) ?: previousVisual.second
    val category = state.snapshot?.now?.let { weatherCategory(it.icon, it.text) } ?: previousVisual.first
    SideEffect { if (state.snapshot != null) previousVisual = category to night }
    val background = animatedWeatherColors(category, night)
    val busy by rememberUpdatedState(state.isRefreshing || state.isLoading)
    val refresh by rememberUpdatedState(vm::refresh)
    val pullState = rememberPullDownRefreshState(busy)
    val connection = remember(pullState, homeListState) { pullState.connection(homeListState, { busy }, { refresh() }) }
    val refreshOffset = with(LocalDensity.current) { 52.dp.toPx() }
    val translation by animateFloatAsState(
        if (state.snapshot == null) 0f else if (pullState.released) refreshOffset else pullState.distance,
        tween(150), label = "pull-down-offset",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(background))) {
        WeatherAtmosphere(category, night)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            val fontScale = LocalDensity.current.fontScale
            LazyColumn(
                state = homeListState,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .testTag("weather-scroll").observePullDownTouch(pullState).nestedScroll(connection)
                    .graphicsLayer { translationY = translation }
                    .semantics { customActions = listOf(CustomAccessibilityAction("刷新天气") { if (!busy) refresh(); true }) },
                contentPadding = PaddingValues(horizontal = if (wide) 32.dp else 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { WeatherToolbar(state, Modifier.widthIn(max = 1040.dp), onCities, onSettings) }
                if (state.isDemo) item {
                    InfoStrip("示例预览 · 非实时天气", "当前展示内置样例，请勿用于出行决策。", "说明", onSettings, Modifier.widthIn(max = 1040.dp))
                }
                state.errorMessage?.let { message -> item {
                    InfoStrip(if (state.snapshot != null) "暂时无法更新" else "天气获取失败", message, "重试", vm::refresh, Modifier.widthIn(max = 1040.dp))
                } }
                state.lastLocationMessage?.let { message -> item {
                    InfoStrip("位置信息", message, "城市", onCities, Modifier.widthIn(max = 1040.dp))
                } }
                val snapshot = state.snapshot
                if (snapshot == null) {
                    item { EmptyWeather(state, onLocation = onRequestLocation, onCities = onCities, onRetry = vm::refresh, onDemo = { vm.setDemoMode(true) }) }
                } else {
                    if (snapshot.isStale && !snapshot.isDemo) item {
                        InfoStrip("正在显示已保存的天气", "网络或数据源暂时不可用，以下信息可能已过时。", "更新", vm::refresh, Modifier.widthIn(max = 1040.dp))
                    }
                    item { WeatherHero(snapshot, state.temperatureUnit, Modifier.widthIn(max = 1040.dp), wide) }
                    val cached = snapshot.isStale || state.errorMessage != null || clockMillis - snapshot.fetchedAt > 60 * 60 * 1000
                    val visibleWarnings = snapshot.warnings.filter { warning ->
                        val expiry = runCatching { OffsetDateTime.parse(warning.endTime).toInstant().toEpochMilli() }.getOrNull()
                        if (expiry != null) expiry > clockMillis else !cached
                    }
                    if (visibleWarnings.isNotEmpty()) item { WarningsCard(visibleWarnings, cached, Modifier.widthIn(max = 1040.dp)) }
                    item { HourlyCard(snapshot.hourly, state.temperatureUnit, Modifier.widthIn(max = 1040.dp)) }
                    item {
                        if (wide) Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                            DailyCard(snapshot.daily, state.temperatureUnit, Modifier.weight(1.12f), snapshot.city.tz)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                SunCard(snapshot, Modifier.fillMaxWidth())
                                AirCard(snapshot.air, Modifier.fillMaxWidth())
                            }
                        } else Column(Modifier.widthIn(max = 1040.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            DailyCard(snapshot.daily, state.temperatureUnit, timeZone = snapshot.city.tz)
                            SunCard(snapshot)
                        }
                    }
                    item { MetricGrid(snapshot, state.temperatureUnit, if (wide) { if (fontScale > 1.5f) 2 else 4 } else { if (fontScale > 1.5f) 1 else 2 }, Modifier.widthIn(max = 1040.dp)) }
                    if (!wide) item { AirCard(snapshot.air, Modifier.widthIn(max = 1040.dp)) }
                    if (snapshot.partialFailures.isNotEmpty()) item {
                        InfoStrip("数据提示", snapshot.partialFailures.map(::partialFailureText).distinct().joinToString("；"), "更新", vm::refresh, Modifier.widthIn(max = 1040.dp))
                    }
                    item { SourceFooter(snapshot, Modifier.widthIn(max = 1040.dp)) }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            RefreshHeader(
                pullState, initialLoading = state.isLoading && state.snapshot == null,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun WeatherToolbar(state: WeatherUiState, modifier: Modifier, onCities:()->Unit, onSettings:()->Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick=onCities).padding(vertical=6.dp).semantics{contentDescription="管理城市，当前 ${state.selectedCity.name}"}) {
            Text("随 想  /  WEATHER", color=SkyMuted, fontSize=10.sp, letterSpacing=2.sp, fontWeight=FontWeight.Medium)
            Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.padding(top=7.dp)) {
                Text(state.selectedCity.name, color=SkyWhite, fontSize=23.sp,fontWeight=FontWeight.Medium, maxLines=1, overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                Spacer(Modifier.width(5.dp))
                LineIcon(Glyph.Chevron,Modifier.size(15.dp),SkyMuted)
            }
        }
        WeatherIconButton("管理城市",Glyph.Cities,onCities)
        WeatherIconButton("设置",Glyph.Settings,onSettings)
    }
}

@Composable
internal fun WeatherIconButton(label: String, glyph: Glyph, onClick:()->Unit, color:Color=SkyWhite) {
    IconButton(onClick=onClick, modifier=Modifier.size(48.dp).semantics{contentDescription=label}) { LineIcon(glyph,color=color) }
}

@Composable
internal fun GlassCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF173E59).copy(alpha=.43f), Color(0xFF244D64).copy(alpha=.38f))))
        .border(.7.dp, Color.White.copy(alpha=.13f),RoundedCornerShape(28.dp)).padding(20.dp),content=content)
}

@Composable
internal fun PanelHeading(title:String, caption:String?=null) {
    Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=SkyWhite,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f).semantics{heading()})
        caption?.let{Text(it,color=SkyMuted,fontSize=11.sp)}
    }
}

@Composable
private fun WeatherHero(snapshot:WeatherSnapshot, unit:TemperatureUnit, modifier:Modifier, wide:Boolean) {
    val now=snapshot.now
    val today=snapshot.daily.firstOrNull()
    Column(modifier.fillMaxWidth().padding(top=if(wide) 16.dp else 5.dp,bottom=12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale=LocalDensity.current.fontScale
        val symbolSize=if(wide) 200.dp else if(fontScale>1.3f) 88.dp else 124.dp
        val number=unit.format(now.temp).removeSuffix("°")
        val available=(maxWidth-symbolSize).value
        val temperatureSize=minOf(if(wide)140f else 112f,available/fontScale/(number.length*.52f+.38f)).coerceAtLeast(38f)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(snapshot.isDemo) "样例天气" else observationTime(now.obsTime),color=SkyMuted,fontSize=12.sp,letterSpacing=.4.sp)
                Text(buildAnnotatedString{append(number);withStyle(SpanStyle(fontSize=(temperatureSize*.48f).sp,letterSpacing=0.sp)){append("°")}},
                    color=SkyWhite,fontSize=temperatureSize.sp,lineHeight=(temperatureSize*1.12f).sp,fontWeight=FontWeight.ExtraLight,letterSpacing=(-5).sp,maxLines=1,softWrap=false,
                    modifier=Modifier.padding(top=9.dp).clearAndSetSemantics{contentDescription="当前温度 ${unit.format(now.temp,true)}"})
            }
            WeatherSymbol(now.icon,now.text,Modifier.size(symbolSize),night=isNight(snapshot))
        }
        }
        Text(now.text.ifBlank{"天气状态未知"},color=SkyWhite,fontSize=25.sp,fontWeight=FontWeight.Light,letterSpacing=2.sp,modifier=Modifier.padding(top=4.dp))
        Spacer(Modifier.height(12.dp))
        Text(buildString{
            if(today!=null) append("最高 ${unit.format(today.tempMax)}  ·  最低 ${unit.format(today.tempMin)}    ")
            append("体感 ${unit.format(now.feelsLike)}")
        },color=SkyWhite.copy(alpha=.86f),fontSize=14.sp,lineHeight=24.sp)
        Spacer(Modifier.height(22.dp))
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            StatusPill(Glyph.Wind, "${now.windDir} ${now.windScale.ifBlank{"—"}}级")
            StatusPill(Glyph.Drop, "湿度 ${value(now.humidity,"%")}")
        }
    }
}

@Composable
private fun StatusPill(glyph:Glyph,label:String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha=.09f)).padding(horizontal=13.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        LineIcon(glyph,Modifier.size(15.dp),SkyMuted)
        Text(label,color=SkyWhite,fontSize=11.sp)
    }
}

@Composable
private fun HourlyCard(hours:List<HourlyForecast>,unit:TemperatureUnit,modifier:Modifier=Modifier) {
    GlassCard(modifier) {
        PanelHeading("逐小时预报",if(hours.isNotEmpty())"${hours.size} 小时" else null)
        if(hours.isEmpty()) Text("逐小时预报暂不可用",color=SkyMuted,fontSize=14.sp)
        else {
            val scroll=rememberScrollState()
            val itemWidth=66.dp
            Column(Modifier.horizontalScroll(scroll)) {
                Row {
                    hours.forEachIndexed { index,hour ->
                        Column(Modifier.width(itemWidth).clearAndSetSemantics{contentDescription="${hourLabel(hour.fxTime)}，${hour.text}，${unit.format(hour.temp,true)}，降水概率 ${hour.pop.ifBlank{"未知"}}${if(hour.pop.isNotBlank())"%" else ""}"},horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(hourLabel(hour.fxTime),color=if(index==0)SkyWhite else SkyMuted,fontSize=11.sp)
                            WeatherSymbol(hour.icon,hour.text,Modifier.padding(vertical=11.dp).size(31.dp),night=isNightIcon(hour.icon))
                            Text(unit.format(hour.temp),color=SkyWhite,fontSize=20.sp,fontWeight=FontWeight.Light)
                        }
                    }
                }
                TemperatureCurve(hours.map{it.temp.toFloatOrNull()?.takeIf{value->value.isFinite()}},Modifier.width(itemWidth*hours.size).height(70.dp).padding(horizontal=32.dp,vertical=13.dp))
                Row {
                    hours.forEach { hour -> Column(Modifier.width(itemWidth).clearAndSetSemantics{},horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(hour.text,color=SkyMuted,fontSize=10.sp,maxLines=1)
                        Text(if(hour.pop.isNotBlank())"${hour.pop}%" else "—",color=Color(0xFFC1E6F0),fontSize=11.sp,modifier=Modifier.padding(top=6.dp),maxLines=1)
                    } }
                }
            }
            Text("降水概率 · 左右滑动查看更多",color=SkyMuted.copy(alpha=.8f),fontSize=10.sp,modifier=Modifier.padding(top=17.dp))
        }
    }
}

@Composable
private fun TemperatureCurve(temperatures:List<Float?>,modifier:Modifier) {
    Canvas(modifier.clearAndSetSemantics{}) {
        val valid=temperatures.filterNotNull()
        if(valid.isEmpty()) return@Canvas
        val low=valid.minOrNull()?:0f;val high=valid.maxOrNull()?:0f
        val span=max(4f,high-low)
        val points=temperatures.mapIndexed{index,t->t?.let{Offset(if(temperatures.size==1) size.width/2 else size.width*index/(temperatures.size-1),size.height*(.78f-.56f*(it-low)/span))}}
        val p=Path().apply{
            var previous:Offset?=null
            points.forEach { next->
                val prev=previous
                if(next!=null) {if(prev==null)moveTo(next.x,next.y) else cubicTo((prev.x+next.x)/2,prev.y,(prev.x+next.x)/2,next.y,next.x,next.y)}
                previous=next
            }
        }
        drawPath(p,Brush.horizontalGradient(listOf(Color(0xFFB8E9E4),Color(0xFFF2D3A1))),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        points.filterNotNull().forEach{drawCircle(SkyWhite,2.5.dp.toPx(),it)}
    }
}

@Composable
private fun DailyCard(days:List<DailyForecast>,unit:TemperatureUnit,modifier:Modifier=Modifier,timeZone:String="Asia/Shanghai") {
    GlassCard(modifier) {
        PanelHeading("每日预报",if(days.isNotEmpty())"${days.size} 天" else null)
        if(days.isEmpty()) Text("每日预报暂不可用",color=SkyMuted,fontSize=14.sp)
        val globalMin=days.mapNotNull{it.tempMin.toFloatOrNull()?.takeIf{number->number.isFinite()}}.minOrNull()?:0f
        val globalMax=days.mapNotNull{it.tempMax.toFloatOrNull()?.takeIf{number->number.isFinite()}}.maxOrNull()?:globalMin+1
        days.forEachIndexed { index,day ->
            if(index>0) HorizontalDivider(color=Color.White.copy(alpha=.09f),modifier=Modifier.padding(vertical=9.dp))
            Row(Modifier.fillMaxWidth().heightIn(min=39.dp).semantics(mergeDescendants=true){},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Column(Modifier.width(42.dp)) {
                    Text(dayLabel(day.fxDate,timeZone),color=SkyWhite,fontSize=13.sp,fontWeight=if(index==0)FontWeight.Medium else FontWeight.Normal)
                    Text(shortDate(day.fxDate),color=SkyMuted,fontSize=10.sp)
                }
                WeatherSymbol(day.iconDay,day.textDay,Modifier.size(28.dp))
                Column(Modifier.width(37.dp)) {
                    Text(day.textDay,color=SkyMuted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(day.pop.isNotBlank()) Text("${day.pop}%",color=Color(0xFFC1E6F0),fontSize=10.sp)
                }
                Text(unit.format(day.tempMin),color=SkyMuted,fontSize=14.sp,modifier=Modifier.widthIn(min=28.dp),textAlign=TextAlign.End)
                if(day.tempMin.toFloatOrNull()!=null && day.tempMax.toFloatOrNull()!=null) RangeBar(day.tempMin.toFloat(),day.tempMax.toFloat(),globalMin,globalMax,Modifier.weight(1f).height(6.dp))
                else Spacer(Modifier.weight(1f))
                Text(unit.format(day.tempMax),color=SkyWhite,fontSize=14.sp,modifier=Modifier.widthIn(min=28.dp),textAlign=TextAlign.End)
            }
        }
    }
}

@Composable
private fun RangeBar(low:Float,high:Float,min:Float,max:Float,modifier:Modifier) {
    Canvas(modifier.clearAndSetSemantics{}) {
        if(!low.isFinite() || !high.isFinite() || !min.isFinite() || !max.isFinite()) return@Canvas
        val span=(max-min).coerceAtLeast(1f)
        val left=((low-min)/span).coerceIn(0f,1f)*size.width
        val right=((high-min)/span).coerceIn(0f,1f)*size.width
        drawLine(Color.White.copy(alpha=.1f),Offset(0f,size.height/2),Offset(size.width,size.height/2),size.height,StrokeCap.Round)
        drawLine(Brush.horizontalGradient(listOf(Color(0xFFA5D9DF),Color(0xFFE8C692))),Offset(left,size.height/2),Offset(maxOf(left+1,right),size.height/2),size.height,StrokeCap.Round)
    }
}

private data class Metric(val label:String,val value:String,val unit:String,val detail:String,val glyph:Glyph)

@Composable
private fun MetricGrid(snapshot:WeatherSnapshot,unit:TemperatureUnit,columns:Int,modifier:Modifier=Modifier) {
    val now=snapshot.now;val today=snapshot.daily.firstOrNull()
    val metrics=listOf(
        Metric("体感温度",unit.format(now.feelsLike).removeSuffix("°"),if(unit==TemperatureUnit.CELSIUS)"°C" else "°F","实际气温 ${unit.format(now.temp)}",Glyph.Sun),
        Metric("相对湿度",now.humidity,"%",when(now.humidity.toIntOrNull()){in 0..39->"空气偏干燥";in 40..70->"湿度适中";in 71..100->"空气较湿润";else->"暂无湿度说明"},Glyph.Drop),
        Metric("风速",now.windSpeed,"km/h","${now.windDir} · ${now.windScale.ifBlank{"—"}}级",Glyph.Wind),
        Metric("今日最高紫外线",today?.uvIndex.orEmpty(),"",uvLabel(today?.uvIndex),Glyph.Sun),
        Metric("能见度",now.vis,"km","当前水平能见距离",Glyph.Eye),
        Metric("气压",now.pressure,"hPa","当前地面气压",Glyph.Pressure),
        Metric("小时降水",now.precip,"mm","过去 1 小时降水量",Glyph.Drop),
        Metric("云量",now.cloud,"%","天空被云覆盖的比例",Glyph.Eye),
    )
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        metrics.chunked(columns).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            row.forEach { metric -> MetricCard(metric,Modifier.weight(1f)) }
        }}
    }
}

@Composable
private fun MetricCard(metric:Metric,modifier:Modifier) {
    GlassCard(modifier) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(metric.label,color=SkyMuted,fontSize=12.sp,modifier=Modifier.weight(1f))
            LineIcon(metric.glyph,Modifier.size(19.dp),SkyWhite.copy(alpha=.8f))
        }
        Spacer(Modifier.height(23.dp))
        Row(verticalAlignment=Alignment.Bottom) {
            Text(metric.value.ifBlank{"—"},color=SkyWhite,fontSize=29.sp,fontWeight=FontWeight.Light)
            if(metric.value.isNotBlank()) Text(metric.unit,color=SkyMuted,fontSize=11.sp,modifier=Modifier.padding(start=3.dp,bottom=5.dp))
        }
        Text(metric.detail,color=SkyMuted,fontSize=10.sp,lineHeight=17.sp,modifier=Modifier.padding(top=8.dp))
    }
}

@Composable
private fun SunCard(snapshot:WeatherSnapshot,modifier:Modifier=Modifier) {
    val today=snapshot.daily.firstOrNull()
    GlassCard(modifier) {
        PanelHeading("日出与日落",today?.moonPhase?.takeIf{it.isNotBlank()})
        Canvas(Modifier.fillMaxWidth().height(72.dp).clearAndSetSemantics{}) {
            val p=Path().apply{moveTo(size.width*.06f,size.height*.9f);cubicTo(size.width*.23f,-size.height*.15f,size.width*.77f,-size.height*.15f,size.width*.94f,size.height*.9f)}
            drawLine(Color.White.copy(alpha=.18f),Offset(0f,size.height*.9f),Offset(size.width,size.height*.9f),1.dp.toPx())
            drawPath(p,Color(0xFFE9D7AF).copy(alpha=.9f),style=Stroke(2.dp.toPx()))
            drawCircle(Color(0xFFF1DBA4),7.dp.toPx(),Offset(size.width*.5f,size.height*.115f))
        }
        Row(Modifier.fillMaxWidth().padding(top=13.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Column { Text("日出",color=SkyMuted,fontSize=11.sp);Text(today?.sunrise?.takeIf{it.isNotBlank()}?:"—",color=SkyWhite,fontSize=21.sp,fontWeight=FontWeight.Light,modifier=Modifier.padding(top=5.dp)) }
            Column(horizontalAlignment=Alignment.End) {Text("日落",color=SkyMuted,fontSize=11.sp);Text(today?.sunset?.takeIf{it.isNotBlank()}?:"—",color=SkyWhite,fontSize=21.sp,fontWeight=FontWeight.Light,modifier=Modifier.padding(top=5.dp))}
        }
        Text("时间均为当地时间 · 弧线为示意",color=SkyMuted.copy(alpha=.8f),fontSize=10.sp,modifier=Modifier.padding(top=16.dp))
    }
}

@Composable
private fun AirCard(air:AirQuality?,modifier:Modifier=Modifier) {
    GlassCard(modifier) {
        PanelHeading("空气质量",if(air!=null)"AQI" else null)
        if(air==null) {
            Text("暂未提供空气质量",color=SkyWhite,fontSize=18.sp,fontWeight=FontWeight.Light)
            Text("此数据依地区与服务权限提供，请以当地监测信息为准。",color=SkyMuted,fontSize=12.sp,lineHeight=21.sp,modifier=Modifier.padding(top=10.dp))
        } else {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(air.aqi.ifBlank{"—"},color=SkyWhite,fontSize=44.sp,fontWeight=FontWeight.ExtraLight)
                Column {Text(air.category.ifBlank{"暂无等级"},color=SkyAccent,fontSize=17.sp);if(air.primary.isNotBlank() && !air.primary.equals("NA",ignoreCase=true))Text("首要污染物 ${air.primary}",color=SkyMuted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))}
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                AirValue("PM₂.₅",air.pm2p5,air.pm2p5Unit,Modifier.weight(1f));AirValue("PM₁₀",air.pm10,air.pm10Unit,Modifier.weight(1f));AirValue("O₃",air.o3,air.o3Unit,Modifier.weight(1f))
            }
            Text("浓度单位由数据源提供 · AQI 以数据源口径为准",color=SkyMuted.copy(alpha=.8f),fontSize=10.sp,modifier=Modifier.padding(top=14.dp))
        }
    }
}

@Composable
private fun AirValue(label:String,value:String,unit:String,modifier:Modifier) {
    Column(modifier.semantics(mergeDescendants=true){}) {
        Text(label,color=SkyMuted,fontSize=10.sp)
        Text(value.ifBlank{"—"},color=SkyWhite,fontSize=20.sp,fontWeight=FontWeight.Light,modifier=Modifier.padding(top=6.dp))
        if(unit.isNotBlank() && value.isNotBlank()) Text(unit,color=SkyMuted,fontSize=10.sp,modifier=Modifier.padding(top=3.dp))
    }
}

@Composable
private fun WarningsCard(warnings:List<WeatherWarning>,cached:Boolean,modifier:Modifier=Modifier) {
    GlassCard(modifier) {
        PanelHeading("天气预警","${warnings.size} 条")
        Text(if(cached)"缓存预警，请核实最新官方信息" else "请核实当地官方最新发布",color=SkyMuted,fontSize=11.sp,lineHeight=19.sp,modifier=Modifier.padding(bottom=10.dp))
        warnings.forEachIndexed { index,warning ->
            var expanded by remember(warning.id){mutableStateOf(false)}
            if(index>0) HorizontalDivider(color=Color.White.copy(alpha=.12f),modifier=Modifier.padding(vertical=14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable{expanded=!expanded}.padding(vertical=6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(warningColor(warning.severityColor)))
                    Text(warning.title.ifBlank{warning.typeName},color=SkyWhite,fontSize=14.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f).padding(horizontal=10.dp))
                    Text(if(expanded)"收起" else "详情",color=SkyMuted,fontSize=11.sp)
                }
                if(expanded){Text(warning.text,color=SkyWhite.copy(alpha=.9f),fontSize=13.sp,lineHeight=23.sp,modifier=Modifier.padding(top=13.dp));Text(warning.sender,color=SkyMuted,fontSize=10.sp,modifier=Modifier.padding(top=10.dp));Text(warning.endTime.takeIf{it.isNotBlank()}?.let{"有效至 $it"}?:"有效期未提供，请核实官方信息",color=SkyMuted,fontSize=10.sp,lineHeight=18.sp,modifier=Modifier.padding(top=5.dp))}
            }
        }
    }
}

@Composable
private fun SourceFooter(snapshot:WeatherSnapshot,modifier:Modifier) {
    val uri=LocalUriHandler.current
    Column(modifier.fillMaxWidth().padding(top=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(if(snapshot.isDemo)"内置示例数据 · 仅供界面预览" else "天气数据 · 和风天气",color=SkyWhite.copy(alpha=.85f),fontSize=11.sp,modifier=Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled=!snapshot.isDemo){runCatching{uri.openUri(safeWeatherUrl(snapshot.sourceUrl))}}.padding(horizontal=14.dp,vertical=12.dp))
        if(!snapshot.isDemo) Text("更新于 ${fetchedTime(snapshot.fetchedAt,snapshot.city.tz)} · 天气信息仅供参考",color=SkyMuted,fontSize=10.sp,textAlign=TextAlign.Center)
        if(!snapshot.isDemo && snapshot.sources.isNotEmpty()) Text("来源：${snapshot.sources.joinToString("、")}",color=SkyMuted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=7.dp))
        if(!snapshot.isDemo && snapshot.licenses.isNotEmpty()) Text("许可：${snapshot.licenses.joinToString("、")}",color=SkyMuted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=4.dp))
        Text("随想天气，陪你感知每一天",color=SkyWhite.copy(alpha=.52f),fontSize=10.sp,letterSpacing=2.sp,modifier=Modifier.padding(top=16.dp))
    }
}

@Composable
private fun InfoStrip(title:String,message:String,action:String,onAction:()->Unit,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xFF122F48).copy(alpha=.27f)).border(.7.dp,Color.White.copy(alpha=.1f),RoundedCornerShape(19.dp)).padding(start=16.dp,end=6.dp,top=9.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(title,color=SkyWhite,fontSize=12.sp,fontWeight=FontWeight.Medium);Text(message,color=SkyMuted,fontSize=11.sp,lineHeight=18.sp,modifier=Modifier.padding(top=3.dp))}
        TextButton(onClick=onAction,colors=ButtonDefaults.textButtonColors(contentColor=SkyWhite)) {Text(action,fontSize=12.sp)}
    }
}

@Composable
private fun EmptyWeather(state:WeatherUiState,onLocation:()->Unit,onCities:()->Unit,onRetry:()->Unit,onDemo:()->Unit) {
    GlassCard(Modifier.widthIn(max=600.dp).padding(top=35.dp)) {
        WeatherSymbol("101","多云",Modifier.align(Alignment.CenterHorizontally).size(130.dp))
        Text(if(state.isLoading)"正在获取天气…" else "从一座城市开始",color=SkyWhite,fontSize=25.sp,fontWeight=FontWeight.Light,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=22.dp))
        Text(if(state.isLoading)"正在获取最新天气，请稍候。" else if(!state.backendConfigured)"实时天气服务尚未配置。你可以先浏览示例，或在配置完成后获取天气。" else "搜索想要关注的城市，或使用当前位置查看天气。",color=SkyMuted,fontSize=14.sp,lineHeight=24.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=15.dp,bottom=26.dp))
        if(!state.isLoading) {
            Button(onClick=onCities,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),colors=ButtonDefaults.buttonColors(containerColor=SkyWhite,contentColor=Color(0xFF31566F))) {Text("选择城市")}
            TextButton(onClick=onLocation,enabled=!state.isDemo && state.backendConfigured,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=SkyWhite)) {Text("使用当前位置")}
            TextButton(onClick=if(state.backendConfigured)onRetry else onDemo,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=SkyMuted)){Text(if(state.backendConfigured)"重新获取天气" else "浏览示例天气")}
        } else CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(26.dp),color=SkyWhite,strokeWidth=2.dp)
    }
}

internal fun hourLabel(value:String):String = runCatching{OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrElse{value.substringAfter("T",value).take(5).ifBlank{"—"}}
internal fun shortDate(value:String):String = runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("M/d"))}.getOrDefault("")
private fun dayLabel(value:String,timeZone:String):String = runCatching{
    val date=LocalDate.parse(value)
    val today=LocalDate.now(runCatching{ZoneId.of(timeZone)}.getOrDefault(ZoneId.of("Asia/Shanghai")))
    when(date){today->"今天";today.plusDays(1)->"明天";else->listOf("周一","周二","周三","周四","周五","周六","周日")[date.dayOfWeek.value-1]}
}.getOrDefault("预报")
private fun observationTime(value:String):String = if(value.isBlank())"实时天气" else "当地 ${hourLabel(value)} 观测"
private fun fetchedTime(time:Long,tz:String):String = runCatching{java.time.Instant.ofEpochMilli(time).atZone(ZoneId.of(tz)).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}.getOrDefault("最近一次同步")
private fun value(text:String,unit:String):String = if(text.isBlank())"—" else text+unit
private fun uvLabel(value:String?):String = when(value?.toFloatOrNull()?.takeIf{it.isFinite() && it>=0}){null->"暂无紫外线数据";in 0f..2.99f->"较弱";in 3f..5.99f->"中等 · 注意防晒";in 6f..7.99f->"较强 · 注意防晒";in 8f..10.99f->"很强 · 减少暴晒";else->"极强 · 避免暴晒"}
private fun isNightIcon(icon:String):Boolean = icon in setOf("150","151","152","153","350","351","456","457")
private fun isNight(snapshot:WeatherSnapshot):Boolean = if(snapshot.isDemo) DemoWeather.scenario(snapshot.city).night else isNightIcon(snapshot.now.icon) || runCatching{OffsetDateTime.parse(snapshot.now.obsTime).hour.let{it<6||it>=19}}.getOrDefault(false)
private fun warningColor(value:String):Color = when(value.lowercase()){ "red","红色"->Color(0xFFFFA6A1);"orange","橙色"->Color(0xFFFFC19C);"yellow","黄色"->Color(0xFFF4DF9B);else->Color(0xFFA6D9FA)}
internal fun safeWeatherUrl(value:String):String = runCatching{java.net.URI(value).let{if(it.scheme=="https" && !it.host.isNullOrBlank())value else "https://www.qweather.com"}}.getOrDefault("https://www.qweather.com")
private fun partialFailureText(code:String):String {
    val name=when(code.substringBeforeLast('_')){"now"->"实时天气";"hourly"->"逐小时预报";"daily"->"每日预报";"air"->"空气质量";"warnings"->"天气预警";else->null}
    return if(name==null) "部分天气信息暂不可用" else when(code.substringAfterLast('_')){"disabled"->"此服务暂未提供$name";"stale"->"${name}正在显示缓存信息";else->"${name}暂时无法更新"}
}

@Preview(name="随想天气 · 手机",showBackground=true,widthDp=393,heightDp=852)
@Preview(name="随想天气 · 大字体",showBackground=true,widthDp=393,heightDp=852,fontScale=1.6f)
@Composable
private fun WeatherPreview() {
    val sample=com.thinkingweather.android.data.DemoWeather.create(
        DefaultCities.beijing,java.time.Instant.parse("2026-10-28T03:00:00Z").toEpochMilli())
    ThinkingWeatherTheme {
        LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(weatherBackgroundColors(weatherCategory(sample.now.icon,sample.now.text)))),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item {WeatherToolbar(WeatherUiState(snapshot=sample,isDemo=true,isLoading=false),Modifier,{}, {})}
            item {InfoStrip("示例预览 · 非实时天气","当前展示内置样例，请勿用于出行决策。","说明",{})}
            item {WeatherHero(sample,TemperatureUnit.CELSIUS,Modifier,false)}
            item {HourlyCard(sample.hourly,TemperatureUnit.CELSIUS)}
            item {DailyCard(sample.daily,TemperatureUnit.CELSIUS)}
        }
    }
}
