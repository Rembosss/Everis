package org.everis.app

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.everis.app.data.RsueData
import org.everis.core.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun CalendarScreen(diary: DiaryViewModel) {
    val clock = rememberLiveInstant(60_000)
    val today by remember { derivedStateOf { clock.value.atZone(RsueData.zone).toLocalDate() } }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var anchorText by rememberSaveable { mutableStateOf(LocalDate.now(RsueData.zone).toString()) }
    val anchor = LocalDate.parse(anchorText)
    val locale = uiLocale(LocalConfiguration.current)
    val efficient = LocalPerformanceMode.current
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EverisSelect(mode, listOf(MenuChoice(0, stringResource(R.string.calendar_week)), MenuChoice(1, stringResource(R.string.calendar_month))),
            { mode = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val previous = stringResource(if (mode == 0) R.string.previous_week else R.string.previous_month)
            val next = stringResource(if (mode == 0) R.string.next_week else R.string.next_month)
            EverisIconButton(onClick = { anchorText = (if (mode == 0) anchor.minusWeeks(1) else anchor.minusMonths(1)).toString() }) {
                Icon(painterResource(R.drawable.ic_chevron_left), previous)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(mode to anchorText, label = "calendar heading", transitionSpec = {
                    headingTransition(calendarDirection(initialState.first, initialState.second, targetState.first, targetState.second), efficient)
                }) { (displayMode, dateText) ->
                    val date = LocalDate.parse(dateText)
                    val week = DatePeriod.weekContaining(date)
                    Text(if (displayMode == 0) "${week.first.format(DateTimeFormatter.ofPattern("d MMM", locale))} – ${week.last.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
                        else YearMonth.from(date).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                EverisTextButton(onClick = { anchorText = LocalDate.now(RsueData.zone).toString() }) { Text(stringResource(R.string.today)) }
            }
            EverisIconButton(onClick = { anchorText = (if (mode == 0) anchor.plusWeeks(1) else anchor.plusMonths(1)).toString() }) {
                Icon(painterResource(R.drawable.ic_chevron_right), next)
            }
        }
        AnimatedContent(targetState = mode to anchorText, modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(mode, anchorText) {
            var distance = 0f
            detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f }, onDragEnd = {
                if (kotlin.math.abs(distance) >= swipeThreshold) {
                    val step = if (distance < 0) 1L else -1L
                    anchorText = (if (mode == 0) anchor.plusWeeks(step) else anchor.plusMonths(step)).toString()
                }
            }) { change, amount -> change.consume(); distance += amount }
        }, label = "calendar period",
            transitionSpec = { screenTransition(calendarDirection(initialState.first, initialState.second, targetState.first, targetState.second), efficient) }) { (displayMode, displayAnchor) ->
            val date = LocalDate.parse(displayAnchor)
            val range = remember(displayMode, date) { if (displayMode == 0) DatePeriod.weekContaining(date) else DatePeriod.month(YearMonth.from(date)) }
            val allLessons = diary.state.schedule?.lessons.orEmpty()
            val published = diary.state.schedule?.days.orEmpty()
            val days = remember(range) { generateSequence(range.first) { it.plusDays(1) }.takeWhile { it <= range.last }.toList() }
            val monthLessons = remember(range, allLessons) {
                val first = range.first.minusDays((range.first.dayOfWeek.value - 1).toLong())
                (0L..41L).associate { offset -> val day = first.plusDays(offset); day to lessonsForDay(day, RsueData.zone, allLessons) }
            }
            val knownDays = remember(range, allLessons, published) { monthLessons.keys.associateWith { dayCoverage(it, published, allLessons, RsueData.zone).complete } }
            val complete = remember(range, allLessons, published) { days.all { dayCoverage(it, published, allLessons, RsueData.zone).complete } }
            val summary = remember(range, allLessons, published) { summarizePeriod(range, RsueData.zone, allLessons, Coverage(range.first, range.last, complete)) }
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).immediateListEdge(listState), state = listState, overscrollEffect = null, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = LocalNavigationClearance.current + 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("period-totals") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.lesson_count, summary.lessonCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(LessonType.LECTURE to R.string.lecture_count, LessonType.PRACTICE to R.string.practice_count, LessonType.LABORATORY to R.string.lab_count).forEach { (type, label) ->
                                    val colors = lessonColors(type)
                                    Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.small) {
                                        Text(stringResource(label, summary.countsByType[type] ?: 0), Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(stringResource(R.string.study_total, compactDuration(summary.teaching)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            if (!summary.complete) Text(stringResource(R.string.partial_calendar), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (displayMode == 0) {
                    items(days, key = { it.toString() }) { day ->
                        val lessons = remember(day, allLessons) { lessonsForDay(day, RsueData.zone, allLessons) }
                        val coverage = remember(day, published, allLessons) { dayCoverage(day, published, allLessons, RsueData.zone) }
                        val daily = remember(day, allLessons, coverage) { summarizeDay(day, RsueData.zone, lessons, coverage) }
                        Card(onClick = { diary.requestDay(day) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.width(68.dp)) {
                                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelLarge)
                                    Text(day.format(DateTimeFormatter.ofPattern("d MMM", locale)), style = MaterialTheme.typography.bodySmall)
                                }
                                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    lessons.forEachIndexed { index, lesson ->
                                        val description = "${index + 1}. ${lesson.disciplineName}, ${stringResource(lessonTypeLabel(lesson.type))}, ${lesson.start.toLocalTime()}"
                                        val colors = lessonColors(lesson.type)
                                        Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.width(36.dp).heightIn(min = 40.dp).semantics { contentDescription = description }) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                                                Text(stringResource(lessonAbbreviation(lesson.type)), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    if (lessons.isEmpty()) Text(if (coverage.complete) "—" else "?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (coverage.complete || lessons.isNotEmpty()) compactDuration(daily.teaching) else "—", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    item("weekdays") {
                        Row(Modifier.fillMaxWidth()) {
                            DayOfWeek.values().forEach { weekday ->
                                Text(weekday.getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val firstCell = range.first.minusDays((range.first.dayOfWeek.value - 1).toLong())
                    val rows = ((range.last.toEpochDay() - firstCell.toEpochDay() + 1 + 6) / 7).toInt()
                    items(rows) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(7) { column ->
                                val day = firstCell.plusDays((row * 7 + column).toLong())
                                val lessons = monthLessons[day].orEmpty()
                                val known = knownDays[day] == true
                                val currentMonth = day.month == range.first.month
                                val description = day.format(DateTimeFormatter.ofPattern("d MMMM", locale)) + ", " +
                                    if (known) stringResource(R.string.lesson_count, lessons.size) else stringResource(R.string.unknown_day)
                                Surface(onClick = { diary.requestDay(day) }, modifier = Modifier.weight(1f).heightIn(min = 72.dp).semantics { contentDescription = description },
                                    color = if (day == today) MaterialTheme.colorScheme.primaryContainer else if (currentMonth) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                                    shape = MaterialTheme.shapes.large) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall,
                                            color = if (currentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                                        Surface(shape = MaterialTheme.shapes.small,
                                            color = if (lessons.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                                            Text(if (known || lessons.isNotEmpty()) lessons.size.toString() else "—", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (lessons.isNotEmpty()) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

