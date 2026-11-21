package org.everis.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.everis.app.data.RsueData
import org.everis.core.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val CENTER_DAY = 36525
private data class DailyHeading(val day: LocalDate, val group: String, val summary: DaySummary)

@Composable
fun DailySchedule(diary: DiaryViewModel) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val state = diary.state
    val zone = RsueData.zone
    val motion = MaterialTheme.motionScheme
    val locale = uiLocale(LocalConfiguration.current)
    val baseDate by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    val base = LocalDate.parse(baseDate)
    val pager = rememberPagerState(initialPage = CENTER_DAY, pageCount = { CENTER_DAY * 2 + 1 })
    val navigation = rememberDirectPagerNavigation(pager)
    // A programmatic week jump may skip intermediate pages. Headers follow the
    // destination, rather than restarting their animation for each skipped date.
    val selectedDay = base.plusDays((navigation.selectedPage - CENTER_DAY).toLong())
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var originalText by rememberSaveable { mutableStateOf("") }
    var lastEditedLesson by remember { mutableStateOf<Lesson?>(null) }
    var editingSubject by rememberSaveable { mutableStateOf("") }
    var editingDisciplineId by rememberSaveable { mutableStateOf("") }
    var recoveryDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryId by rememberSaveable { mutableStateOf("") }
    var lastSettledPage by rememberSaveable { mutableIntStateOf(pager.settledPage) }
    var lastGroupId by rememberSaveable { mutableStateOf(state.group?.id) }
    val dayClock = rememberLiveInstant(60_000)
    val today by remember { derivedStateOf { dayClock.value.atZone(zone).toLocalDate() } }
    val allLessons = state.schedule?.lessons.orEmpty()
    val publishedDays = state.schedule?.days.orEmpty()
    val selectedHeading = remember(selectedDay, state.group?.name, allLessons, publishedDays) {
        DailyHeading(selectedDay, state.group?.name.orEmpty(), summarizeDay(selectedDay, zone,
            allLessons, dayCoverage(selectedDay, publishedDays, allLessons, zone)))
    }
    val windowHeight = maxHeight
    val largeFont = LocalConfiguration.current.fontScale > 1.4f
    val compact = windowHeight < 620.dp || maxWidth < 360.dp || largeFont
    val tinyWindow = windowHeight < 460.dp
    val compactScroll = rememberScrollState()
    var showDailyTotals by rememberSaveable { mutableStateOf(false) }
    fun navigate(page: Int) {
        if (editingId != null) return
        navigation.navigate(page, state.performanceMode)
    }
    fun navigateDate(day: LocalDate) { navigate(CENTER_DAY + ChronoUnit.DAYS.between(base, day).toInt()) }
    fun switchWeek(offset: Long) { navigateDate(weekStart(selectedDay).plusWeeks(offset)) }
    OnSectionReset { if (!state.saving) { editingId = null; navigateDate(today) } }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != lastSettledPage) { editingId = null; lastSettledPage = pager.settledPage }
    }
    LaunchedEffect(state.group?.id) {
        if (state.group?.id != lastGroupId) { editingId = null; lastGroupId = state.group?.id }
    }
    LaunchedEffect(allLessons, editingId) {
        val id = editingId
        if (id != null && allLessons.none { it.id == id && it.disciplineId == editingDisciplineId && it.disciplineName == editingSubject && !it.cancelled }) {
            recoveryId = id; recoveryDraft = draft; editingId = null
        }
    }
    LaunchedEffect(state.requestedDay) {
        state.requestedDay?.let { requested ->
            editingId = null
            val offset = ChronoUnit.DAYS.between(base, LocalDate.parse(requested)).coerceIn(-CENTER_DAY.toLong(), CENTER_DAY.toLong())
            diary.consumeDayRequest()
            navigate(CENTER_DAY + offset.toInt())
        }
    }
    BackHandler(LocalScreenActive.current && editingId != null) { if (!state.saving) { editingId = null } }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().then(if (tinyWindow) Modifier.verticalScroll(compactScroll) else Modifier)) {
        AnimatedVisibility(true,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.defaultEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec())) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    AnimatedContent(selectedHeading, modifier = Modifier.fillMaxWidth().clipToBounds(), label = "date and daily totals", transitionSpec = {
                        headingTransition(if (targetState.day >= initialState.day) 1 else -1, state.performanceMode).using(
                            SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.tween(
                                if (state.performanceMode) 0 else 320, easing = androidx.compose.animation.core.FastOutSlowInEasing) })
                    }) { heading ->
                        val day = heading.day
                        val summary = heading.summary
                        Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (compact) {
                                    EverisTextButton(onClick = { showDailyTotals = true }, modifier = Modifier.weight(1f)) {
                                      Column(horizontalAlignment = Alignment.Start) {
                                            Text(day.format(DateTimeFormatter.ofPattern("d MMM", locale)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(heading.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                      }
                                        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.padding(start = 4.dp).size(18.dp))
                                    }
                                    EverisIconButton(onClick = { switchWeek(-1) }) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.previous_week)) }
                                    EverisIconButton(onClick = { switchWeek(1) }) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.next_week)) }
                                } else {
                                    Text(day.format(DateTimeFormatter.ofPattern("d MMMM", locale)), style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                                }
                            }
                            if (!compact) Text(day.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (!compact) Text(heading.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (!compact) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.study_total, compactDuration(summary.teaching)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.break_total, compactDuration(summary.breaks)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.overall_total, compactDuration(summary.teaching.plus(summary.breaks))), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.group?.university == "rgups") Text(stringResource(R.string.rgups_cycle_notice),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                        LiveStatus(allLessons, publishedDays)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val previous = stringResource(R.string.previous_week)
                        val next = stringResource(R.string.next_week)
                        EverisIconButton(onClick = { switchWeek(-1) }) {
                            Icon(painterResource(R.drawable.ic_chevron_left), previous)
                        }
                        val week = weekStart(selectedDay)
                        AnimatedContent(week, modifier = Modifier.clipToBounds(), label = "week range", transitionSpec = {
                            headingTransition(if (targetState > initialState) 1 else -1, state.performanceMode)
                        }) { displayedWeek ->
                            Text(displayedWeek.format(DateTimeFormatter.ofPattern("d MMM", locale)) + " – " + displayedWeek.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", locale)),
                                style = MaterialTheme.typography.labelLarge)
                        }
                        EverisIconButton(onClick = { switchWeek(1) }) {
                            Icon(painterResource(R.drawable.ic_chevron_right), next)
                        }
                    }
                    WeekStrip(selectedDay, allLessons, publishedDays, ::navigateDate)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        HorizontalPager(state = pager, modifier = if (tinyWindow) Modifier.height(windowHeight * .85f).fillMaxWidth() else Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = editingId == null && !navigation.bridging,
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = motion.defaultSpatialSpec())) { page ->
            val day = base.plusDays((navigation.contentPage(page) - CENTER_DAY).toLong())
            val lessons = remember(day, allLessons) { lessonsForDay(day, zone, allLessons) }
            val confirmed = remember(day, allLessons, publishedDays) { dayCoverage(day, publishedDays, allLessons, zone).complete }
            key(day) { DayPage(day, lessons, confirmed, diary, onEdit = { lesson ->
                lastEditedLesson = lesson; editingId = lesson.id; editingSubject = lesson.disciplineName
                editingDisciplineId = lesson.disciplineId; originalText = state.tasks[lesson.id].orEmpty(); draft = originalText
            }) }
        }
    }
    HomeworkEditorLayer(editingId != null, modifier = Modifier.matchParentSize()) {
        val lesson = allLessons.firstOrNull { it.id == editingId } ?: lastEditedLesson
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (lesson != null) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalNavigationClearance.current + 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item("lesson") { LessonCard(lesson, originalText, lesson.id in state.completedTasks, editing = true,
                    active = lesson.start.toLocalDate() == today && Instant.now() >= lesson.start.toInstant() && Instant.now() < lesson.end.toInstant(),
                    onNext = {}, onEdit = {}, onComplete = { diary.completeTask(lesson.id, it) }) }
                item("editor") {
                    HomeworkInput(draft, { draft = it.take(20_000) }, minLines = 5, maxLines = 10)
                }
                item("actions") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EverisTextButton(onClick = { editingId = null }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) }
                        if (originalText.isNotBlank()) EverisTextButton(onClick = {
                            diary.deleteHomework(lesson.id, originalText) { editingId = null }
                        }, enabled = !state.saving) { Text(stringResource(R.string.delete_task), color = MaterialTheme.colorScheme.error) }
                        EverisButton(onClick = {
                            diary.saveTask(lesson.copy(disciplineId = editingDisciplineId, disciplineName = editingSubject), draft,
                                onSaved = { editingId = null }, expectedText = originalText)
                        }, enabled = !state.saving && draft.isNotBlank() && lesson.disciplineId == editingDisciplineId && lesson.disciplineName == editingSubject) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
    AnimatedVisibility(selectedDay != today && editingId == null, modifier = Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalNavigationClearance.current + 12.dp),
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = .88f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = .88f)) {
        if (compact) FloatingActionButton(onClick = { navigateDate(today) }) {
            Icon(painterResource(R.drawable.ic_calendar_agenda), stringResource(R.string.today))
        } else ExtendedFloatingActionButton(onClick = { navigateDate(today) }) { Text(stringResource(R.string.today)) }
    }
    }
    if (showDailyTotals) {
        val summary = remember(selectedDay, allLessons, publishedDays) { summarizeDay(selectedDay, zone, allLessons, dayCoverage(selectedDay, publishedDays, allLessons, zone)) }
        EverisAlertDialog(onDismissRequest = { showDailyTotals = false }, title = { Text(selectedDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))) },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.study_total, compactDuration(summary.teaching)))
                Text(stringResource(R.string.break_total, compactDuration(summary.breaks)))
                Text(stringResource(R.string.overall_total, compactDuration(summary.span)))
                if (!summary.complete) Text(stringResource(R.string.unknown_day), style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { EverisTextButton(onClick = { showDailyTotals = false }) { Text(stringResource(R.string.ok)) } })
    }
    if (recoveryDraft != null) EverisAlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.editor_lesson_unavailable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.editor_recovery_info), style = MaterialTheme.typography.bodySmall)
                HomeworkInput(value = recoveryDraft.orEmpty(), onValueChange = { recoveryDraft = it.take(20_000) }, minLines = 3, maxLines = 8)
            }
        },
        confirmButton = { EverisTextButton(onClick = {
            diary.saveTaskForReview(recoveryId, editingSubject, recoveryDraft.orEmpty()) { recoveryDraft = null }
        }, enabled = !state.saving && !recoveryDraft.isNullOrBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { EverisTextButton(onClick = { recoveryDraft = null }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } })
  }
}

@Composable
private fun DayPage(day: LocalDate, lessons: List<Lesson>, confirmed: Boolean, diary: DiaryViewModel, onEdit: (Lesson) -> Unit) {
    val state = diary.state
    val motion = MaterialTheme.motionScheme
    val efficient = LocalPerformanceMode.current
    val listState = rememberLazyListState()
    val clock = rememberLiveInstant()
    val activeIds by remember(day, lessons) { derivedStateOf { activeLessonIds(day, clock.value, lessons, RsueData.zone) } }
    val manualTasks = remember(day, state.homework) { state.homework.filter { it.lessonId == null && it.due.toLocalDate() == day } }
    if (lessons.isEmpty() && manualTasks.isEmpty() && !state.scheduleLoading) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (confirmed) R.string.day_off else R.string.unknown_day),
                    style = if (confirmed) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = if (confirmed) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                if (state.scheduleFailed) Text(stringResource(R.string.schedule_error), color = MaterialTheme.colorScheme.error)
                if (!confirmed) EverisOutlinedButton(onClick = { diary.refreshSchedule() }) { Text(stringResource(R.string.refresh)) }
                val next = remember(day, state.schedule) { nextStudyDay(day, state.schedule?.lessons.orEmpty(), RsueData.zone) }
                if (next != null) EverisTonalButton(onClick = { diary.requestDay(next) }) { Text(stringResource(R.string.next_study_day)) }
            }
        }
    } else LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).immediateListEdge(listState), overscrollEffect = null,
        contentPadding = PaddingValues(top = 12.dp, bottom = LocalNavigationClearance.current + 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.scheduleLoading && lessons.isEmpty()) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.loading_schedule), style = MaterialTheme.typography.bodySmall) }
        if (state.scheduleFailed) item("error") { Text(stringResource(R.string.schedule_error), color = MaterialTheme.colorScheme.error) }
        items(lessons, key = { it.id }) { lesson ->
            Box(Modifier.animateItem(placementSpec = if (efficient) null else motion.defaultSpatialSpec())) {
                LessonCard(lesson, state.tasks[lesson.id].orEmpty(), lesson.id in state.completedTasks, editing = false,
                    active = lesson.id in activeIds, onNext = { diary.addHomeworkAfter(lesson) },
                    onEdit = { onEdit(lesson) }, onComplete = { diary.completeTask(lesson.id, it) })
            }
        }
        items(manualTasks, key = { it.id }) { task -> HomeworkRow(task, diary) }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, task: String, complete: Boolean, editing: Boolean, active: Boolean,
    onNext: () -> Unit, onEdit: () -> Unit, onComplete: (Boolean) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val activeLabel = stringResource(R.string.current_class)
    Card(Modifier.fillMaxWidth().semantics { if (active) stateDescription = activeLabel },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${lesson.start.toLocalTime()} – ${lesson.end.toLocalTime()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(lessonTypeLabel(lesson.type)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Text(lesson.disciplineName, style = MaterialTheme.typography.titleMedium.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto), fontWeight = FontWeight.SemiBold)
            Text(lesson.teachers.joinToString("\n").ifEmpty { stringResource(R.string.no_teacher) }, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                lesson.building?.let { Text(stringResource(R.string.building, it), style = MaterialTheme.typography.bodySmall) }
                lesson.room?.let { Text(stringResource(R.string.room, it), style = MaterialTheme.typography.bodySmall) }
                lesson.subgroup?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (task.isNotBlank() && !editing) {
                Text(task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = complete, onCheckedChange = onComplete)
                    Text(stringResource(R.string.task_complete), style = MaterialTheme.typography.bodySmall)
                }
            }
            AnimatedVisibility(!editing, enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.defaultEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec())) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = if (LocalConfiguration.current.fontScale > 1.3f) 1 else 2) {
                    EverisTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (task.isBlank()) R.string.add_task else R.string.edit_task), style = MaterialTheme.typography.labelMedium)
                    }
                    val nextLabel = stringResource(R.string.next_task)
                    EverisTextButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = nextLabel }) {
                        Text(stringResource(R.string.next_task_short), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Only this small component observes seconds; the pager, cards and summaries do not. */
@Composable
internal fun LiveStatus(lessons: List<Lesson>, publishedDays: Set<LocalDate>) {
    val now by rememberLiveInstant()
    val today = now.atZone(RsueData.zone).toLocalDate()
    val todayLessons = remember(today, lessons) { lessonsForDay(today, RsueData.zone, lessons) }
    val status = currentStatus(now, RsueData.zone, todayLessons, dayCoverage(today, publishedDays, todayLessons, RsueData.zone))
    val efficient = LocalPerformanceMode.current
    AnimatedContent(status, contentKey = { it::class }, label = "live study status", transitionSpec = {
        headingTransition(efficient = efficient)
    }) { displayed ->
        Text(statusText(displayed), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
    }
}

fun lessonTypeLabel(type: LessonType): Int = when (type) {
    LessonType.LECTURE -> R.string.lecture; LessonType.PRACTICE -> R.string.practice
    LessonType.LABORATORY -> R.string.laboratory; else -> R.string.other
}

@Composable
fun compactDuration(duration: Duration): String = stringResource(R.string.duration_compact, duration.toMinutes() / 60, duration.toMinutes() % 60)

private fun clock(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
private fun statusText(status: DayStatus): String = when (status) {
    DayStatus.Unavailable -> stringResource(R.string.unknown_day)
    DayStatus.DayOff -> stringResource(R.string.day_off)
    DayStatus.Finished -> stringResource(R.string.finished)
    is DayStatus.BeforeFirst -> stringResource(R.string.before_first, clock(status.remaining))
    is DayStatus.During -> stringResource(R.string.during, clock(status.untilNextEnd))
    is DayStatus.Break -> stringResource(R.string.in_break, clock(status.remaining))
}
