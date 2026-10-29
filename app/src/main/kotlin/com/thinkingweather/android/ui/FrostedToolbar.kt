package com.thinkingweather.android.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Capture drawing commands once; only the small toolbar overlay is blurred on the GPU. */
internal fun Modifier.weatherBackdropSource(layer: GraphicsLayer): Modifier =
    if (Build.VERSION.SDK_INT >= 31) drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    } else this

/** A feathered blur fades into the scrolling content. API 26–30 use the same tinted scrim. */
@Composable
internal fun FrostedToolbarBackdrop(source: GraphicsLayer, tint: Color, active: Boolean, modifier: Modifier) {
    val overlay = rememberGraphicsLayer()
    val supported = remember { Build.VERSION.SDK_INT >= 31 }
    Box(modifier.drawWithContent {
        if (active && supported) {
            overlay.compositingStrategy = CompositingStrategy.Offscreen
            overlay.renderEffect = BlurEffect(12.dp.toPx(), 12.dp.toPx(), TileMode.Clamp)
            overlay.clip = true
            overlay.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                drawLayer(source)
                drawRect(Brush.verticalGradient(listOf(Color.White, Color.White, Color.Transparent)), blendMode = BlendMode.DstIn)
            }
            drawLayer(overlay)
        }
        drawRect(Brush.verticalGradient(listOf(
            tint.copy(alpha = if (active) .93f else .65f),
            tint.copy(alpha = if (active) .82f else .30f),
            tint.copy(alpha = .0f),
        )))
        drawContent()
    })
}
