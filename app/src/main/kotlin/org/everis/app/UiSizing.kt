package org.everis.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Caps scrollable controls to the visible viewport, including split-screen and the IME. */
@Composable
internal fun visibleWindowHeight(): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val safe = WindowInsets.safeDrawing
    val ime = WindowInsets.ime
    return with(density) {
        (window.height - safe.getTop(this) - maxOf(safe.getBottom(this), ime.getBottom(this)))
            .coerceAtLeast(0).toDp().coerceAtLeast(96.dp)
    }
}
