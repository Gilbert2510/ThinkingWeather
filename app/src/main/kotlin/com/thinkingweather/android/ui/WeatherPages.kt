package com.thinkingweather.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinkingweather.android.WeatherUiState
import com.thinkingweather.android.WeatherViewModel
import com.thinkingweather.android.model.TemperatureUnit
@Composable
internal fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    // The body keeps a 22 dp gutter; the 48 dp back target starts at the safe edge + 8 dp.
    Row(Modifier.fillMaxWidth().offset(x = (-14).dp).padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        WeatherIconButton("返回", Glyph.Back, onBack, MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f).padding(start = 7.dp)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
internal fun SettingsPage(state: WeatherUiState, vm: WeatherViewModel, onBack: () -> Unit, onPrivacy: () -> Unit, onAbout: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp)) {
            PageHeader("设置", "简单设置，舒适看天气", onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
                item { SettingsSection("温度单位") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UnitButton("摄氏度  ℃", state.temperatureUnit == TemperatureUnit.CELSIUS, Modifier.weight(1f)) { vm.setTemperatureUnit(TemperatureUnit.CELSIUS) }
                        UnitButton("华氏度  °F", state.temperatureUnit == TemperatureUnit.FAHRENHEIT, Modifier.weight(1f)) { vm.setTemperatureUnit(TemperatureUnit.FAHRENHEIT) }
                    }
                } }
                item { SettingsNavigationItem("隐私", "位置权限与本地数据", onPrivacy) }
                item { SettingsNavigationItem("关于", "版本信息与天气数据来源", onAbout) }
            }
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
