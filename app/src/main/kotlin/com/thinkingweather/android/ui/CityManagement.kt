package com.thinkingweather.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.thinkingweather.android.data.orderedSavedCities
import com.thinkingweather.android.model.*
import kotlinx.coroutines.delay

@Composable
internal fun CitiesPage(
    state: WeatherUiState, vm: WeatherViewModel, onBack: () -> Unit, onSearch: () -> Unit,
    onOpenCity: (City) -> Unit, sharedScope: SharedTransitionScope, visibilityScope: AnimatedVisibilityScope,
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
    fun beginEditing(city: City? = null) {
        order = state.cities.map(City::location)
        selected = city?.let { listOf(it.location) }.orEmpty()
        editing = true
        vm.clearCityEditMessage()
    }
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
    LaunchedEffect(state.cities) { selected = selected.filter { key -> state.cities.any { it.location == key } } }
    BackHandler(editing, finish)
    LifecycleStartEffect(vm) { vm.onCitiesOpened(); onStopOrDispose { vm.onCitiesClosed() } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp)) {
            // Fixed geometry: changing editing state replaces labels without moving the list.
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(76.dp)) {
                    if (editing) TextButton(onClick = { selected = if (selected.size == cities.size) emptyList() else cities.map(City::location) }) {
                        Text(if (selected.size == cities.size) "取消全选" else "全选", fontSize = 14.sp)
                    } else WeatherIconButton("返回", Glyph.Back, onBack, MaterialTheme.colorScheme.onSurface)
                }
                Text(if (!editing) "我的城市" else if (selected.isEmpty()) "选择项目" else "已选择${selected.size}项",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 21.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).semantics { heading() })
                Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                    if (editing) TextButton(onClick = finish) { Text("完成") }
                    else TextButton(onClick = { beginEditing() }, modifier = Modifier.testTag("edit-cities")) { Text("编辑") }
                }
            }
            CitySearchEntry(onSearch, enabled = !editing)
            Spacer(Modifier.height(16.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("city-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 22.dp)) {
                items(cities, key = City::location) { city ->
                    val raised = dragging == city.location
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (raised) 1.065f else if (pressed) .965f else 1f,
                        spring(dampingRatio = if (raised || pressed) .82f else .48f, stiffness = if (pressed) 420f else 340f), label = "city-touch-scale")
                    val cardBounds = with(sharedScope) {
                        Modifier.sharedBounds(rememberSharedContentState("city-${city.location}"), visibilityScope,
                            boundsTransform = { _, _ -> androidx.compose.animation.core.tween(430) },
                            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
                            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)),
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(alignment = Alignment.TopStart),
                            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(26.dp)))
                    }
                    CityManagementCard(city, state, editing, city.location in selected,
                        Modifier.animateItem(placementSpec = if (raised) null else spring(stiffness = 340f))
                            .zIndex(if (raised) 1f else 0f).graphicsLayer {
                                val top = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == city.location }
                                translationY = if (raised && top != null) dragCenter - top.offset - top.size / 2f else 0f
                                scaleX = scale; scaleY = scale
                                shadowElevation = if (raised) 18.dp.toPx() else 0f
                                shape = RoundedCornerShape(24.dp); clip = false
                            }.then(cardBounds).testTag("city-card-${city.location}"), interaction,
                        onSelect = { if (editing) toggle(city.location) else onOpenCity(city) },
                        onLongPress = { if (!editing) { beginEditing(city); haptics.performHapticFeedback(HapticFeedbackType.LongPress) } },
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
            if (editing) {
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
private fun CitySearchEntry(onSearch: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(enabled = enabled, onClick = onSearch)
        .heightIn(min = 48.dp).padding(horizontal = 16.dp).testTag("open-city-search")
        .semantics { contentDescription = "搜索城市" }, verticalAlignment = Alignment.CenterVertically) {
        LineIcon(Glyph.Search, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("搜索城市、区县或拼音", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun CityManagementCard(
    city: City, state: WeatherUiState, editing: Boolean, checked: Boolean, modifier: Modifier,
    interaction: MutableInteractionSource, onSelect: () -> Unit, onLongPress: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onMove: (Int) -> Unit,
) {
    val snapshot = state.snapshot?.takeIf { it.city.location == city.location }
    val summary = state.citySummaries[city.location]
    val temperature = summary?.temp ?: snapshot?.now?.temp.orEmpty()
    val weather = summary?.text ?: snapshot?.now?.text.orEmpty()
    val icon = summary?.icon ?: snapshot?.now?.icon.orEmpty()
    val night = icon in setOf("150", "151", "152", "153", "350", "351", "456", "457")
    val loading = city.location in state.loadingCitySummaries
    val stale = summary?.isStale ?: (snapshot?.isStale == true)
    val current = state.selectedCity.location == city.location
    val colors = if (temperature.isBlank()) listOf(Color(0xFF3D627C), Color(0xFF476779)) else weatherBackgroundColors(weatherCategory(icon, weather), night)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(colors))
        .combinedClickable(interactionSource = interaction, indication = null,
            onClick = onSelect, onLongClick = onLongPress, onLongClickLabel = "选择并编辑城市", hapticFeedbackEnabled = false).semantics {
            selected = if (editing) checked else current
            stateDescription = if (editing) { if (checked) "已选择" else "未选择" } else if (current) "当前城市" else "已保存城市"
        }.heightIn(min = 112.dp).padding(start = if (editing) 8.dp else 20.dp, end = if (editing) 4.dp else 20.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editing) Checkbox(checked = checked, onCheckedChange = { onSelect() },
            colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color(0xFF225878), uncheckedColor = Color.White.copy(alpha = .8f)),
            modifier = Modifier.semantics { contentDescription = "选择 ${city.name}" })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(city.name, color = SkyWhite, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (state.locatedCityLocation == city.location)
                    LineIcon(Glyph.Location, Modifier.padding(start = 6.dp).size(16.dp).semantics { contentDescription = "定位城市" }, SkyWhite)
                if (current && !editing) SelectedCityMark(Modifier.padding(start = 7.dp))
            }
            val description = when {
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
