package com.thinkingweather.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleStartEffect
import com.thinkingweather.android.WeatherUiState
import com.thinkingweather.android.WeatherViewModel
import com.thinkingweather.android.data.DemoWeather
import com.thinkingweather.android.data.WeatherStore
import com.thinkingweather.android.data.orderedSavedCities
import com.thinkingweather.android.model.*
import kotlinx.coroutines.delay

@Composable
internal fun CitiesPage(
    state: WeatherUiState, vm: WeatherViewModel, onLocation: () -> Unit,
    onBack: () -> Unit, onSearch: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var order by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val cities = if (editing) orderedSavedCities(state.cities, order) else state.cities
    val latestCities by rememberUpdatedState(cities)
    val finish: () -> Unit = {
        vm.reorderCities(cities.map(City::location))
        editing = false; selected = emptyList(); dragging = null; vm.clearCityEditMessage()
    }
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }
    fun move(key: String, target: Int) {
        val updated = latestCities.toMutableList()
        val source = updated.indexOfFirst { it.location == key }
        if (source >= 0 && target in updated.indices && source != target) {
            updated.add(target, updated.removeAt(source)); order = updated.map(City::location)
        }
    }
    val reorderAtCenter by rememberUpdatedState(newValue = {
        val key = dragging
        val currentIndex = latestCities.indexOfFirst { it.location == key }
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != key && dragCenter >= info.offset && dragCenter <= info.offset + info.size
        }
        if (key != null && currentIndex >= 0 && target != null) {
            val targetIndex = latestCities.indexOfFirst { it.location == target.key }
            move(key, targetIndex)
        }
    })
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        while (dragging != null) {
            val viewport = listState.layoutInfo
            val movement = when {
                dragCenter < viewport.viewportStartOffset + edge -> -((viewport.viewportStartOffset + edge - dragCenter) / edge).coerceIn(0f, 1f)
                dragCenter > viewport.viewportEndOffset - edge -> ((dragCenter - viewport.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (movement != 0f) listState.scrollBy(movement * with(density) { 12.dp.toPx() })
            reorderAtCenter()
            delay(16)
        }
    }
    LaunchedEffect(state.isDemo) { if (state.isDemo) { editing = false; selected = emptyList() } }
    LaunchedEffect(state.cities) { selected = selected.filter { key -> state.cities.any { it.location == key } } }
    BackHandler(editing, finish)
    LifecycleStartEffect(vm) { vm.onCitiesOpened(); onStopOrDispose { vm.onCitiesClosed() } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp)) {
            if (editing) Row(Modifier.fillMaxWidth().heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = if (selected.size == cities.size) emptyList() else cities.map(City::location) }) {
                    Text(if (selected.size == cities.size) "取消全选" else "全选")
                }
                Text("已选择 ${selected.size} 项", textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(onClick = finish) { Text("完成") }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PageHeader("我的城市", if (state.isDemo) "固定场景 · 不占用真实收藏" else "把关心的地方，放在身边", onBack) }
                TextButton(onClick = { order = state.cities.map(City::location); editing = true; vm.clearCityEditMessage() },
                    enabled = !state.isDemo, modifier = Modifier.testTag("edit-cities")) { Text("编辑") }
            }
            AnimatedVisibility(!editing, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    CitySearchEntry(onSearch)
                    if (!state.isDemo) FilledTonalButton(onClick = { onLocation(); onBack() }, enabled = state.backendConfigured,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
                        LineIcon(Glyph.Location, Modifier.size(18.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(9.dp)); Text("使用当前位置")
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp)) {
                        Text(if (state.isDemo) "天气示例 · 非实时数据" else "已保存", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(if (state.isDemo) "${cities.size} 个场景" else "${cities.size} / ${WeatherStore.MAX_CITIES}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (editing) Text("长按右侧手柄拖动排序，点击卡片选择城市", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("city-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 22.dp)) {
                items(cities, key = City::location) { city ->
                    val raised = dragging == city.location
                    val scale by animateFloatAsState(if (raised) 1.025f else 1f, spring(), label = "city-drag-scale")
                    CityManagementCard(city, state, editing, city.location in selected,
                        Modifier.animateItem(placementSpec = if (raised) null else spring(stiffness = 340f))
                            .zIndex(if (raised) 1f else 0f).graphicsLayer {
                                val top = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == city.location }
                                translationY = if (raised && top != null) dragCenter - top.offset - top.size / 2f else 0f
                                scaleX = scale; scaleY = scale
                                shadowElevation = if (raised) 18.dp.toPx() else 0f
                                shape = RoundedCornerShape(24.dp); clip = false
                            }.testTag("city-card-${city.location}"),
                        onSelect = { if (editing) toggle(city.location) else { vm.selectCity(city); onBack() } },
                        onDragStart = {
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == city.location }
                            if (item != null) { dragging = city.location; dragCenter = item.offset + item.size / 2f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                        },
                        onDrag = { delta -> dragCenter += delta; reorderAtCenter() }, onDragEnd = { dragging = null },
                        onMove = { delta -> move(city.location, cities.indexOf(city) + delta) },
                    )
                }
            }
            AnimatedVisibility(editing, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        state.cityEditMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                        Button(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("删除${if (selected.isEmpty()) "" else "（${selected.size}）"}") }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除所选城市？") },
        text = { Text(if (selected.size == cities.size) "需要至少保留一座城市，请取消选择其中一座后再删除。" else "将移除 ${selected.size} 座城市，之后可重新搜索添加。") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            if (selected.size < cities.size) { vm.removeCities(selected.toSet()); selected = emptyList() }
        }) {
            Text(if (selected.size == cities.size) "知道了" else "删除", color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

@Composable
private fun CitySearchEntry(onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onSearch)
        .heightIn(min = 58.dp).padding(horizontal = 18.dp).testTag("open-city-search")
        .semantics { contentDescription = "搜索城市" }, verticalAlignment = Alignment.CenterVertically) {
        LineIcon(Glyph.Search, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("搜索城市、区县或拼音", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun CityManagementCard(
    city: City, state: WeatherUiState, editing: Boolean, checked: Boolean, modifier: Modifier,
    onSelect: () -> Unit, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onMove: (Int) -> Unit,
) {
    val scenario = if (state.isDemo) DemoWeather.scenario(city) else null
    val snapshot = state.snapshot?.takeIf { it.city.location == city.location }
    val summary = state.citySummaries[city.location]
    val temperature = scenario?.temperature?.toString() ?: summary?.temp ?: snapshot?.now?.temp.orEmpty()
    val weather = scenario?.text ?: summary?.text ?: snapshot?.now?.text.orEmpty()
    val icon = scenario?.icon ?: summary?.icon ?: snapshot?.now?.icon.orEmpty()
    val night = scenario?.night ?: (icon in setOf("150", "151", "152", "153", "350", "351", "456", "457"))
    val loading = city.location in state.loadingCitySummaries
    val stale = summary?.isStale ?: (snapshot?.isStale == true)
    val current = state.selectedCity.location == city.location
    val colors = if (temperature.isBlank()) listOf(Color(0xFF3D627C), Color(0xFF476779)) else weatherBackgroundColors(weatherCategory(icon, weather), night)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(colors))
        .clickable(onClick = onSelect).semantics {
            selected = if (editing) checked else current
            stateDescription = if (editing) { if (checked) "已选择" else "未选择" } else if (current) "当前城市" else "已保存城市"
        }.padding(start = if (editing) 8.dp else 20.dp, end = if (editing) 4.dp else 20.dp, top = 19.dp, bottom = 19.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editing) Checkbox(checked = checked, onCheckedChange = { onSelect() },
            colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color(0xFF225878), uncheckedColor = Color.White.copy(alpha = .8f)),
            modifier = Modifier.semantics { contentDescription = "选择 ${city.name}" })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(city.name, color = SkyWhite, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (!state.isDemo && state.locatedCityLocation == city.location)
                    LineIcon(Glyph.Location, Modifier.padding(start = 6.dp).size(16.dp).semantics { contentDescription = "定位城市" }, SkyWhite)
                if (current && !editing) SelectedCityMark(Modifier.padding(start = 7.dp))
            }
            val description = when {
                scenario != null -> "示例 · $weather${if (night) " · 夜间" else ""}"
                weather.isNotBlank() -> (if (stale) "缓存 · " else "") + weather
                loading -> "正在获取天气"
                city.location in state.citySummaryErrors -> "暂时无法更新"
                else -> city.description.ifBlank { "等待更新天气" }
            }
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loading) { CircularProgressIndicator(Modifier.size(11.dp), color = SkyMuted, strokeWidth = 1.3.dp); Spacer(Modifier.width(6.dp)) }
                Text(description, color = SkyMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!editing && icon.isNotBlank()) WeatherSymbol(icon, weather, Modifier.padding(start = 6.dp).size(34.dp), night)
        Text(state.temperatureUnit.format(temperature), color = SkyWhite, fontSize = if (editing) 23.sp else 30.sp,
            fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.padding(start = 9.dp))
        if (editing) DragCityHandle(city, onDragStart, onDrag, onDragEnd, onMove)
    }
}

@Composable
private fun DragCityHandle(city: City, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit, onMove: (Int) -> Unit) {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Canvas(Modifier.size(48.dp).testTag("city-drag-${city.location}")
        .semantics { contentDescription = "拖动排序 ${city.name}"; customActions = listOf(
            CustomAccessibilityAction("向上移动") { onMove(-1); true }, CustomAccessibilityAction("向下移动") { onMove(1); true }) }
        .pointerInput(city.location) { detectDragGesturesAfterLongPress(
            onDragStart = { start() }, onDragEnd = { end() }, onDragCancel = { end() },
            onDrag = { change, amount -> change.consume(); drag(amount.y) },
        ) }) {
        val left = size.width * .3f; val right = size.width * .7f
        for (fraction in listOf(.4f, .5f, .6f)) drawLine(SkyWhite.copy(alpha = .9f), Offset(left, size.height * fraction),
            Offset(right, size.height * fraction), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    }
}
