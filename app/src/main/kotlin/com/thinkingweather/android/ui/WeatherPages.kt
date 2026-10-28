package com.thinkingweather.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinkingweather.android.WeatherUiState
import com.thinkingweather.android.BuildConfig
import com.thinkingweather.android.WeatherViewModel
import com.thinkingweather.android.data.DemoWeather
import com.thinkingweather.android.data.WeatherStore
import com.thinkingweather.android.model.*

@Composable
internal fun CitiesPage(state: WeatherUiState, vm: WeatherViewModel, onLocation: () -> Unit, onBack: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)) {
            PageHeader("我的城市", if (state.isDemo) "选择一座城市，预览不同的天气" else "把关心的地方，放在身边", onBack)
            OutlinedTextField(
                value = state.searchQuery, onValueChange = vm::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true,
                placeholder = { Text(if (state.isDemo) "搜索示例城市、拼音或天气" else "搜索城市、区县或拼音", fontSize = 14.sp) },
                leadingIcon = { LineIcon(Glyph.Search, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }, modifier = Modifier.semantics { contentDescription = "清空搜索" }) { LineIcon(Glyph.Close, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
            if (state.isDemo) {
                Text("天气示例 · 非实时数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).semantics { heading() })
                Text("每座城市对应固定场景，不占用你的真实城市收藏。", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))
                val query = state.searchQuery.trim()
                val scenarios = DemoWeather.catalog.filter { scenario ->
                    query.isBlank() || scenario.city.name.contains(query) || scenario.text.contains(query) ||
                        state.searchResults.any { it.location == scenario.city.location }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(scenarios, key = { it.city.location }) { scenario ->
                        DemoCityCard(scenario.city, state) {
                            vm.selectCity(scenario.city); vm.setSearchQuery(""); keyboard?.hide(); onBack()
                        }
                    }
                    if (scenarios.isEmpty()) item {
                        Text("没有匹配的示例，试试「晴」「雪」或城市拼音。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 22.dp))
                    }
                }
            } else {
                FilledTonalButton(onClick = { keyboard?.hide(); onBack(); onLocation() }, enabled = state.backendConfigured,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp)) {
                    LineIcon(Glyph.Location, Modifier.size(18.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(9.dp)); Text("使用当前位置")
                }
                if (state.searchQuery.isNotBlank()) {
                    Text("搜索结果", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp).semantics { heading() })
                    when {
                        state.isSearching -> Row(Modifier.fillMaxWidth().padding(vertical = 25.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("正在寻找城市…", fontSize = 13.sp)
                        }
                        state.searchErrorMessage != null -> {
                            Text(state.searchErrorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                            TextButton(onClick = { vm.setSearchQuery(state.searchQuery) }) { Text("重新搜索") }
                        }
                        state.searchResults.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没找到这座城市", fontSize = 17.sp)
                            Text("试试完整中文名或拼音", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
                        }
                        else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.searchResults, key = { it.location }) { city ->
                                val saved = state.cities.any { it.location == city.location }
                                val canAdd = saved || state.cities.size < WeatherStore.MAX_CITIES
                                CitySearchResult(city, saved, canAdd) { vm.addCity(city); vm.setSearchQuery(""); keyboard?.hide(); onBack() }
                            }
                        }
                    }
                    if (state.cities.size >= WeatherStore.MAX_CITIES) Text("已保存 ${WeatherStore.MAX_CITIES} 个城市，请先移除一座城市再添加。", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 25.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("已保存", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).semantics { heading() })
                        Text("${state.cities.size} / ${WeatherStore.MAX_CITIES}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 25.dp)) {
                        items(state.cities, key = { it.location }) { city ->
                            FavoriteCityCard(city, state, onSelect = { vm.selectCity(city); onBack() }, onDelete = { vm.removeCity(city) })
                        }
                        item { Text("天气会在选中城市后更新。至少保留一座城市。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        WeatherIconButton("返回", Glyph.Back, onBack, MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f).padding(start = 7.dp)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun DemoCityCard(city: City, state: WeatherUiState, onSelect: () -> Unit) {
    val scenario = DemoWeather.scenario(city)
    val selected = state.selectedCity.location == city.location
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(weatherBackgroundColors(scenario.category, scenario.night)))
            .clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(city.name, color = SkyWhite, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                if (selected) LineIcon(Glyph.Check, Modifier.padding(start = 8.dp).size(16.dp), SkyWhite)
            }
            Text("示例 · ${scenario.text}${if (scenario.night) " · 夜间" else ""}", color = SkyMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
        }
        WeatherSymbol(scenario.icon, scenario.text, Modifier.size(48.dp), night = scenario.night)
        Text(state.temperatureUnit.format(scenario.temperature.toString()), color = SkyWhite, fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun CitySearchResult(city: City, saved: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(city.name, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(city.description.ifBlank { "城市 / 地区" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        }
        Text(if (saved) "已保存" else if (enabled) "添加" else "已达上限", fontSize = 12.sp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun FavoriteCityCard(city: City, state: WeatherUiState, onSelect: () -> Unit, onDelete: () -> Unit) {
    val selected = state.selectedCity.location == city.location
    val snapshot = state.snapshot?.takeIf { it.city.location == city.location }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF3D627C), Color(0xFF476779))))
        .clickable(onClick = onSelect).padding(start = 20.dp, end = 7.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(city.name, color = SkyWhite, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selected) LineIcon(Glyph.Check, Modifier.padding(start = 7.dp).size(16.dp), SkyWhite)
            }
            Text(snapshot?.now?.text ?: city.description.ifBlank { if (selected) "正在获取天气" else "轻点查看天气" }, color = SkyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
        if (snapshot != null) Text(state.temperatureUnit.format(snapshot.now.temp), color = SkyWhite, fontSize = 37.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 9.dp))
        IconButton(onClick = onDelete, enabled = state.cities.size > 1, modifier = Modifier.semantics { contentDescription = "移除 ${city.name}" }) {
            LineIcon(Glyph.Close, Modifier.size(17.dp), SkyWhite.copy(alpha = if (state.cities.size > 1) .8f else .25f))
        }
    }
}

@Composable
internal fun SettingsPage(state: WeatherUiState, vm: WeatherViewModel, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            PageHeader("设置", "简单设置，舒适看天气", onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
                item { SettingsSection("温度单位") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UnitButton("摄氏度  °C", state.temperatureUnit == TemperatureUnit.CELSIUS, Modifier.weight(1f)) { vm.setTemperatureUnit(TemperatureUnit.CELSIUS) }
                        UnitButton("华氏度  °F", state.temperatureUnit == TemperatureUnit.FAHRENHEIT, Modifier.weight(1f)) { vm.setTemperatureUnit(TemperatureUnit.FAHRENHEIT) }
                    }
                } }
                item { SettingsSection("数据模式") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("示例预览", fontSize = 16.sp)
                            Text("固定城市与天气，预览全部场景", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                        }
                        Switch(checked = state.isDemo, onCheckedChange = vm::setDemoMode, enabled = state.backendConfigured || !state.isDemo,
                            modifier = Modifier.semantics { contentDescription = "示例预览模式" })
                    }
                    Text(if (state.isDemo) "当前所有天气数值均为示例，非实时观测，不可用于出行或安全决策。返回首页后打开「我的城市」，可浏览全部天气场景。" else "当前使用实时天气服务。天气有变化，关注最新的本地预警。",
                        fontSize = 12.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 13.dp))
                    if (!state.backendConfigured) Text("实时天气服务尚未配置，当前可完整浏览示例。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                } }
                item { SettingsSection("操作方式") {
                    Text("回到首页顶部，向下拖动，看到「松开刷新」后松手即可更新天气。", fontSize = 13.sp, lineHeight = 23.sp)
                    Text("城市与设置为独立页面，可点击左上角返回或使用系统返回手势。", fontSize = 12.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                } }
                item { SettingsSection("隐私与位置") {
                    Text("城市、温度单位与最近一次天气保存在此设备，方便下次打开和离线查看。", fontSize = 13.sp, lineHeight = 23.sp)
                    Text("仅在你点击「使用当前位置」后申请位置权限，获取一次位置以查询当地天气。位置将发送至天气服务完成查询，不会持续跟踪位置。拒绝权限后仍可手动搜索城市。", fontSize = 13.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
                    Text("你可以在系统设置中随时撤回位置权限，清除应用数据可删除本机保存的信息。", fontSize = 13.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
                } }
                item { SettingsSection("关于随想天气") {
                    Text("版本 ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                    Text("把天气放轻一点，把生活看近一点。", fontSize = 15.sp, lineHeight = 25.sp, fontWeight = FontWeight.Light)
                    Text("天气数据由和风天气提供。预报会受到地区覆盖、观测时间与服务可用性的影响，请以当地官方发布为准。示例模式使用内置样例。", fontSize = 12.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 13.dp))
                    TextButton(onClick = { runCatching { uri.openUri("https://www.qweather.com/") } }, contentPadding = PaddingValues(vertical = 10.dp, horizontal = 0.dp), modifier = Modifier.padding(top = 7.dp)) {
                        Text("了解和风天气", fontSize = 12.sp); Spacer(Modifier.width(7.dp)); LineIcon(Glyph.External, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                    }
                } }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 17.dp).semantics { heading() })
        content()
    }
}

@Composable
private fun UnitButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text(label, fontSize = 13.sp) }
    else OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text(label, fontSize = 13.sp) }
}
