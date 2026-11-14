package org.everis.app

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.everis.app.data.RsueData
import org.everis.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun WeekStrip(selected: LocalDate, lessons: List<Lesson>, published: Set<LocalDate>, onDay: (LocalDate) -> Unit) {
    val start = weekStart(selected)
    val motion = MaterialTheme.motionScheme
    val feedback = LocalEverisHaptics.current
    val efficient = LocalPerformanceMode.current
    val configuration = LocalConfiguration.current
    val locale = uiLocale(configuration)
    val narrow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() < 360.dp } || configuration.fontScale > 1.3f
    val dayShape = RoundedCornerShape(20.dp)
    Surface(shape = dayShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        AnimatedContent(start, modifier = Modifier.fillMaxWidth().clipToBounds(), label = "week strip", transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            if (efficient) EnterTransition.None togetherWith ExitTransition.None else
                slideInHorizontally(androidx.compose.animation.core.tween(320)) { it * direction } togetherWith
                slideOutHorizontally(androidx.compose.animation.core.tween(320)) { -it * direction }
        }) { week ->
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(7) { offset ->
                        val day = week.plusDays(offset.toLong())
                        val daily = remember(day, lessons) { lessonsForDay(day, RsueData.zone, lessons) }
                        val known = dayCoverage(day, published, daily, RsueData.zone).complete
                        val active = day == selected
                        val background by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            motion.defaultEffectsSpec(), label = "selected day")
                        val foreground by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            motion.defaultEffectsSpec(), label = "selected day text")
                        val description = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) + ", " +
                            if (known || daily.isNotEmpty()) stringResource(R.string.lesson_count, daily.size) else stringResource(R.string.unknown_day)
                        Surface(onClick = { if (day != selected) feedback?.select(); onDay(day) }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                            .semantics { contentDescription = description; this.selected = active },
                            shape = dayShape,
                            color = background, contentColor = foreground) {
                            Column(Modifier.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text(day.dayOfWeek.getDisplayName(if (narrow) TextStyle.NARROW else TextStyle.SHORT, locale).removeSuffix("."),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(if (known || daily.isNotEmpty()) daily.size.toString() else "—", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
