package org.everis.app

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

/** Standard Android haptics respect both the user's app preference and system settings. */
@Stable
class EverisHaptics(private val view: View, private val enabled: () -> Boolean) {
    private var lastTick = 0L
    fun select() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 45) return
        lastTick = now
        perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    }
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)
    fun toggle(on: Boolean) = perform(if (Build.VERSION.SDK_INT >= 34) {
        if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
    } else HapticFeedbackConstants.CLOCK_TICK)
    private fun perform(effect: Int) { if (enabled() && view.isAttachedToWindow) view.performHapticFeedback(effect) }
}

val LocalEverisHaptics = staticCompositionLocalOf<EverisHaptics?> { null }

@Composable
fun HapticsProvider(enabled: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val preference by rememberUpdatedState(enabled)
    val haptics = remember(view) { EverisHaptics(view) { preference } }
    CompositionLocalProvider(LocalEverisHaptics provides haptics, content = content)
}
