package com.thinkingweather.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinkingweather.android.BuildConfig
import com.thinkingweather.android.R
import com.thinkingweather.android.WeatherUiState
import java.net.URI

@Composable
internal fun SettingsNavigationItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        }
        LineIcon(Glyph.Chevron, Modifier.padding(start = 12.dp).size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A stronger, rounded tick without increasing the visual weight of other toolbar icons. */
@Composable
internal fun SelectedCityMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val tick = Path().apply {
            moveTo(size.width * .16f, size.height * .52f)
            lineTo(size.width * .40f, size.height * .76f)
            lineTo(size.width * .86f, size.height * .24f)
        }
        drawPath(tick, SkyWhite, style = Stroke(size.width * .14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun PrivacyPage(onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp)) {
            PageHeader("隐私", "了解你的数据如何使用", onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { SettingsSection("位置权限") {
                    DetailText("仅在你点击「使用当前位置」并授权后，获取一次大致位置，用于查询当地天气。位置会发送至天气服务完成查询，不会在后台持续跟踪。")
                    DetailText("拒绝或关闭定位权限后，仍可手动搜索城市。你可以在系统的应用权限设置中随时撤回授权。", Modifier.padding(top = 12.dp))
                } }
                item { SettingsSection("本地数据") {
                    DetailText("收藏城市、温度单位、示例预览偏好和最近的天气缓存在此设备保存，方便下次打开与离线查看。")
                    DetailText("可在「我的城市」中移除收藏；清除应用数据或卸载应用可删除全部本地内容。系统云备份已关闭。", Modifier.padding(top = 12.dp))
                } }
                item { SettingsSection("网络与天气服务") {
                    DetailText("真实天气查询会将城市、搜索文本或大致坐标发送到天气服务，服务提供方也会收到网络连接信息。示例预览使用本地数据，不发送天气查询或位置。")
                    DetailText("前台读取网络状态仅用于识别可用网络及移动流量提示，不读取网络名称、SIM 身份或流量账单，也不上传这些网络状态。离开前台即停止监听。", Modifier.padding(top = 12.dp))
                } }
                item { SettingsSection("信息使用") {
                    DetailText("无需注册账户，不读取通讯录、通话记录、相册或外部文件，不包含广告与行为分析组件。")
                } }
            }
        }
    }
}

@Composable
internal fun AboutPage(state: WeatherUiState, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val liveSnapshot = state.snapshot?.takeUnless { it.isDemo }
    val sources = (liveSnapshot?.sources.orEmpty() + state.citySummaries.values.flatMap { it.sources })
        .filter(String::isNotBlank).distinct().ifEmpty { listOf("和风天气") }
    val licenses = (liveSnapshot?.licenses.orEmpty() + state.citySummaries.values.flatMap { it.licenses })
        .filter(String::isNotBlank).distinct()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp)) {
            PageHeader("关于", "随想天气 · ThinkingWeather", onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ic_thinking_weather), null,
                                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
                            Column(Modifier.padding(start = 16.dp)) {
                                Text("随想天气", fontSize = 23.sp, fontWeight = FontWeight.Medium)
                                Text("版本 ${BuildConfig.VERSION_NAME}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        Text("把天气放轻一点，把生活看近一点。", fontSize = 15.sp, lineHeight = 25.sp,
                            fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp))
                    }
                }
                item { SettingsSection("天气数据") {
                    DetailText("实时天气数据由和风天气提供。预报受到地区覆盖、观测时间与服务可用性的影响，请以当地官方发布为准。")
                    if (state.isDemo) DetailText("示例预览使用内置样例，所有数值均为非实时数据，不用于出行或安全决策。", Modifier.padding(top = 12.dp))
                    TextButton(onClick = { runCatching { uri.openUri(validatedDataLink(liveSnapshot?.sourceUrl.orEmpty()) ?: "https://www.qweather.com/") } },
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 0.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text("和风天气", fontSize = 13.sp)
                        Spacer(Modifier.width(7.dp))
                        LineIcon(Glyph.External, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                    }
                } }
                item { SettingsSection("来源与归属") {
                    sources.forEachIndexed { index, source ->
                        AttributionItem(source, "数据来源说明", if (index == 0) Modifier else Modifier.padding(top = 14.dp))
                    }
                } }
                if (licenses.isNotEmpty()) item { SettingsSection("数据许可") {
                    licenses.forEachIndexed { index, license ->
                        val spacing = if (index == 0) Modifier else Modifier.padding(top = 10.dp)
                        AttributionItem(license, "查看数据许可", spacing)
                    }
                } }
            }
        }
    }
}

/** Links are only opened after an explicit tap, and cannot disguise another host with user info. */
private fun validatedDataLink(value: String): String? = runCatching {
    URI(value).takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null }
        ?.toASCIIString()
}.getOrNull()

@Composable
private fun AttributionItem(value: String, label: String, modifier: Modifier = Modifier) {
    val link = validatedDataLink(value)
    val uri = LocalUriHandler.current
    Column(modifier) {
        if (link != null) {
            TextButton(onClick = { runCatching { uri.openUri(link) } }, contentPadding = PaddingValues(0.dp)) {
                Text(label, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                LineIcon(Glyph.External, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
            }
        }
        DetailText(value)
    }
}

@Composable
private fun DetailText(value: String, modifier: Modifier = Modifier) {
    Text(value, modifier = modifier, fontSize = 13.sp, lineHeight = 23.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
