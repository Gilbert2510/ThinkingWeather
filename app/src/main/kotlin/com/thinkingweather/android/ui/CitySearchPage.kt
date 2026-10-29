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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinkingweather.android.WeatherUiState
import com.thinkingweather.android.WeatherViewModel
import com.thinkingweather.android.data.DemoWeather
import com.thinkingweather.android.data.WeatherStore
import com.thinkingweather.android.model.City
import com.thinkingweather.android.model.DefaultCities

/** Popular cities are curated coordinates, not claimed live weather or remotely ranked data. */
private val domesticPopular = listOf(DefaultCities.beijing, DefaultCities.shanghai, DefaultCities.guangzhou,
    DefaultCities.shenzhen, DefaultCities.chengdu, DefaultCities.hangzhou, DefaultCities.chongqing,
    DefaultCities.nanjing, DefaultCities.xiamen, DefaultCities.wuhan, DefaultCities.haikou, DefaultCities.harbin)
private val internationalPopular = listOf(
    City("139.69,35.69", "东京", "日本", lat = "35.69", lon = "139.69", tz = "Asia/Tokyo"),
    City("126.98,37.57", "首尔", "韩国", lat = "37.57", lon = "126.98", tz = "Asia/Seoul"),
    City("103.85,1.29", "新加坡", "新加坡", lat = "1.29", lon = "103.85", tz = "Asia/Singapore"),
    City("-0.13,51.51", "伦敦", "英国", lat = "51.51", lon = "-0.13", tz = "Europe/London"),
    City("2.35,48.86", "巴黎", "法国", lat = "48.86", lon = "2.35", tz = "Europe/Paris"),
    City("-74.01,40.71", "纽约", "美国", lat = "40.71", lon = "-74.01", tz = "America/New_York"),
    City("-118.24,34.05", "洛杉矶", "美国", lat = "34.05", lon = "-118.24", tz = "America/Los_Angeles"),
    City("151.21,-33.87", "悉尼", "澳大利亚", lat = "-33.87", lon = "151.21", tz = "Australia/Sydney"),
    City("100.50,13.75", "曼谷", "泰国", lat = "13.75", lon = "100.50", tz = "Asia/Bangkok"),
)

@Composable
internal fun CitySearchPage(state: WeatherUiState, vm: WeatherViewModel, onBack: () -> Unit, onSelected: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var international by rememberSaveable { mutableStateOf(false) }
    fun choose(city: City) { vm.addCity(city); vm.setSearchQuery(""); keyboard?.hide(); onSelected() }
    val query = state.searchQuery.trim()
    val results = if (state.isDemo) DemoWeather.catalog.filter { scenario ->
        scenario.city.name.contains(query) || scenario.text.contains(query) || state.searchResults.any { it.location == scenario.city.location }
    }.map { it.city } else state.searchResults
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 22.dp)) {
            PageHeader("搜索城市", if (state.isDemo) "搜索城市或天气，探索全部示例" else "找到下一座关心的城市", onBack)
            OutlinedTextField(value = state.searchQuery, onValueChange = vm::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("city-search-input"), singleLine = true,
                placeholder = { Text(if (state.isDemo) "城市、拼音或天气" else "城市、区县或拼音", fontSize = 14.sp) },
                leadingIcon = { LineIcon(Glyph.Search, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") },
                    modifier = Modifier.semantics { contentDescription = "清空搜索" }) { LineIcon(Glyph.Close, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (query.isBlank()) {
                    item { Text("热门城市", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() }) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        FilterChip(selected = !international, onClick = { international = false }, label = { Text("国内") })
                        FilterChip(selected = international, onClick = { international = true }, label = { Text("国际") })
                    } }
                    val popular = if (international) internationalPopular else domesticPopular
                    items(popular.chunked(3)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { city ->
                                val saved = state.cities.any { it.location == city.location }
                                val enabled = (!state.isDemo || !international) && (state.isDemo || saved || state.cities.size < WeatherStore.MAX_CITIES)
                                PopularCityTile(city, international, enabled, saved && !state.isDemo, Modifier.weight(1f)) { choose(city) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item { Text(when {
                        state.isDemo && international -> "国际城市使用实时天气，请在设置中关闭示例预览后添加。"
                        state.isDemo -> "热门城市展示固定示例，不会修改真实收藏。"
                        state.cities.size >= WeatherStore.MAX_CITIES -> "已保存 ${WeatherStore.MAX_CITIES} 个城市，请先在编辑中删除一座再添加。"
                        else -> "也可以输入完整中文名、区县名或城市拼音。"
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp)) }
                } else {
                    item { Text("搜索结果", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.semantics { heading() }) }
                    when {
                        state.isSearching -> item { Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("正在寻找城市…", fontSize = 13.sp)
                        } }
                        state.searchErrorMessage != null || state.searchErrorIsOffline -> item {
                            Text(if (state.searchErrorIsOffline) "天气服务不可用，请稍后重试" else state.searchErrorMessage.orEmpty(),
                                color = if (state.searchErrorIsOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 14.dp))
                            TextButton(onClick = { vm.setSearchQuery(state.searchQuery) }) { Text("重新搜索") }
                        }
                        state.searchHint != null -> item { Text(state.searchHint, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 18.dp)) }
                        results.isEmpty() -> item { Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没找到这座城市", fontSize = 17.sp)
                            Text(if (state.isDemo) "试试「晴」「雪」或城市拼音" else "试试完整中文名或拼音", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                        } }
                    }
                    if (!state.isSearching) items(results, key = City::location) { city ->
                        val saved = state.cities.any { it.location == city.location }
                        val enabled = state.isDemo || saved || state.cities.size < WeatherStore.MAX_CITIES
                        CitySearchResult(city, !state.isDemo && saved, enabled, state.isDemo) { choose(city) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularCityTile(city: City, international: Boolean, enabled: Boolean, saved: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(
        if (international) listOf(Color(0xFF427C9D), Color(0xFF325A80)) else listOf(Color(0xFF5C96B4), Color(0xFF386B90))))
        .clickable(enabled = enabled, onClick = onClick).heightIn(min = 95.dp).padding(horizontal = 14.dp, vertical = 18.dp)) {
        Text(city.name, color = Color.White.copy(alpha = if (enabled) 1f else .5f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (saved) "已保存" else if (international) city.adm1 else city.adm1.removeSuffix("省").removeSuffix("市").removeSuffix("自治区"),
            color = Color.White.copy(alpha = if (enabled) .75f else .4f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun CitySearchResult(city: City, saved: Boolean, enabled: Boolean, demo: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(city.name, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(city.description.ifBlank { "城市 / 地区" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        }
        Text(if (demo) "预览" else if (saved) "已保存" else if (enabled) "添加" else "已达上限", fontSize = 12.sp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
}
