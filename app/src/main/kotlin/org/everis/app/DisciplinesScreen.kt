package org.everis.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.everis.app.data.HomeworkEntry
import org.everis.app.data.RsueData
import org.everis.app.data.disciplineKey
import org.everis.app.data.disciplineTitle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import org.everis.core.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun DisciplinesScreen(diary: DiaryViewModel) {
    val state = diary.state
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var homeworkOnly by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = remember(selection) { selection.toSet() }
    var lastSelectionCount by remember { mutableIntStateOf(0) }
    SideEffect { if (selected.isNotEmpty()) lastSelectionCount = selected.size }
    val courses = remember(state.schedule, state.homework, state.tasksForReview) {
        (state.schedule?.lessons.orEmpty().map { it.disciplineName } + state.homework.map { it.subject } + state.tasksForReview.map { it.subject })
            .map { disciplineKey(it) to disciplineTitle(it) }.distinctBy { it.first }.sortedBy { it.second }
    }
    val byDiscipline = remember(state.schedule) { state.schedule?.lessons.orEmpty().groupBy { disciplineKey(it.disciplineName) } }
    val incomplete = remember(state.homework) { state.homework.filterNot { it.complete } }
    LaunchedEffect(incomplete) { val ids = incomplete.map { it.id }.toSet(); selection = selection.filter { it in ids } }
    val motion = MaterialTheme.motionScheme
    val efficient = LocalPerformanceMode.current
    val feedback = LocalEverisHaptics.current
    BackHandler(expanded != null || selected.isNotEmpty()) { if (selected.isNotEmpty()) selection = emptyList() else expanded = null }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EverisPageHeading(stringResource(R.string.disciplines))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !homeworkOnly, onClick = { homeworkOnly = false; selection = emptyList() }, label = { Text(stringResource(R.string.all_subjects)) })
                    FilterChip(selected = homeworkOnly, onClick = { homeworkOnly = true }, label = { Text(stringResource(R.string.unfinished_tasks)) })
                    EverisTonalButton(onClick = diary::addHomework, enabled = courses.isNotEmpty()) { Text(stringResource(R.string.add_task)) }
                }
            }
            AnimatedContent(homeworkOnly, modifier = Modifier.weight(1f), label = "discipline filter", transitionSpec = { screenTransition(if (targetState) 1 else -1, efficient) }) { showHomework ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showHomework) {
                        if (incomplete.isEmpty()) item("empty-homework") { Text(stringResource(R.string.no_unfinished_tasks)) }
                        incomplete.groupBy { it.due.toLocalDate() }.forEach { (date, entries) ->
                            item("date-$date") { DateLabel(date) }
                            items(entries, key = { it.id }) { task ->
                                Box(Modifier.animateItem(placementSpec = if (efficient) null else motion.defaultSpatialSpec())) {
                                    HomeworkRow(task, diary, stagedSelection = task.id in selected, onSelect = { checked ->
                                        selection = if (checked) (selection + task.id).distinct() else selection - task.id
                                    })
                                }
                            }
                        }
                    } else {
                        if (courses.isEmpty()) item("empty-subjects") { Text(stringResource(R.string.no_disciplines)) }
                        items(courses, key = { it.first }) { (id, title) ->
                            val open = expanded == id
                            val angle by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec(), label = "discipline expand")
                            Card(Modifier.fillMaxWidth().animateItem(placementSpec = if (efficient) null else motion.defaultSpatialSpec()), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Surface(onClick = { feedback?.select(); expanded = if (open) null else id }, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.padding(start = 8.dp).rotate(angle))
                                    }
                                }
                                AnimatedVisibility(open, enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                                    exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())) {
                                    DisciplineDetails(id, title, byDiscipline[id].orEmpty(), diary)
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(homeworkOnly && selected.isNotEmpty(), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.defaultSpatialSpec()) { it / 2 }) {
            ExtendedFloatingActionButton(onClick = { if (!state.saving && selected.isNotEmpty()) diary.completeTasks(selected) { selection = emptyList() } }) {
                if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(painterResource(R.drawable.ic_check), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mark_done_count, if (selected.isNotEmpty()) selected.size else lastSelectionCount))
            }
        }
    }
}

@Composable
private fun DisciplineDetails(id: String, title: String, lessons: List<Lesson>, diary: DiaryViewModel) {
    val dayClock = rememberLiveInstant(60_000)
    val today by remember { derivedStateOf { dayClock.value.atZone(RsueData.zone).toLocalDate() } }
    val locale = uiLocale(LocalConfiguration.current)
    val week = remember(today) { DatePeriod.weekContaining(today) }
    val month = remember(today) { DatePeriod.month(YearMonth.from(today)) }
    val weekly = remember(lessons, week) { summarizePeriod(week, RsueData.zone, lessons, Coverage(week.first, week.last, true)) }
    val monthly = remember(lessons, month) { summarizePeriod(month, RsueData.zone, lessons, Coverage(month.first, month.last, true)) }
    val tasks = remember(diary.state.homework, id) { diary.state.homework.filter { disciplineKey(it.subject) == id } }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lessons.groupBy { it.subgroup.orEmpty() }.toSortedMap().forEach { (subgroup, subgroupLessons) ->
            if (subgroup.isNotBlank()) Text(stringResource(R.string.subgroup_teacher_heading, subgroup),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            subgroupLessons.groupBy { it.type }.forEach { (type, entries) ->
                val teachers = entries.flatMap { it.teachers }.distinct()
                if (teachers.isNotEmpty()) Text(stringResource(lessonTypeLabel(type)) + ": " + teachers.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.week_load, weekly.lessonCount, compactDuration(weekly.teaching)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.month_load, monthly.lessonCount, compactDuration(monthly.teaching)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.loaded_schedule_stats), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EverisTonalButton(onClick = { diary.addHomeworkForDiscipline(id, title) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_task)) }
        Text(stringResource(R.string.homework_title), style = MaterialTheme.typography.titleSmall)
        if (tasks.isEmpty()) Text(stringResource(R.string.no_homework), style = MaterialTheme.typography.bodySmall)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.groupBy { it.due.toLocalDate() }.forEach { (day, entries) ->
                item("date-$day") { DateLabel(day) }
                items(entries, key = { it.id }) { task -> HomeworkRow(task, diary, showSubject = false) }
            }
        }
        val review = diary.state.tasksForReview.filter { disciplineKey(it.subject) == id }
        if (review.isNotEmpty()) {
            Text(stringResource(R.string.tasks_need_review), style = MaterialTheme.typography.titleSmall)
            review.forEach { task ->
                Text(task.text, style = MaterialTheme.typography.bodySmall)
                EverisTextButton(onClick = { diary.reviewHomework(task) }) { Text(stringResource(R.string.move_homework)) }
            }
        }
        Text(stringResource(R.string.lessons_list), style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lessons, key = { it.id }) { lesson ->
                Surface(onClick = { diary.requestDay(lesson.start.toLocalDate()) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lesson.start.format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", locale)) + "–" + lesson.end.toLocalTime(), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(lessonTypeLabel(lesson.type)), style = MaterialTheme.typography.bodySmall)
                        Text(lesson.teachers.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        Text(listOfNotNull(lesson.building, lesson.room, lesson.subgroup).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DateLabel(date: LocalDate) {
    val locale = uiLocale(LocalConfiguration.current)
    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun HomeworkRow(task: HomeworkEntry, diary: DiaryViewModel, showSubject: Boolean = true, stagedSelection: Boolean? = null, onSelect: ((Boolean) -> Unit)? = null) {
    val clock = rememberLiveInstant(60_000)
    val overdue by remember(task.due, task.complete) { derivedStateOf { !task.complete && task.due.toInstant() < clock.value } }
    Card(onClick = { diary.editHomework(task) }, modifier = Modifier.fillMaxWidth(),
        border = if (stagedSelection == true) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = stagedSelection ?: task.complete, onCheckedChange = { if (onSelect != null) onSelect(it) else diary.completeTask(task.id, it) }, enabled = !diary.state.saving)
            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showSubject) Text(disciplineTitle(task.subject), style = MaterialTheme.typography.titleSmall)
                Text(task.text, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                if (overdue) Text(stringResource(R.string.overdue), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
