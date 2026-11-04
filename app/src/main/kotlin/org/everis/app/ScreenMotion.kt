package org.everis.app

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/** Positive direction enters from the right; negative direction reverses the same motion.
 * No delayed exit or size animation: a new selection retargets the running transition immediately.
 * Callers keep an opaque Material background behind both surfaces.
 */
fun screenTransition(direction: Int = 1, efficient: Boolean = false): ContentTransform {
    val incoming = fadeIn(tween(if (efficient) 100 else 220, easing = FastOutSlowInEasing))
    val outgoing = fadeOut(tween(if (efficient) 90 else 140))
    val result = if (efficient) incoming togetherWith outgoing
    else (incoming + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 12 * direction }) togetherWith
        (outgoing + slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 12 * direction })
    return ContentTransform(result.targetContentEnter, result.initialContentExit, targetContentZIndex = 1f, sizeTransform = null)
}

/** Text moves in one direction without two independent fades exposing the backdrop. */
fun headingTransition(direction: Int = 1, efficient: Boolean = false): ContentTransform {
    if (efficient) return EnterTransition.None togetherWith ExitTransition.None
    val duration = 300
    return slideInVertically(tween(duration, easing = FastOutSlowInEasing)) { it * direction } togetherWith
        slideOutVertically(tween(duration, easing = FastOutSlowInEasing)) { -it * direction }
}
