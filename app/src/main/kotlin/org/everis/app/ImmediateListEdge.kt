package org.everis.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Release at the edge immediately, without waiting for the platform stretch fling. */
@Composable
fun Modifier.immediateListEdge(state: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val efficient = LocalPerformanceMode.current
    val limit = with(LocalDensity.current) { 24.dp.toPx() }
    val connection = remember(state, efficient, limit) {
        object : NestedScrollConnection {
            var release: Job? = null
            var flingBounced = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) flingBounced = false
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (efficient || kotlin.math.abs(available.y) < .5f) return Offset.Zero
                if (source == NestedScrollSource.UserInput) {
                    release?.cancel()
                    release = scope.launch { offset.snapTo((offset.value + available.y * .18f).coerceIn(-limit, limit)) }
                } else if (!flingBounced) {
                    flingBounced = true
                    release?.cancel()
                    release = scope.launch {
                        offset.snapTo((available.y * .1f).coerceIn(-limit * .5f, limit * .5f))
                        offset.animateTo(0f, tween(220))
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                release?.cancel()
                release = scope.launch { offset.animateTo(0f, tween(220)) }
                val atEdge = available.y > 0 && !state.canScrollBackward || available.y < 0 && !state.canScrollForward
                return if (atEdge) available else Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection).graphicsLayer { translationY = if (efficient) 0f else offset.value }
}
