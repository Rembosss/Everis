package org.everis.app

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Both contours use the same absolute radius. A percentage shape would be
    // clamped differently for the whole strip and its narrow day cells at high DPI.
    val dayShape = RoundedCornerShape(20.dp.coerceAtMost(maxWidth / 14))
    val compact = maxWidth < 300.dp || configuration.fontScale > 1.3f
    val smallStyle = MaterialTheme.typography.labelSmall.let {
        if (compact) it.copy(fontSize = 10.sp, lineHeight = 14.sp) else it
    }
    Surface(shape = dayShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        AnimatedContent(start, modifier = Modifier.fillMaxWidth().clipToBounds(), label = "week strip", transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            if (efficient) EnterTransition.None togetherWith ExitTransition.None else
                slideInHorizontally(androidx.compose.animation.core.tween(320)) { it * direction } togetherWith
                slideOutHorizontally(androidx.compose.animation.core.tween(320)) { -it * direction }
        }) { week ->
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    repeat(7) { offset ->
                        val day = week.plusDays(offset.toLong())
                        val daily = remember(day, lessons) { lessonsForDay(day, RsueData.zone, lessons) }
                        val known = dayCoverage(day, published, daily, RsueData.zone).complete
                        val active = day == selected
                        val selectedFraction by animateFloatAsState(if (active) 1f else 0f,
                            motion.defaultEffectsSpec(), label = "selected day")
                        val background = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary, selectedFraction)
                        val foreground = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onPrimary, selectedFraction)
                        val description = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) + ", " +
                            if (known || daily.isNotEmpty()) stringResource(R.string.lesson_count, daily.size) else stringResource(R.string.unknown_day)
                        Surface(onClick = { if (day != selected) feedback?.select(); onDay(day) }, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 60.dp)
                            .semantics { contentDescription = description; role = Role.Tab; this.selected = active },
                            shape = dayShape,
                            color = background, contentColor = foreground) {
                            Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".").take(2),
                                    style = smallStyle, maxLines = 1)
                                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(if (known || daily.isNotEmpty()) daily.size.toString() else "—", style = smallStyle, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
