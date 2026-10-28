package com.thinkingweather.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Only consumes downward drag left over after the list reaches its top. */
@Stable
internal class PullDownRefreshState(private val threshold: Float) {
    var distance by mutableFloatStateOf(0f)
        private set
    var released by mutableStateOf(false)
        private set
    private var touchActive = false
    val progress get() = (distance / threshold).coerceIn(0f, 1f)
    val ready get() = distance >= threshold

    fun connection(list: LazyListState, busy: () -> Boolean, refresh: () -> Unit) =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!touchActive || source != NestedScrollSource.UserInput || available.y >= 0 || distance <= 0) return Offset.Zero
                val consumed = maxOf(available.y, -distance * 2)
                distance = (distance + consumed / 2).coerceAtLeast(0f)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Compose semantics/key scrolling also uses UserInput. Only a physical drag arms refresh.
                if (!touchActive || source != NestedScrollSource.UserInput || busy() || released || list.canScrollBackward || available.y <= 0) return Offset.Zero
                distance = (distance + available.y / 2).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (distance == 0f) return Velocity.Zero
                val shouldRefresh = ready && !busy() && !released
                distance = 0f
                if (shouldRefresh) { released = true; refresh() }
                return Velocity(0f, available.y)
            }
        }

    fun finishRelease() { released = false }
    fun cancelDrag() { distance = 0f }
    fun beginTouch() { distance = 0f; touchActive = true }
    fun endTouch(cancelled: Boolean) {
        touchActive = false
        // A normal pointer-up is followed by nested-scroll onPreFling, including zero velocity.
        if (cancelled) distance = 0f
    }
}

/** Observe the initial pass without consuming events needed by the list or its child controls. */
internal fun Modifier.observePullDownTouch(state: PullDownRefreshState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.beginTouch()
        var normalRelease = false
        try {
            var fingersDown = true
            while (fingersDown) {
                fingersDown = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
            }
            normalRelease = true
        } finally {
            state.endTouch(cancelled = !normalRelease)
        }
    }
}

@Composable
internal fun rememberPullDownRefreshState(busy: Boolean): PullDownRefreshState {
    // Finger travels about 144 dp, damped to a 72 dp indicator displacement.
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val state = remember(threshold) { PullDownRefreshState(threshold) }
    LaunchedEffect(state.released, busy) {
        if (state.released && !busy) { delay(400); state.finishRelease() }
        if (busy) state.cancelDrag()
    }
    return state
}

@Composable
internal fun RefreshHeader(state: PullDownRefreshState, refreshing: Boolean, initialLoading: Boolean) {
    val rotation by animateFloatAsState(if (state.ready) 0f else 180f, label = "refresh-arrow")
    val active = refreshing || state.released
    val height by animateDpAsState(
        when { initialLoading -> 8.dp; active -> 44.dp; else -> (28 + 20 * state.progress).dp },
        label = "refresh-header-height",
    )
    Row(
        Modifier.fillMaxWidth().height(height).testTag("refresh-header")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (!initialLoading) {
            if (active) CircularProgressIndicator(Modifier.size(14.dp), color = SkyWhite, strokeWidth = 1.5.dp)
            else LineIcon(Glyph.ArrowUp, Modifier.size(14.dp).graphicsLayer { rotationZ = rotation }, SkyMuted)
            Text(when { active -> "正在刷新天气"; state.ready -> "松开刷新"; state.distance > 0 -> "继续下拉刷新"; else -> "下拉刷新天气" }, color = SkyMuted, fontSize = 11.sp)
        }
    }
}
