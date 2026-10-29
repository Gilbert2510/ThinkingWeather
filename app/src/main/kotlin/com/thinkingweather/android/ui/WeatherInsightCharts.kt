package com.thinkingweather.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun chartNumber(value: Float): String = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

/** Separate axes and units keep precipitation and temperatures from suggesting the same scale. */
@Composable
internal fun InsightLineChart(
    title: String, values: List<Float?>, labels: List<String>, suffix: String,
    accent: Color, muted: Color, modifier: Modifier = Modifier,
) {
    val domain = insightDomain(values) ?: return
    val valid = values.filterNotNull().filter(Float::isFinite)
    val axisWidth = (54 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val chartDescription = "$title，最低 ${chartNumber(valid.min())}$suffix，最高 ${chartNumber(valid.max())}$suffix。具体时点数据见下方列表。"
    Column(modifier.fillMaxWidth().semantics { contentDescription = chartDescription }) {
        Row(Modifier.fillMaxWidth().height(166.dp)) {
            Column(Modifier.width(axisWidth).fillMaxHeight().padding(bottom = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("${chartNumber(domain.high)}$suffix", color = muted, fontSize = 10.sp, maxLines = 1)
                Text("${chartNumber(domain.low)}$suffix", color = muted, fontSize = 10.sp, maxLines = 1)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp, bottom = 12.dp).clearAndSetSemantics { }) {
                val points = values.mapIndexed { index, value -> value?.takeIf(Float::isFinite)?.let {
                    Offset(if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1), size.height * (1f - domain.fraction(it)))
                } }
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(muted.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val segments = mutableListOf<List<Offset>>()
                var segment = mutableListOf<Offset>()
                for (point in points) {
                    if (point == null) { if (segment.isNotEmpty()) segments.add(segment); segment = mutableListOf() }
                    else segment.add(point)
                }
                if (segment.isNotEmpty()) segments.add(segment)
                segments.forEach { pathPoints ->
                    fun path() = Path().apply {
                        moveTo(pathPoints.first().x, pathPoints.first().y)
                        pathPoints.zipWithNext().forEach { (a, b) ->
                            val middle = (a.x + b.x) / 2f
                            cubicTo(middle, a.y, middle, b.y, b.x, b.y)
                        }
                    }
                    if (pathPoints.size > 1) {
                        val area = path().apply {
                            lineTo(pathPoints.last().x, size.height)
                            lineTo(pathPoints.first().x, size.height)
                            close()
                        }
                        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .2f), accent.copy(alpha = .015f))))
                        drawPath(path(), accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                    pathPoints.forEach { drawCircle(accent, if (points.size > 12) 2.dp.toPx() else 3.dp.toPx(), it) }
                }
            }
        }
        ChartTimeLabels(labels, muted, Modifier.padding(start = axisWidth))
    }
}

@Composable
internal fun InsightBarChart(
    title: String, values: List<Float?>, labels: List<String>, suffix: String,
    accent: Color, muted: Color,
) {
    val domain = insightDomain(values, zeroBaseline = true) ?: return
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "$title，最高 ${chartNumber(values.filterNotNull().max())}$suffix，坐标从零开始。" }) {
        Text("${chartNumber(domain.high)}$suffix", color = muted, fontSize = 10.sp)
        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 10.dp).clearAndSetSemantics { }) {
            val step = size.width / values.size.coerceAtLeast(1)
            drawLine(muted.copy(alpha = .18f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            values.forEachIndexed { index, value ->
                if (value != null && value.isFinite() && value > 0f) {
                    val height = size.height * domain.fraction(value)
                    drawRoundRect(Brush.verticalGradient(listOf(accent.copy(alpha = .9f), accent.copy(alpha = .35f))),
                        topLeft = Offset(step * index + step * .2f, size.height - height),
                        size = Size(step * .6f, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                } else if (value == 0f) {
                    drawCircle(accent.copy(alpha = .5f), 1.7.dp.toPx(), Offset(step * (index + .5f), size.height))
                }
            }
        }
        ChartTimeLabels(labels, muted, Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun ChartTimeLabels(labels: List<String>, muted: Color, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    val shown = listOf(0, labels.lastIndex / 2, labels.lastIndex).distinct().map(labels::get)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        shown.forEach { Text(it, color = muted, fontSize = 10.sp) }
    }
}

@Composable
internal fun InsightPercentageRing(label: String, percentage: Float, accent: Color, muted: Color) {
    Box(Modifier.size(92.dp).semantics { contentDescription = "$label ${chartNumber(percentage)}%" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp).clearAndSetSemantics { }) {
            val line = 7.dp.toPx()
            drawArc(accent.copy(alpha = .12f), -90f, 360f, false, style = Stroke(line, cap = StrokeCap.Round))
            drawArc(accent, -90f, percentage.coerceIn(0f, 100f) * 3.6f, false, style = Stroke(line, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text("${chartNumber(percentage)}%", color = accent, fontSize = 20.sp)
            Text(label, color = muted, fontSize = 10.sp)
        }
    }
}
