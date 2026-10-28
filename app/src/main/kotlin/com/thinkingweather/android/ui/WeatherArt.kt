package com.thinkingweather.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import com.thinkingweather.android.model.WeatherCategory
import com.thinkingweather.android.model.weatherCategory

internal enum class Glyph { Cities, Settings, Location, Close, Search, Check, Wind, Drop, Sun, Eye, Pressure, Chevron, External, Back, ArrowUp }

@Composable
internal fun LineIcon(glyph: Glyph, modifier: Modifier = Modifier, color: Color = SkyWhite) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.65f, cap = StrokeCap.Round)
            fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(color, Offset(a,b), Offset(c,d), strokeWidth = 1.65f, cap = StrokeCap.Round)
            when (glyph) {
                Glyph.Cities -> { line(4f,6f,15f,6f);line(4f,12f,12f,12f);line(4f,18f,12f,18f);line(18f,12f,18f,20f);line(14f,16f,22f,16f) }
                Glyph.Settings -> {
                    val contour = Path().apply {
                        for (i in 0 until 32) {
                            val angle = (i / 4 + when(i % 4) { 0 -> -.5; 1 -> -.27; 2 -> .27; else -> .5 }) * Math.PI / 4
                            val radius = if (i % 4 == 1 || i % 4 == 2) 9.3f else 7.5f
                            val x = 12f + cos(angle).toFloat() * radius
                            val y = 12f + sin(angle).toFloat() * radius
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(contour, color, style = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(color, 3.2f, Offset(12f,12f), style=stroke)
                }
                Glyph.Location -> { val p=Path().apply{moveTo(12f,22f);cubicTo(8f,16f,4f,13f,4f,9f);cubicTo(4f,-1f,20f,-1f,20f,9f);cubicTo(20f,13f,16f,16f,12f,22f);close()};drawPath(p,color,style=stroke);drawCircle(color,2.5f,Offset(12f,8f),style=stroke) }
                Glyph.Close -> { line(6f,6f,18f,18f);line(6f,18f,18f,6f) }
                Glyph.Search -> {drawCircle(color,7f,Offset(10f,10f),style=stroke);line(15f,15f,21f,21f)}
                Glyph.Check -> {line(5f,12f,10f,17f);line(10f,17f,20f,6f)}
                Glyph.Wind -> {line(3f,9f,16f,9f);drawArc(color,180f,270f,false,Offset(13f,3f),Size(6f,6f),style=stroke);line(3f,13f,20f,13f);line(5f,17f,13f,17f);drawArc(color,-90f,270f,false,Offset(10f,17f),Size(5f,5f),style=stroke)}
                Glyph.Drop -> {val p=Path().apply{moveTo(12f,2f);cubicTo(10f,7f,5f,11f,5f,15f);cubicTo(5f,24f,19f,24f,19f,15f);cubicTo(19f,11f,14f,7f,12f,2f);close()};drawPath(p,color,style=stroke)}
                Glyph.Sun -> {drawCircle(color,4f,Offset(12f,12f),style=stroke);for(i in 0..7){val t=i*Math.PI/4;line(12f+cos(t).toFloat()*7,12f+sin(t).toFloat()*7,12f+cos(t).toFloat()*9,12f+sin(t).toFloat()*9)}}
                Glyph.Eye -> {val p=Path().apply{moveTo(2f,12f);quadraticTo(12f,-1f,22f,12f);quadraticTo(12f,25f,2f,12f)};drawPath(p,color,style=stroke);drawCircle(color,3f,Offset(12f,12f),style=stroke)}
                Glyph.Pressure -> {drawArc(color,140f,260f,false,Offset(3f,4f),Size(18f,18f),style=stroke);line(12f,13f,17f,8f);drawCircle(color,2f,Offset(12f,13f))}
                Glyph.Chevron -> {line(9f,5f,16f,12f);line(16f,12f,9f,19f)}
                Glyph.External -> {line(13f,4f,21f,4f);line(21f,4f,21f,12f);line(21f,4f,11f,14f);val p=Path().apply{moveTo(9f,5f);lineTo(4f,5f);lineTo(4f,20f);lineTo(19f,20f);lineTo(19f,15f)};drawPath(p,color,style=stroke)}
                Glyph.Back -> {line(14f,5f,7f,12f);line(7f,12f,14f,19f)}
                Glyph.ArrowUp -> {line(12f,20f,12f,4f);line(5f,11f,12f,4f);line(12f,4f,19f,11f)}
            }
        }
    }
}

@Composable
internal fun WeatherSymbol(icon: String, text: String, modifier: Modifier = Modifier, night: Boolean = false) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val category = weatherCategory(icon, text)
        val showSun = category == WeatherCategory.CLEAR || category == WeatherCategory.CLOUDY
        if (showSun && night) {
            val center = Offset(w * .63f, h * .30f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF1E6C8).copy(alpha = .16f), Color.Transparent), center, w * .42f), w * .42f, center)
            val radius = w * .18f
            val moon = Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
            val cutCenter = Offset(w * .72f, h * .24f)
            val cutRadius = w * .16f
            val cutout = Path().apply { addOval(Rect(cutCenter.x - cutRadius, cutCenter.y - cutRadius, cutCenter.x + cutRadius, cutCenter.y + cutRadius)) }
            drawPath(Path.combine(PathOperation.Difference, moon, cutout), Color(0xFFF4E7C9))
            drawCircle(SkyWhite.copy(alpha = .6f), w * .012f, Offset(w * .20f, h * .17f))
        } else if (showSun) {
            val center = Offset(w * .62f, h * .34f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3AA).copy(alpha = .26f), Color.Transparent), center, w * .48f), w * .48f, center)
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFEBC2), Color(0xFFF4C673))), w * if(category == WeatherCategory.CLEAR) .26f else .20f, center)
        }
        if (category in setOf(WeatherCategory.CLOUDY, WeatherCategory.OVERCAST, WeatherCategory.RAIN,
                WeatherCategory.THUNDERSTORM, WeatherCategory.HAIL, WeatherCategory.SNOW, WeatherCategory.SLEET)) {
            cloud(w, h, category != WeatherCategory.CLOUDY)
        }
        val heavy = text.contains("大") || text.contains("暴")
        when(category) {
            WeatherCategory.RAIN, WeatherCategory.THUNDERSTORM -> {
                val count = if(heavy) 5 else 3
                for(i in 0 until count) {
                    val x = w * (.26f + i * .48f / (count - 1))
                    drawLine(Color(0xFFA5D7EB), Offset(x, h * .79f), Offset(x - w * .045f, h * .94f), strokeWidth = w * .023f, cap = StrokeCap.Round)
                }
                if(category == WeatherCategory.THUNDERSTORM) {
                    val bolt = Path().apply { moveTo(w * .57f, h * .56f); lineTo(w * .44f, h * .79f); lineTo(w * .56f, h * .79f); lineTo(w * .48f, h * .98f); lineTo(w * .72f, h * .69f); lineTo(w * .59f, h * .69f); close() }
                    drawPath(bolt, Color(0xFFFFDF7D))
                }
            }
            WeatherCategory.SNOW -> for(i in 0 until if(heavy) 5 else 3) {
                snowflake(Offset(w * (.23f + i * (if (heavy) .13f else .23f)), h * (.86f + (if (i % 2 == 0) 0f else .05f))), w * .038f)
            }
            WeatherCategory.SLEET -> {
                for(i in 0..1) drawLine(Color(0xFFA5D7EB), Offset(w * (.28f + i * .3f), h * .79f), Offset(w * (.24f + i * .3f), h * .93f), w * .023f, StrokeCap.Round)
                for(i in 0..1) snowflake(Offset(w * (.43f + i * .3f), h * .87f), w * .035f)
            }
            WeatherCategory.HAIL -> for(i in 0..2) {
                val x = w * (.3f + i * .20f); val y = h * (.84f + if(i == 1) .07f else 0f)
                val ice = Path().apply { moveTo(x, y - w * .035f); lineTo(x + w * .035f, y); lineTo(x, y + w * .035f); lineTo(x - w * .035f, y); close() }
                drawPath(ice, Color(0xFFE3F6FF))
            }
            WeatherCategory.FOG, WeatherCategory.HAZE -> {
                val color = if(category == WeatherCategory.FOG) Color(0xFFDBE9F0) else Color(0xFFE1CCAB)
                for(i in 0..3) drawLine(color.copy(alpha = .9f), Offset(w * (if(i % 2 == 0) .13f else .22f), h * (.30f + i * .17f)), Offset(w * (if(i % 2 == 0) .86f else .78f), h * (.30f + i * .17f)), w * .045f, StrokeCap.Round)
            }
            WeatherCategory.DUST -> {
                for(i in 0..2) drawLine(Color(0xFFE8C796), Offset(w * .13f, h * (.35f + i * .2f)), Offset(w * .86f, h * (.35f + i * .2f)), w * .035f, StrokeCap.Round)
                for(i in 0..8) drawCircle(Color(0xFFE8C796), w * .018f, Offset(w * (.19f + (i % 3) * .25f), h * (.2f + i / 3 * .23f)))
            }
            WeatherCategory.WIND -> {
                for(i in 0..2) {
                    val y = h * (.3f + i * .2f)
                    val path = Path().apply { moveTo(w * .1f, y); lineTo(w * .67f, y); cubicTo(w * .99f, y, w * .96f, y - h * .23f, w * .72f, y - h * .14f) }
                    drawPath(path, Color(0xFFD4ECEF), style = Stroke(w * .035f, cap = StrokeCap.Round))
                }
            }
            WeatherCategory.UNKNOWN -> {
                drawCircle(SkyMuted, w * .32f, Offset(w * .5f, h * .5f), style = Stroke(w * .025f))
                val question = Path().apply { moveTo(w * .39f, h * .37f); cubicTo(w * .39f, h * .2f, w * .72f, h * .24f, w * .62f, h * .43f); lineTo(w * .51f, h * .53f); lineTo(w * .51f, h * .59f) }
                drawPath(question, SkyMuted, style = Stroke(w * .035f, cap = StrokeCap.Round))
                drawCircle(SkyMuted, w * .022f, Offset(w * .51f, h * .7f))
            }
            else -> Unit
        }
    }
}

private fun DrawScope.snowflake(center: Offset, radius: Float) {
    for(i in 0..2) {
        val angle = i * Math.PI / 3
        val delta = Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
        drawLine(Color(0xFFE2F6FF), center - delta, center + delta, radius * .25f, StrokeCap.Round)
    }
}
private fun DrawScope.cloud(w: Float, h: Float, rainy: Boolean) {
    val tint=if(rainy) Color(0xFFCFDCE8) else Color(0xFFF3F7FC)
    val path=Path().apply{
        moveTo(w*.22f,h*.72f)
        cubicTo(w*.04f,h*.72f,w*.03f,h*.43f,w*.23f,h*.43f)
        cubicTo(w*.23f,h*.15f,w*.63f,h*.12f,w*.67f,h*.43f)
        cubicTo(w*.91f,h*.35f,w*1.03f,h*.72f,w*.77f,h*.72f)
        close()
    }
    drawPath(path,Brush.linearGradient(listOf(tint,Color(0xFFB6CBDE)),Offset(w*.4f,h*.3f),Offset(w*.6f,h*.8f)))
}
