package org.everis.app

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal data class AnchoredMenuPosition(val offset: IntOffset, val availableHeight: Int, val above: Boolean)

/** Compose Popup supplies the visible display frame, with the keyboard already excluded. */
internal fun anchoredMenuPosition(anchor: IntRect, visibleWindow: IntSize, content: IntSize,
    layoutDirection: LayoutDirection, gap: Int, margin: Int, preferBelow: Boolean): AnchoredMenuPosition {
    val below = (visibleWindow.height - margin - anchor.bottom - gap).coerceAtLeast(0)
    val top = (anchor.top - gap - margin).coerceAtLeast(0)
    val above = !preferBelow && content.height > below && top > below
    val available = if (above) top else below
    // The first measurement may precede the new height constraint. Keep the anchor stable
    // during that pass too, instead of shifting a tall popup over the editable field.
    val height = content.height.coerceAtMost(available)
    val x = if (layoutDirection == LayoutDirection.Ltr) anchor.left else anchor.right - content.width
    val y = if (above) anchor.top - gap - height else anchor.bottom + gap
    return AnchoredMenuPosition(IntOffset(
        x.coerceIn(0, (visibleWindow.width - content.width).coerceAtLeast(0)),
        y.coerceIn(0, (visibleWindow.height - height).coerceAtLeast(0))), available, above)
}
