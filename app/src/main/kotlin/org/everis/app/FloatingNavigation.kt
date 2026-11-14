package org.everis.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A transparent overlay; one full-height capsule follows taps and optional horizontal scrubbing. */
@Composable
fun FloatingNavigation(selected: Int, onSelect: (Int) -> Unit, onSectionRoot: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(R.string.home to R.drawable.ic_home, R.string.disciplines to R.drawable.ic_disciplines,
        R.string.timetable to R.drawable.ic_calendar_agenda, R.string.settings to R.drawable.ic_settings)
    val efficient = LocalPerformanceMode.current
    val haptics = LocalEverisHaptics.current
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var lastTick by remember { mutableIntStateOf(selected) }
    val latestSelection by rememberUpdatedState(onSelect)
    val rootAction = stringResource(R.string.section_root_action)
    val position by animateFloatAsState(scrubPosition ?: selected.toFloat(),
        if (efficient) tween(130) else spring(dampingRatio = 1f, stiffness = if (scrubPosition != null) 1000f else 480f, visibilityThreshold = .001f), label = "dock capsule")
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 340.dp).fillMaxWidth(), shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 4.dp, shadowElevation = 3.dp) {
            BoxWithConstraints(Modifier.height(58.dp)) {
                val width = maxWidth / items.size
                val cellPixels = with(LocalDensity.current) { width.toPx() }
                // Translate the entire state layer instead of relaying out or crossfading two pills.
                Surface(Modifier.width(width).fillMaxHeight().graphicsLayer { translationX = cellPixels * position },
                    shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {}
                Row(Modifier.fillMaxSize().selectableGroup().pointerInput(cellPixels) {
                    detectHorizontalDragGestures(
                        onDragStart = { point -> scrubPosition = (point.x / cellPixels - .5f).coerceIn(0f, 3f); lastTick = scrubPosition!!.roundToInt() },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val next = (change.position.x / cellPixels - .5f).coerceIn(0f, 3f)
                            scrubPosition = next
                            val tick = next.roundToInt()
                            if (tick != lastTick) { haptics?.tick(); lastTick = tick }
                        },
                        onDragEnd = { val index = scrubPosition?.roundToInt(); scrubPosition = null; if (index != null) latestSelection(index) },
                        onDragCancel = { scrubPosition = null })
                }) {
                    items.forEachIndexed { index, (title, icon) ->
                        val source = remember { MutableInteractionSource() }
                        val color by animateColorAsState(if (selected == index) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.motionScheme.fastEffectsSpec(), label = "dock tint")
                        Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .semantics { this.selected = selected == index }
                            .combinedClickable(role = Role.Tab, interactionSource = source, indication = null,
                                hapticFeedbackEnabled = false, onClick = { latestSelection(index) },
                                onLongClickLabel = if (selected == index) rootAction else null,
                                onLongClick = if (selected == index) ({ onSectionRoot(index) }) else null),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(icon), stringResource(title), Modifier.size(22.dp).pressFeedback(source), tint = color)
                            if (LocalConfiguration.current.fontScale <= 1.4f)
                                Text(stringResource(title), Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp), style = MaterialTheme.typography.labelSmall,
                                    color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Traditional Material navigation retains the same section-root action without an overlay. */
@Composable
fun ClassicNavigation(selected: Int, onSelect: (Int) -> Unit, onSectionRoot: (Int) -> Unit) {
    val items = listOf(R.string.home to R.drawable.ic_home, R.string.disciplines to R.drawable.ic_disciplines,
        R.string.timetable to R.drawable.ic_calendar_agenda, R.string.settings to R.drawable.ic_settings)
    val rootAction = stringResource(R.string.section_root_action)
    NavigationBar {
        items.forEachIndexed { index, (title, icon) ->
            val source = remember { MutableInteractionSource() }
            val active = selected == index
            val tint by animateColorAsState(if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.motionScheme.fastEffectsSpec(), label = "classic navigation tint")
            val fill by animateColorAsState(if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                MaterialTheme.motionScheme.fastEffectsSpec(), label = "classic navigation indicator")
            Column(Modifier.weight(1f).height(80.dp).semantics { this.selected = active }
                .combinedClickable(role = Role.Tab, interactionSource = source, indication = null, hapticFeedbackEnabled = false,
                    onClick = { onSelect(index) }, onLongClickLabel = if (active) rootAction else null,
                    onLongClick = if (active) ({ onSectionRoot(index) }) else null),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(Modifier.size(width = 64.dp, height = 32.dp), shape = CircleShape, color = fill) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), stringResource(title), Modifier.size(24.dp).pressFeedback(source), tint = tint) }
                }
                if (LocalConfiguration.current.fontScale <= 1.4f)
                    Text(stringResource(title), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
