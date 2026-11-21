package org.everis.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A date jump uses one page of motion, even when the destination is weeks away.
 * The adjacent slot temporarily renders the actual departing day, so Pager's pre-jump
 * cannot expose intervening dates or flash a different list before the animation.
 */
@Stable
internal class DirectPagerNavigation(private val pager: PagerState, private val scope: CoroutineScope) {
    private data class Bridge(val slot: Int, val source: Int)
    private var bridge by mutableStateOf<Bridge?>(null)
    private var requested by mutableStateOf<Int?>(null)
    private var job: Job? = null
    private var generation = 0L
    val selectedPage: Int get() = requested ?: pager.targetPage
    val bridging: Boolean get() = bridge != null
    fun contentPage(slot: Int): Int = bridge?.takeIf { it.slot == slot }?.source ?: slot

    fun navigate(page: Int, efficient: Boolean) {
        val target = page.coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0))
        val previous = job
        val token = ++generation
        requested = target
        previous?.cancel()
        job = scope.launch {
            // Finish cleanup of a cancelled bridge before replacing its slot mapping.
            previous?.join()
            try {
                val from = pager.currentPage
                if (efficient) pager.scrollToPage(target)
                else if (abs(target - from) <= 1) pager.animateScrollToPage(target, animationSpec = tween(360, easing = FastOutSlowInEasing))
                else with(pager) { scroll {
                    val direction = if (target > from) 1 else -1
                    bridge = Bridge(target - direction, from)
                    updateTargetPage(target)
                    updateCurrentPage(target - direction)
                    val distance = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat() * direction
                    var consumed = 0f
                    animate(0f, distance, animationSpec = tween(360, easing = FastOutSlowInEasing)) { value, _ ->
                        consumed += scrollBy(value - consumed)
                    }
                    updateCurrentPage(target)
                } }
            } finally {
                bridge?.let { old ->
                    // Cancellation must not leave a real date associated with a temporary slot.
                    if (pager.currentPage == old.slot) withContext(NonCancellable) { pager.scrollToPage(old.source) }
                }
                bridge = null
                if (generation == token) requested = null
            }
        }
    }
}

@Composable
internal fun rememberDirectPagerNavigation(pager: PagerState): DirectPagerNavigation {
    val scope = rememberCoroutineScope()
    return remember(pager, scope) { DirectPagerNavigation(pager, scope) }
}
