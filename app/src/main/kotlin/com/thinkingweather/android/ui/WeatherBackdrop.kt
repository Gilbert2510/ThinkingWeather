package com.thinkingweather.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thinkingweather.android.model.WeatherCategory

internal fun weatherBackgroundColors(category: WeatherCategory, night: Boolean = false): List<Color> =
    if (night) listOf(Color(0xFF122137), Color(0xFF243D59), Color(0xFF19384E)) else when (category) {
        WeatherCategory.CLEAR -> listOf(Color(0xFF176B9C), Color(0xFF286D8F), Color(0xFF1C506F))
        WeatherCategory.CLOUDY -> listOf(Color(0xFF3C6B89), Color(0xFF496B7E), Color(0xFF35596E))
        WeatherCategory.OVERCAST -> listOf(Color(0xFF4B5A6F), Color(0xFF5A6873), Color(0xFF3D5264))
        WeatherCategory.RAIN -> listOf(Color(0xFF23495F), Color(0xFF45677A), Color(0xFF264C61))
        WeatherCategory.THUNDERSTORM -> listOf(Color(0xFF27243D), Color(0xFF414760), Color(0xFF222F48))
        WeatherCategory.HAIL -> listOf(Color(0xFF34495F), Color(0xFF506A7B), Color(0xFF2A4359))
        WeatherCategory.SNOW -> listOf(Color(0xFF3E657F), Color(0xFF4D6A7D), Color(0xFF385C77))
        WeatherCategory.SLEET -> listOf(Color(0xFF36596C), Color(0xFF4B6B7B), Color(0xFF344E66))
        WeatherCategory.FOG -> listOf(Color(0xFF505D68), Color(0xFF596871), Color(0xFF435965))
        WeatherCategory.HAZE -> listOf(Color(0xFF655A52), Color(0xFF6F6558), Color(0xFF514E4D))
        WeatherCategory.DUST -> listOf(Color(0xFF71582F), Color(0xFF79633C), Color(0xFF5B4B34))
        WeatherCategory.WIND -> listOf(Color(0xFF235F69), Color(0xFF356F7A), Color(0xFF284B62))
        WeatherCategory.UNKNOWN -> listOf(Color(0xFF365F80), Color(0xFF4B6A7E), Color(0xFF426C80))
    }

@Composable
internal fun animatedWeatherColors(category: WeatherCategory, night: Boolean): List<Color> {
    val target = weatherBackgroundColors(category, night)
    val top by animateColorAsState(target[0], tween(650), label = "weather-sky-top")
    val middle by animateColorAsState(target[1], tween(650), label = "weather-sky-middle")
    val bottom by animateColorAsState(target[2], tween(650), label = "weather-sky-bottom")
    return listOf(top, middle, bottom)
}

/** Low-contrast scenery remains behind every label and card. No bitmap assets or network work. */
@Composable
internal fun WeatherAtmosphere(category: WeatherCategory, night: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val center = Offset(w * .9f, h * .16f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (night) .045f else .12f), Color.Transparent), center, w * .9f), w * .9f, center)
        if (night) for (i in 0..18) {
            drawCircle(Color.White.copy(alpha = .18f + i % 3 * .06f), if (i % 3 == 0) 1.8f else 1f,
                Offset((i * 137.508f % 1000) / 1000 * w, (i * 73.37f % 500) / 1000 * h))
        }
        when (category) {
            WeatherCategory.CLEAR -> if (!night) {
                val sun = Offset(w * .91f, h * .17f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFEDB4).copy(alpha = .18f), Color.Transparent), sun, w * .65f), w * .65f, sun)
            }
            WeatherCategory.CLOUDY, WeatherCategory.OVERCAST -> for (i in 0..3) {
                drawOval(Color.White.copy(alpha = if (category == WeatherCategory.CLOUDY) .045f else .065f), Offset(w * (i * .3f - .4f), h * (.12f + i * .06f)), androidx.compose.ui.geometry.Size(w * .95f, h * .1f))
            }
            WeatherCategory.RAIN, WeatherCategory.THUNDERSTORM, WeatherCategory.SLEET -> for (i in 0..30) {
                val x = (i * 97.37f % 1000) / 1000 * w
                val y = (i * 157.7f % 1000) / 1000 * h
                drawLine(Color.White.copy(alpha = .06f), Offset(x, y), Offset(x - 10, y + 31), 1.3f, StrokeCap.Round)
            }
            WeatherCategory.SNOW, WeatherCategory.HAIL -> for (i in 0..35) {
                drawCircle(Color.White.copy(alpha = .08f + (i % 3) * .025f), 1.5f + i % 3,
                    Offset((i * 97.37f % 1000) / 1000 * w, (i * 157.7f % 1000) / 1000 * h))
            }
            WeatherCategory.FOG, WeatherCategory.HAZE -> for (i in 0..5) {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .035f), Color.Transparent)), Offset(0f, h * (.08f + i * .13f)), androidx.compose.ui.geometry.Size(w, h * .18f))
            }
            WeatherCategory.DUST -> for (i in 0..39) {
                drawCircle(Color(0xFFF2D099).copy(alpha = .08f), 1f + i % 3,
                    Offset((i * 107.37f % 1000) / 1000 * w, (i * 167.7f % 1000) / 1000 * h))
            }
            WeatherCategory.WIND -> for (i in 0..4) {
                val y = h * (.12f + i * .17f)
                val path = Path().apply { moveTo(-w * .1f, y); cubicTo(w * .3f, y - 30f, w * .6f, y + 40f, w * 1.1f, y - 20f) }
                drawPath(path, Color.White.copy(alpha = .055f), style = Stroke(2f))
            }
            else -> Unit
        }
    }
}
