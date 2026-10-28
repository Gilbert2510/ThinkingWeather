package com.thinkingweather.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

internal val SkyWhite = Color(0xFFF7FAFF)
internal val SkyMuted = Color(0xFFE0EAF3)
internal val SkyAccent = Color(0xFFB7E5E3)

@Composable
fun ThinkingWeatherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA8DCD9), onPrimary = Color(0xFF0A3639),
        surface = Color(0xFF142833), onSurface = Color(0xFFE7F0F4),
        surfaceContainer = Color(0xFF1C3543), background = Color(0xFF102431),
    ) else lightColorScheme(
        primary = Color(0xFF2C666D), onPrimary = Color.White,
        surface = Color(0xFFF5F8FA), onSurface = Color(0xFF203442),
        surfaceContainer = Color(0xFFEAF0F4), background = Color(0xFFF5F8FA),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp),
        ),
        content = content,
    )
}
