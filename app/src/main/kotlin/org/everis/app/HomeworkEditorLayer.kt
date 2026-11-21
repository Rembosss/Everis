package org.everis.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Measure the editor at its final top position before revealing it; animate drawing only. */
@Composable
internal fun HomeworkEditorLayer(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState) return
    val efficient = LocalPerformanceMode.current
    val transition = rememberTransition(state, label = "homework editor")
    val alpha by transition.animateFloat(transitionSpec = { tween(if (efficient) 100 else 170) }, label = "editor opacity") { if (it) 1f else 0f }
    val position by transition.animateFloat(transitionSpec = { tween(if (efficient) 100 else 260, easing = FastOutSlowInEasing) }, label = "editor position") { if (it) 1f else 0f }
    val offset = with(LocalDensity.current) { (if (efficient) 0.dp else 24.dp).toPx() }
    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = (1f - position) * offset },
        contentAlignment = Alignment.TopStart) { content() }
}
