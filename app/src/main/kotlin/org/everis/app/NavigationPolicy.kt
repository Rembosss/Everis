package org.everis.app

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp

/** Outgoing AnimatedContent must not intercept Back for a screen that is no longer selected. */
val LocalScreenActive = staticCompositionLocalOf { true }
val LocalNavigationClearance = staticCompositionLocalOf { 108.dp }
val LocalSectionReset = staticCompositionLocalOf { 0 }

/** Per-section request survives tab disposal without resetting a different tab on its next visit. */
@Composable
fun OnSectionReset(onReset: () -> Unit) {
    val request = LocalSectionReset.current
    val active = LocalScreenActive.current
    var handled by rememberSaveable { mutableIntStateOf(request) }
    val action by rememberUpdatedState(onReset)
    LaunchedEffect(request, active) {
        if (active && request != handled) { handled = request; action() }
    }
}

data class TabSelection(val index: Int, val direction: Int = 1) {
    fun select(target: Int): TabSelection {
        require(target in 0..3)
        return if (target == index) this else TabSelection(target, pageDirection(index, target))
    }
}

/** The order in the navigation bar determines the transition, independent of visit history. */
fun pageDirection(previous: Int, target: Int): Int = if (target >= previous) 1 else -1

fun calendarDirection(oldMode: Int, oldDate: String, newMode: Int, newDate: String): Int =
    if (newMode != oldMode) if (newMode > oldMode) -1 else 1
    else if (newDate >= oldDate) 1 else -1

/** Read legacy saved history once, retaining only the selected tab. Back has no app history. */
fun restoreSelectedTab(saved: Any?): Int {
    if (saved is Int) return saved.takeIf { it in 0..3 } ?: 0
    val values = when (saved) {
        is IntArray -> saved.toList()
        is List<*> -> if (saved.all { it is Int }) saved.filterIsInstance<Int>() else emptyList()
        else -> emptyList()
    }
    return values.takeIf { it.isNotEmpty() && it.size <= 4 && it.all { tab -> tab in 0..3 } && it.distinct().size == it.size }?.last() ?: 0
}

fun restoreSettingsPage(saved: String): String = when (saved) {
    "general" -> "language"
    "root", "study", "appearance", "language", "haptics", "performance", "notifications", "data", "about" -> saved
    else -> "root"
}
