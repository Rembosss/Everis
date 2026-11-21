package org.everis.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.everis.app.data.*
import org.everis.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun TimetableScreen(diary: DiaryViewModel) {
    val university = diary.state.group?.university ?: "rsue"
    var searching by rememberSaveable(university) { mutableStateOf(false) }
    var selectedId by rememberSaveable(university) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(university) { mutableStateOf("") }
    // rememberSaveable inputs do not validate state restored after a tab was disposed.
    var routeUniversity by rememberSaveable { mutableStateOf(university) }
    val currentSource = routeUniversity == university
    LaunchedEffect(university) {
        if (routeUniversity != university) {
            selectedId = null; searching = false; query = ""; routeUniversity = university
        }
    }
    var retry by remember { mutableIntStateOf(0) }
    var teachers by remember(university) { mutableStateOf(emptyList<TeacherRef>()) }
    var loading by remember(university) { mutableStateOf(false) }
    var failed by remember(university) { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    val active = LocalScreenActive.current
    fun back() { if (selectedId != null) selectedId = null else searching = false }
    OnSectionReset { selectedId = null; searching = false }
    BackHandler(active && searching && currentSource) { back() }
    LaunchedEffect(searching, university, retry, if (org.everis.app.data.UniversitySources.remoteSearch(university)) query.trim() else "") {
        if (!searching || !currentSource) return@LaunchedEffect
        loading = true; failed = false
        teachers = withContext(Dispatchers.IO) { diary.teacherRepository.cachedCatalog(university) }
        try {
            val remote = org.everis.app.data.UniversitySources.remoteSearch(university)
            if (remote && query.isNotBlank()) delay(350)
            teachers = withContext(Dispatchers.IO) {
                if (remote) diary.teacherRepository.searchCatalog(university, query)
                else diary.teacherRepository.refreshCatalog(university, force = retry > 0)
            }
            diary.refreshTeacherNames()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val teacherSupported = org.everis.app.data.UniversitySources.supportsTeacherTimetable(university)
    val route = if (!searching || !currentSource || !teacherSupported) "calendar" else selectedId?.let { "teacher:$it" } ?: "search"
    AnimatedContent(route, label = "timetable destination", modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background),
        transitionSpec = { screenTransition(if (targetState == "calendar" || (targetState == "search" && initialState.startsWith("teacher:"))) -1 else 1, diary.state.performanceMode) }) { page ->
      pages.SaveableStateProvider(if (page.startsWith("teacher:")) "$university:$page" else page) {
        when {
            page == "calendar" -> Column(Modifier.fillMaxSize()) {
                if (teacherSupported) EverisTonalButton(onClick = { searching = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Icon(painterResource(R.drawable.ic_disciplines), null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.teacher_timetable), style = if (LocalConfiguration.current.fontScale > 1.3f) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.weight(1f)) { CalendarScreen(diary) }
            }
            page == "search" -> TeacherSearch(university, query, { query = it }, teachers, loading, failed,
                onRetry = { retry++ }, onTeacher = { selectedId = it.id }, onBack = ::back)
            else -> {
                val teacher = teachers.firstOrNull { it.id == page.removePrefix("teacher:") }
                if (teacher == null) Column(Modifier.padding(16.dp)) {
                    EverisPageHeading(stringResource(R.string.teacher_timetable), ::back)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else EverisTextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.select_teacher)) }
                } else key(teacher.university, teacher.id) { TeacherDetail(diary, teacher, ::back) }
            }
        }
      }
    }
}

@Composable
private fun TeacherSearch(university: String, query: String, onQuery: (String) -> Unit,
    teachers: List<TeacherRef>, loading: Boolean, failed: Boolean, onRetry: () -> Unit, onTeacher: (TeacherRef) -> Unit, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    val found by produceState(emptyList<TeacherRef>(), query, teachers) {
        delay(100)
        value = withContext(Dispatchers.Default) {
            fun fold(value: String) = value.lowercase().replace('ё', 'е').filterNot { it.isWhitespace() || it == '.' }
            val text = fold(query)
            if (text.isBlank()) emptyList() else teachers.filter { fold(it.name).contains(text) || fold(it.sourceName).contains(text) }.take(60)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EverisPageHeading(stringResource(R.string.teacher_timetable), onBack)
        Text(stringResource(R.string.university_source, universityName(university)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { onQuery(it.take(180)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = EverisInputShape, colors = everisFieldColors(),
            label = { Text(stringResource(R.string.teacher_name_hint)) })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (teachers.isEmpty()) R.string.teacher_load_error else R.string.teacher_cached_directory), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            EverisTextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        if (query.isBlank()) Text(stringResource(R.string.teacher_search_help), style = MaterialTheme.typography.bodyMedium)
        else if (found.isEmpty() && !loading) Text(stringResource(R.string.teacher_not_found), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = LocalNavigationClearance.current + 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(found, key = { it.id }) { teacher ->
                Surface(onClick = { focus.clearFocus(); onTeacher(teacher) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                        if (teacher.name != teacher.sourceName) Text(teacher.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private data class TeacherLoad(val schedule: PublishedSchedule? = null, val loading: Boolean = true, val failed: Boolean = false)

@Composable
private fun TeacherDetail(diary: DiaryViewModel, teacher: TeacherRef, onBack: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState(TeacherLoad(), teacher, retry) {
        value = TeacherLoad(withContext(Dispatchers.IO) { diary.teacherRepository.cachedSchedule(teacher) })
        try { value = TeacherLoad(withContext(Dispatchers.IO) { diary.teacherRepository.refreshSchedule(teacher) }, loading = false) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = value.copy(loading = false, failed = true) }
    }
    TeacherDays(teacher, state.schedule, state.loading, state.failed, onRetry = { retry++ }, onBack = onBack)
}

@Composable
private fun TeacherDays(teacher: TeacherRef, schedule: PublishedSchedule?, loading: Boolean, failed: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val efficient = LocalPerformanceMode.current
    val locale = uiLocale(LocalConfiguration.current)
    val now = rememberLiveInstant(1000)
    val today by remember { derivedStateOf { now.value.atZone(RsueData.zone).toLocalDate() } }
    val baseText by rememberSaveable { mutableStateOf(today.toString()) }
    val base = LocalDate.parse(baseText)
    val center = 36525
    val pager = rememberPagerState(initialPage = center, pageCount = { center * 2 + 1 })
    val navigation = rememberDirectPagerNavigation(pager)
    val selected = base.plusDays((navigation.selectedPage - center).toLong())
    val lessons = schedule?.lessons.orEmpty()
    fun show(day: LocalDate) { navigation.navigate((center + ChronoUnit.DAYS.between(base, day)).toInt(), efficient) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerLimit = (maxHeight * .6f).coerceAtLeast(180.dp)
        Column(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).heightIn(max = headerLimit), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EverisIconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.back)) }
                        AnimatedContent(selected, modifier = Modifier.weight(1f).clipToBounds(), label = "teacher date", transitionSpec = { headingTransition(if (targetState >= initialState) 1 else -1, efficient) }) { date ->
                            Text(date.format(DateTimeFormatter.ofPattern("d MMMM · EEEE", locale)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                    if (schedule != null) LiveStatus(lessons, schedule.days)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (failed) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (schedule == null) R.string.teacher_load_error else R.string.teacher_cached_schedule), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        EverisTextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EverisIconButton(onClick = { show(weekStart(selected).minusWeeks(1)) }) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.previous_week)) }
                        Text(weekStart(selected).format(DateTimeFormatter.ofPattern("d MMM", locale)) + " – " + weekStart(selected).plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", locale)), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        EverisIconButton(onClick = { show(weekStart(selected).plusWeeks(1)) }) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.next_week)) }
                    }
                    WeekStrip(selected, lessons, schedule?.days.orEmpty(), ::show)
                }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f), userScrollEnabled = !navigation.bridging) { page ->
                val date = base.plusDays((navigation.contentPage(page) - center).toLong())
                val daily = remember(date, lessons) { lessonsForDay(date, RsueData.zone, lessons) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = LocalNavigationClearance.current + 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (daily.isEmpty() && !loading) item {
                        Box(Modifier.fillParentMaxWidth().heightIn(min = 140.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(if (schedule != null && dayCoverage(date, schedule.days, lessons, RsueData.zone).complete) R.string.day_off else R.string.no_published_lessons), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    items(daily, key = { it.id }) { lesson ->
                        val running = date == today && now.value >= lesson.start.toInstant() && now.value < lesson.end.toInstant()
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (running) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${lesson.start.toLocalTime()} – ${lesson.end.toLocalTime()} · " + stringResource(lessonTypeLabel(lesson.type)), style = MaterialTheme.typography.labelLarge)
                                Text(lesson.disciplineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (lesson.studentGroups.isNotEmpty()) Text(lesson.studentGroups.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                                Text(listOfNotNull(lesson.building, lesson.room, lesson.subgroup).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(selected != today, Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalNavigationClearance.current + 12.dp), enter = fadeIn(), exit = fadeOut()) {
            FloatingActionButton(onClick = { show(today) }) { Icon(painterResource(R.drawable.ic_calendar_agenda), stringResource(R.string.today)) }
        }
    }
}
