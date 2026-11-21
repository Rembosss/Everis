package org.everis.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One anchored popup for selectors and editable search; animation never resizes its parent. */
@Composable
internal fun AnchoredMenu(expanded: Boolean, widthPx: Int, onDismiss: () -> Unit,
    focusable: Boolean = true, preferBelow: Boolean = false, maxHeight: Dp = 360.dp,
    scrollable: Boolean = true, reserveWindowHeight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return
    val density = LocalDensity.current
    val efficient = LocalPerformanceMode.current
    val gap = with(density) { 6.dp.roundToPx() }
    val margin = with(density) { 12.dp.roundToPx() }
    var above by remember { mutableStateOf(false) }
    var availableHeight by remember { mutableIntStateOf(with(density) { maxHeight.roundToPx() }) }
    val provider = remember(gap, margin, preferBelow) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val position = anchoredMenuPosition(anchorBounds, windowSize, popupContentSize,
                    layoutDirection, gap, margin, preferBelow)
                if (above != position.above) above = position.above
                if (availableHeight != position.availableHeight) availableHeight = position.availableHeight
                return position.offset
            }
        }
    }
    val transition = rememberTransition(visible, label = "anchored menu")
    val duration = if (efficient) 170 else 240
    val reveal by transition.animateFloat(transitionSpec = { tween(duration, easing = FastOutSlowInEasing) }, label = "menu reveal") { if (it) 1f else 0f }
    val displacement = with(density) { (if (efficient) 3.dp else 10.dp).toPx() }
    val corner = with(density) { 28.dp.toPx() }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss,
        // A keyboard-mode key is outside this window but must not dismiss editable suggestions.
        properties = PopupProperties(focusable = focusable, dismissOnClickOutside = focusable)) {
      val slotHeight = with(density) { availableHeight.toDp().coerceIn(1.dp, maxHeight) }
      val dismiss by rememberUpdatedState(onDismiss)
      Box(Modifier.width(with(density) { widthPx.toDp() }).heightIn(max = slotHeight)
          .then(if (reserveWindowHeight) Modifier.height(slotHeight) else Modifier), contentAlignment = Alignment.TopStart) {
        if (reserveWindowHeight) Spacer(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures { dismiss() }
        })
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp, shadowElevation = 0.dp,
            modifier = Modifier.width(with(density) { widthPx.toDp() })
                .heightIn(max = with(density) { availableHeight.toDp().coerceIn(1.dp, maxHeight) })
                .graphicsLayer {
                    alpha = .94f + .06f * reveal
                    translationY = (1f - reveal) * displacement * if (above) 1f else -1f
                }.drawWithContent {
                    val progress = reveal.coerceIn(0f, 1f)
                    val height = size.height * progress
                    if (height > 0f) {
                        val top = if (above) size.height - height else 0f
                        val radius = corner.coerceAtMost(size.width / 2).coerceAtMost(height / 2)
                        val outline = Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + height, CornerRadius(radius))) }
                        clipPath(outline) { this@drawWithContent.drawContent() }
                    }
                }) {
            Column((if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = if (scrollable) 6.dp else 0.dp), content = content)
        }
      }
    }
}
