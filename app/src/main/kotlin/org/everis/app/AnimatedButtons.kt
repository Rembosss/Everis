@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.everis.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Extra feedback for small independent icons, outside Material button surfaces. */
@Composable
internal fun Modifier.pressFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val efficient = LocalPerformanceMode.current
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f,
        if (efficient) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.9f, stiffness = if (pressed) 550f else 220f), label = "button press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
private fun hapticClick(onClick: () -> Unit): () -> Unit {
    val feedback = LocalEverisHaptics.current
    return { feedback?.select(); onClick() }
}

@Composable fun EverisButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    Button(onClick = hapticClick(onClick), shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = enabled, interactionSource = source, content = content)
}
@Composable fun EverisOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(onClick = hapticClick(onClick), shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = enabled, interactionSource = source, content = content)
}
@Composable fun EverisTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilledTonalButton(onClick = hapticClick(onClick), shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = enabled, interactionSource = source, content = content)
}
@Composable fun EverisTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    TextButton(onClick = hapticClick(onClick), shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = enabled,
        contentPadding = ButtonDefaults.TextButtonContentPadding, interactionSource = source, content = content)
}
@Composable fun EverisIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick = hapticClick(onClick), shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = enabled, interactionSource = source, content = content)
}
