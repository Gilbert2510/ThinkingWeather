package com.thinkingweather.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Decorative solar trajectory; the dot is shown only within known daylight hours. */
@Composable
internal fun SunPath(progress: Float?) {
    Canvas(Modifier.fillMaxWidth().height(142.dp).testTag("sun-path").clearAndSetSemantics { }) {
        val left = size.width * .06f
        val span = size.width * .88f
        val horizon = size.height * .83f
        val rise = size.height * .64f
        fun point(t: Float): Offset = Offset(left + t * span, horizon - sin(PI * t).toFloat().coerceAtLeast(0f).pow(.9f) * rise)
        fun arc(offset: Float = 0f): Path = Path().apply {
            val first = point(0f)
            moveTo(first.x, first.y + offset)
            for (i in 1..96) { val p = point(i / 96f); lineTo(p.x, p.y + offset) }
        }
        val sun = progress?.let { point(it.coerceIn(0f, 1f)) }
        val glowCenter = sun ?: Offset(size.width * .5f, horizon)
        val fill = arc().apply { lineTo(left + span, horizon); lineTo(left, horizon); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Color(0xFFF6CA73).copy(alpha = if (sun != null) .14f else .025f), Color(0xFF80C8E9).copy(alpha = .025f), Color.Transparent)))
        drawPath(arc(11.dp.toPx()), Color(0xFF163F57).copy(alpha = .24f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawLine(Color(0xFFCAE1EB).copy(alpha = .22f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
        drawPath(arc(), Color(0xFFB4CEE1).copy(alpha = .16f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(arc(), Brush.radialGradient(
            colors = if (sun != null) listOf(Color(0xFFFFEDAB), Color(0xFFF0C568).copy(alpha = .86f), Color(0xFFBCA9BE).copy(alpha = .4f), Color(0xFF84ACC6).copy(alpha = .14f))
                else listOf(Color(0xFFB7CBDC).copy(alpha = .32f), Color(0xFF81A9C5).copy(alpha = .12f)),
            center = glowCenter, radius = span * .82f,
        ), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        for (t in listOf(0f, 1f)) drawCircle(Color(0xFFE5ECF1).copy(alpha = .55f), 2.dp.toPx(), point(t))
        if (sun != null) {
            val glow = 27.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFDC7B).copy(alpha = .38f), Color.Transparent), sun, glow), glow, sun)
            for (i in 0 until 8) {
                val angle = i * PI / 4
                val from = Offset(sun.x + cos(angle).toFloat() * 11.dp.toPx(), sun.y + sin(angle).toFloat() * 11.dp.toPx())
                val to = Offset(sun.x + cos(angle).toFloat() * 15.dp.toPx(), sun.y + sin(angle).toFloat() * 15.dp.toPx())
                drawLine(Color(0xFFFFE4A0), from, to, 1.8.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4CB), Color(0xFFF6C45C)), sun, 7.dp.toPx()), 7.dp.toPx(), sun)
        }
    }
}
