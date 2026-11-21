package org.everis.app

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.everis.app.data.*
import org.everis.app.notifications.NotificationScheduler
import org.everis.app.notifications.EverisNotifications
import org.everis.core.Lesson
import org.everis.core.NotificationPreferences
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

/** Saved state is owned by DiaryRepository; UI flags never enter background-worker transactions. */
data class DiaryState(
    val loaded: Boolean = false,
    val storageFailed: Boolean = false,
    val groups: List<StudentGroup> = emptyList(),
    val groupsLoading: Boolean = false,
    val groupsFailed: Boolean = false,
    val catalogUniversity: String = "rsue",
    val group: StudentGroup? = null,
    val completed: Boolean = false,
    val acknowledged: Boolean = false,
    val permissionAsked: Boolean = false,
    val schedule: PublishedSchedule? = null,
    val scheduleLoading: Boolean = false,
    val scheduleFailed: Boolean = false,
    val tasks: Map<String, String> = emptyMap(),
    val completedTasks: Set<String> = emptySet(),
    val palette: String = "dynamic",
    val themeMode: String = "system",
    val blackTheme: Boolean = false,
    val performanceMode: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val newDock: Boolean = true,
    val onlyMySubgroup: Boolean = false,
    val subgroup: String = "",
    val subgroups: List<String> = emptyList(),
    val previewGroupId: String? = null,
    val previewSubgroups: List<String> = emptyList(),
    val groupPreviewLoading: Boolean = false,
    val groupPreviewFailed: Boolean = false,
    val homework: List<HomeworkEntry> = emptyList(),
    val history: List<ScheduleHistoryEntry> = emptyList(),
    val languageRequest: String? = null,
    val homeworkRequest: HomeworkRequest? = null,
    val notifications: NotificationPreferences = NotificationPreferences(),
    val changingGroup: Boolean = false,
    val requestedDay: String? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val backgroundFailed: Boolean = false,
    val tasksForReview: List<ReviewTask> = emptyList(),
    val disciplineCourses: List<Pair<String, String>> = emptyList(),
    val disciplineLessons: Map<String, List<Lesson>> = emptyMap(),
    val teacherNames: Map<String, String> = emptyMap(),
)
data class ReviewTask(val id: String, val subject: String, val text: String, val disciplineId: String)
data class HomeworkRequest(val disciplineId: String, val subject: String, val after: Instant,
    val initialLessonId: String? = null, val editTaskId: String? = null)
data class ImportedSetup(val group: StudentGroup, val subgroup: String)

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val updateChecker = org.everis.app.updates.UpdateChecker(application)
    var appUpdate by mutableStateOf<org.everis.app.updates.AppUpdate?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var manualUpdateResult by mutableStateOf<Boolean?>(null)
        private set
    private var automaticUpdateRunning = false
    private var manualUpdateRequested = false

    fun checkAppUpdate(manual: Boolean = false) {
        if (checkingUpdate) return
        if (automaticUpdateRunning) {
            if (manual) { manualUpdateRequested = true; checkingUpdate = true; manualUpdateResult = null }
            return
        }
        manualUpdateRequested = manual
        if (manual) { checkingUpdate = true; manualUpdateResult = null } else automaticUpdateRunning = true
        viewModelScope.launch {
            try {
                var result = updateChecker.check(manual)
                if (result == org.everis.app.updates.UpdateResult.Skipped && manualUpdateRequested) result = updateChecker.check(true)
                when (result) {
                    is org.everis.app.updates.UpdateResult.Available -> appUpdate = result.update
                    org.everis.app.updates.UpdateResult.Current -> if (manualUpdateRequested) manualUpdateResult = true
                    org.everis.app.updates.UpdateResult.Failed -> if (manualUpdateRequested) manualUpdateResult = false
                    org.everis.app.updates.UpdateResult.Skipped -> Unit
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (manualUpdateRequested) manualUpdateResult = false }
            finally { checkingUpdate = false; automaticUpdateRunning = false; manualUpdateRequested = false }
        }
    }
    fun dismissAppUpdate() { appUpdate = null }
    fun consumeManualUpdateResult() { manualUpdateResult = null }

    var languageOverlayText: String? = null
        private set
    fun freezeLanguageMessage(text: String) { if (languageOverlayText == null) languageOverlayText = text }
    val launchTimeMillis = SystemClock.elapsedRealtime()
    var state by mutableStateOf(DiaryState())
        private set
    var backupBusy by mutableStateOf(false)
        private set
    var backupResult by mutableStateOf<Boolean?>(null)
        private set
    var legacyBackupUri by mutableStateOf<Uri?>(null)
        private set
    var importedSetup by mutableStateOf<ImportedSetup?>(null)
        private set
    private var previewJob: kotlinx.coroutines.Job? = null
    private var previewRaw: String? = null
    private var previewGroup: StudentGroup? = null
    private val context get() = getApplication<Application>()
    private val repository = DiaryRepository.get(application)
    val teacherRepository = org.everis.app.data.TeacherRepository(application)
    private var groupsJob: kotlinx.coroutines.Job? = null
    private var groupQuery: Pair<String, String>? = null
    private var catalogChosen = false
    private var writes = 0
    private var refreshing = false
    private var pendingRefreshGroup: StudentGroup? = null
    // Preserve object identity as well as CPU time when only a task/setting changes.
    private var cachedGroupsRaw = ""
    private var cachedGroups = emptyList<StudentGroup>()
    private var cachedScheduleRaw = ""
    private var cachedScheduleGroup: String? = null
    private var cachedSchedule: PublishedSchedule? = null
    private var cachedSubgroup = ""
    private var cachedVisible: PublishedSchedule? = null
    private var indexedSchedule: PublishedSchedule? = null
    private var indexedCourses = emptyList<Pair<String, String>>()
    private var indexedLessons = emptyMap<String, List<Lesson>>()
    private var indexedTeachers = emptyList<String>()
    private val preferenceEdits = PreferenceEdits()
    private var lastCommittedState: DiaryState? = null
    private var lastObservedRaw: String? = null

    init {
        viewModelScope.launch {
            repository.snapshots.filterNotNull().collectLatest { raw ->
                try {
                    val saved = withContext(Dispatchers.Default) {
                        val json = JSONObject(raw)
                        val array = json.optJSONArray("groups") ?: JSONArray()
                        val groupsRaw = array.toString()
                        if (groupsRaw != cachedGroupsRaw) {
                            cachedGroups = (0 until array.length()).map { i -> studentGroup(array.getJSONObject(i)) }
                            cachedGroupsRaw = groupsRaw
                        }
                        val groups = cachedGroups
                        val group = json.selectedGroup()
                        val metadata = json.optJSONObject("taskMetadata") ?: JSONObject()
                        val tasksForReview = json.tasks().mapNotNull { (id, text) ->
                            val item = metadata.optJSONObject(id)
                            if (item?.optBoolean("needsReview") == true && item.optString("group") == group?.id && text.isNotBlank())
                                ReviewTask(id, item.optString("previousSubject", item.optString("subject")), text,
                                    item.optString("disciplineId", item.optString("subject").lowercase())) else null
                        }
                        val rawSchedule = json.optString("schedule")
                        val subgroup = json.visibleSubgroup()
                        if (rawSchedule != cachedScheduleRaw || group?.let { "${it.university}:${it.id}:${it.name}" } != cachedScheduleGroup) {
                            cachedSchedule = rawSchedule.takeIf { it.isNotBlank() && group != null }?.let { parseStudentSchedule(it, group!!) }
                            cachedScheduleRaw = rawSchedule; cachedScheduleGroup = group?.let { "${it.university}:${it.id}:${it.name}" }
                            cachedVisible = cachedSchedule?.forSubgroup(subgroup)
                            cachedSubgroup = subgroup
                        } else if (subgroup != cachedSubgroup) {
                            cachedVisible = cachedSchedule?.forSubgroup(subgroup); cachedSubgroup = subgroup
                        }
                        val homework = json.homeworkEntries()
                        val visible = cachedVisible
                        val lessons = visible?.lessons.orEmpty()
                        if (visible !== indexedSchedule || (visible == null && indexedLessons.isNotEmpty())) {
                            indexedLessons = lessons.groupBy { disciplineKey(it.disciplineName) }
                            indexedCourses = lessons.map { disciplineKey(it.disciplineName) to disciplineTitle(it.disciplineName) }.distinctBy { it.first }
                            indexedTeachers = lessons.flatMap { it.teachers }.distinct()
                            indexedSchedule = visible
                        }
                        val byDiscipline = indexedLessons
                        val additionalCourses = (homework.map { it.subject } + tasksForReview.map { it.subject })
                            .map { disciplineKey(it) to disciplineTitle(it) }
                        val courses = (indexedCourses + additionalCourses).distinctBy { it.first }.sortedBy { it.second }
                        val university = group?.university ?: "rsue"
                        val names = indexedTeachers.associateWith { teacherRepository.fullName(it, university) }
                        DiaryState(loaded = true, groups = groups, group = group,
                            disciplineCourses = courses, disciplineLessons = byDiscipline, teacherNames = names,
                            completed = json.optBoolean("completed") && group != null, acknowledged = json.optBoolean("betaAcknowledged"), permissionAsked = json.optBoolean("notificationPermissionAsked"),
                            schedule = cachedVisible, tasks = json.tasks(), completedTasks = json.completedTasks(),
                            palette = json.optString("palette", "dynamic"), themeMode = json.optString("themeMode", "system"),
                            blackTheme = json.optBoolean("blackTheme"), performanceMode = json.optBoolean("performanceMode"), hapticsEnabled = json.optBoolean("hapticsEnabled", true),
                            newDock = json.optBoolean("newDock", true),
                            onlyMySubgroup = json.optBoolean("onlyMySubgroup"), subgroup = json.optString("subgroup"),
                            subgroups = cachedSchedule?.lessons.orEmpty().mapNotNull { it.subgroup }.distinct().sorted(),
                            homework = homework, history = json.scheduleHistory(),
                            notifications = notificationPreferences(json), tasksForReview = tasksForReview)
                    }
                    lastCommittedState = saved
                    lastObservedRaw = raw
                    state = preferenceEdits.reconcile(saved.copy(groupsLoading = state.groupsLoading, groupsFailed = state.groupsFailed,
                        catalogUniversity = if (catalogChosen) state.catalogUniversity else saved.group?.university ?: "rsue",
                        scheduleLoading = state.scheduleLoading, scheduleFailed = state.scheduleFailed,
                        changingGroup = state.changingGroup, requestedDay = state.requestedDay,
                        languageRequest = state.languageRequest, homeworkRequest = state.homeworkRequest,
                        previewGroupId = state.previewGroupId, previewSubgroups = state.previewSubgroups,
                        groupPreviewLoading = state.groupPreviewLoading, groupPreviewFailed = state.groupPreviewFailed,
                        saving = writes > 0, saveFailed = state.saveFailed, backgroundFailed = state.backgroundFailed),
                        latest = raw == repository.snapshots.value)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { state = state.copy(loaded = true, storageFailed = true) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val saved = repository.read()
                saved.selectedGroup()?.let { warmTeacherNames(it.university) }
                Log.i("Everis", "Protected snapshot loaded")
                // Silent preload begins on the welcome screen, independently from its animations.
                if (!saved.optBoolean("completed")) loadGroups() else refreshSchedule(silent = saved.optString("schedule").isNotBlank())
                configureNotifications(saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state = state.copy(loaded = true, storageFailed = true)
                Log.e("Everis", "Protected storage unavailable; original data preserved")
            }
        }
    }

    private fun warmTeacherNames(university: String) {
        if (university != "dstu") return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { teacherRepository.refreshCatalog(university) } }
            refreshTeacherNames()
        }
    }

    fun refreshTeacherNames() {
        val snapshot = state
        viewModelScope.launch {
            val names = withContext(Dispatchers.Default) {
                snapshot.schedule?.lessons.orEmpty().flatMap { it.teachers }.distinct()
                    .associateWith { teacherRepository.fullName(it, snapshot.group?.university ?: "rsue") }
            }
            if (state.group == snapshot.group && state.schedule === snapshot.schedule) state = state.copy(teacherNames = names)
        }
    }

    fun selectUniversity(university: String) {
        require(university in UniversitySources.universities)
        if (state.catalogUniversity == university && state.groups.any { it.university == university }) return
        catalogChosen = true
        groupsJob?.cancel()
        previewJob?.cancel(); previewRaw = null; previewGroup = null
        state = state.copy(catalogUniversity = university, groupsLoading = false, groupsFailed = false,
            previewGroupId = null, previewSubgroups = emptyList(), groupPreviewLoading = false, groupPreviewFailed = false)
        loadGroups()
    }

    fun loadGroups() {
        if (state.groupsLoading || state.storageFailed) return
        requestGroups("", false)
    }

    fun searchGroups(query: String) {
        if (!UniversitySources.remoteSearch(state.catalogUniversity) || state.storageFailed) return
        val normalized = query.trim().take(80)
        if (groupQuery == (state.catalogUniversity to normalized) && !state.groupsFailed) return
        requestGroups(normalized, true)
    }

    private fun requestGroups(query: String, debounce: Boolean) {
        val university = state.catalogUniversity
        groupQuery = university to query
        groupsJob?.cancel()
        state = state.copy(groupsLoading = true, groupsFailed = false)
        groupsJob = viewModelScope.launch {
            try {
                if (debounce) kotlinx.coroutines.delay(350)
                val groups = withContext(Dispatchers.IO) { UniversitySources.groups(university, query) }
                repository.update { json ->
                    val previous = json.optJSONArray("groups") ?: JSONArray()
                    val retained = (0 until previous.length()).map { studentGroup(previous.getJSONObject(it)) }
                        .filter { UniversitySources.remoteSearch(university) || it.university != university }
                    val selected = json.selectedGroup()
                    val directory = (listOfNotNull(selected) + groups + retained).distinctBy { it.id }.take(30_000)
                    json.put("groups", JSONArray().apply { directory.forEach { put(it.toJson()) } })
                }
                if (state.catalogUniversity == university) state = state.copy(groupsLoading = false)
                Log.i("Everis", "Public $university directory loaded: ${groups.size} groups")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (state.catalogUniversity == university) state = state.copy(groupsLoading = false, groupsFailed = true) }
        }
    }

    fun previewGroup(group: StudentGroup) {
        if (state.group == group && cachedSchedule != null) {
            previewJob?.cancel(); previewGroup = group; previewRaw = cachedScheduleRaw
            state = state.copy(previewGroupId = group.id, previewSubgroups = state.subgroups, groupPreviewLoading = false, groupPreviewFailed = false)
            return
        }
        if (state.previewGroupId == group.id && (state.groupPreviewLoading || previewRaw != null)) return
        previewJob?.cancel()
        previewRaw = null; previewGroup = group
        state = state.copy(previewGroupId = group.id, previewSubgroups = emptyList(), groupPreviewLoading = true, groupPreviewFailed = false)
        previewJob = viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { UniversitySources.schedule(group) }
                val schedule = withContext(Dispatchers.Default) { parseStudentSchedule(raw, group) }
                if (previewGroup == group) {
                    previewRaw = raw
                    state = state.copy(previewSubgroups = schedule.lessons.mapNotNull { it.subgroup }.distinct().sorted(), groupPreviewLoading = false)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (previewGroup == group) state = state.copy(groupPreviewLoading = false, groupPreviewFailed = true) }
        }
    }

    fun chooseGroup(group: StudentGroup, subgroup: String = "", onSelected: () -> Unit = {}) {
        if (group !in state.groups) return
        require(subgroup.isBlank() || subgroup in state.previewSubgroups || subgroup in state.subgroups)
        val cached = if (state.group == group && cachedSchedule != null) cachedScheduleRaw else previewRaw.takeIf { previewGroup == group }
        mutate({ json ->
            if (json.selectedGroup() != group) {
                val old = json.optString("schedule")
                json.selectedGroup()?.let { previous ->
                    if (old.isNotBlank()) rememberTaskMetadata(json, parseStudentSchedule(old, previous).lessons)
                }
                json.put("schedule", "").put("subgroup", "").put("onlyMySubgroup", false).put("changeCounter", 0).put("bedtimeReportedCounter", 0)
                val preferences = notificationPreferences(json)
                json.put("notifications", preferences.copy(generation = preferences.generation + 1).toJson())
            }
            json.put("group", group.toJson())
            json.put("subgroup", subgroup)
        }) {
            if (cached != null) repository.applySchedule(group, cached)
            val changing = state.changingGroup
            state = state.copy(changingGroup = false, homeworkRequest = null)
            onSelected()
            if (changing && cached == null) refreshSchedule(group)
            warmTeacherNames(group.university)
            configureNotifications(repository.read())
        }
    }

    fun finishSetup() {
        mutate({ json ->
            require(json.selectedGroup() != null)
            if (!json.optBoolean("completed")) {
                val preferences = notificationPreferences(json)
                json.put("notifications", preferences.copy(generation = preferences.generation + 1).toJson())
            }
            json.put("completed", true)
        }) {
            if (repository.read().optString("schedule").isBlank()) refreshSchedule()
            configureNotifications(repository.read())
        }
    }
    fun acknowledge() = mutate({ it.put("acknowledged", true).put("betaAcknowledged", true) })
    fun markPermissionAsked() = mutate({ it.put("notificationPermissionAsked", true) })
    fun setPalette(value: String) {
        require(value == "dynamic" || Regex("#[0-9a-fA-F]{6}").matches(value))
        if (value == state.palette) return
        mutate({ it.put("palette", value) }, optimistic = { it.copy(palette = value) })
    }
    fun setTheme(value: String) { require(value in listOf("system", "light", "dark")); mutate({
        it.put("themeMode", value); if (value == "light") it.put("blackTheme", false)
    }, optimistic = { it.copy(themeMode = value, blackTheme = it.blackTheme && value != "light") }) }
    fun setBlackTheme(value: Boolean) = mutate({ it.put("blackTheme", value) }, optimistic = { it.copy(blackTheme = value) })
    fun setHaptics(value: Boolean) = mutate({ it.put("hapticsEnabled", value) }, optimistic = { it.copy(hapticsEnabled = value) })
    fun setNewDock(value: Boolean) = mutate({ it.put("newDock", value) }, optimistic = { it.copy(newDock = value) })
    fun setPerformanceMode(value: Boolean) = mutate({ it.put("performanceMode", value) }, optimistic = { it.copy(performanceMode = value) })
    fun setOnlyMySubgroup(value: Boolean) {
        require(!value || state.subgroup.isNotBlank())
        mutate({ json ->
            json.put("onlyMySubgroup", value)
            val current = notificationPreferences(json)
            json.put("notifications", current.copy(generation = current.generation + 1).toJson())
        }, optimistic = { it.copy(onlyMySubgroup = value) }) { configureNotifications(repository.read(), replaceDaily = true); configureHomework(repository.read()) }
    }
    fun setLanguage(code: String) {
        require(appLanguages.any { it.code == code })
        if (state.languageRequest != null) return
        state = state.copy(languageRequest = code, scheduleFailed = false)
        mutate({ it.put("language", code) })
    }
    fun finishLanguageChange() { state = state.copy(languageRequest = null); languageOverlayText = null; refreshSchedule(silent = true) }
    fun setSubgroup(value: String) {
        require(value.isEmpty() || value in state.subgroups)
        mutate({ json ->
            json.put("subgroup", value); if (value.isBlank()) json.put("onlyMySubgroup", false)
            val current = notificationPreferences(json)
            json.put("notifications", current.copy(generation = current.generation + 1).toJson())
        }, optimistic = { it.copy(subgroup = value, onlyMySubgroup = it.onlyMySubgroup && value.isNotBlank()) }) {
            configureNotifications(repository.read(), replaceDaily = true); configureHomework(repository.read())
        }
    }
    fun addHomeworkAfter(lesson: Lesson) {
        state = state.copy(homeworkRequest = HomeworkRequest(lesson.disciplineId, lesson.disciplineName, lesson.end.toInstant()))
    }
    fun addHomeworkForDiscipline(id: String, subject: String) {
        state = state.copy(homeworkRequest = HomeworkRequest(id, subject, Instant.now()))
    }
    fun addHomework() { state = state.copy(homeworkRequest = HomeworkRequest("", "", Instant.now())) }
    fun editHomework(entry: HomeworkEntry) {
        state = state.copy(homeworkRequest = HomeworkRequest(entry.disciplineId, entry.subject, Instant.EPOCH, entry.lessonId, entry.id))
    }
    fun reviewHomework(task: ReviewTask) {
        state = state.copy(homeworkRequest = HomeworkRequest(task.disciplineId, task.subject, Instant.now(), editTaskId = task.id))
    }
    fun closeHomework() { if (!state.saving) state = state.copy(homeworkRequest = null) }
    fun homeworkSaved() { state = state.copy(homeworkRequest = null) }
    fun changeGroup() { state = state.copy(changingGroup = true); selectUniversity(state.group?.university ?: "rsue"); loadGroups() }
    fun cancelGroupChange() { state = state.copy(changingGroup = false) }
    fun requestDay(day: LocalDate) { state = state.copy(requestedDay = day.toString()) }
    fun consumeDayRequest() { state = state.copy(requestedDay = null) }

    fun updateNotifications(transform: (NotificationPreferences) -> NotificationPreferences) {
        mutate({ json ->
            val current = notificationPreferences(json)
            json.put("notifications", transform(current).copy(generation = current.generation + 1).toJson())
        }, optimistic = { it.copy(notifications = transform(it.notifications)) }) { configureNotifications(repository.read(), replaceDaily = true) }
    }

    fun refreshSchedule(explicitGroup: StudentGroup? = null, silent: Boolean = false) {
        if (state.storageFailed) return
        if (refreshing) { if (explicitGroup != null) pendingRefreshGroup = explicitGroup; return }
        refreshing = true
        if (!silent) state = state.copy(scheduleLoading = true, scheduleFailed = false)
        viewModelScope.launch {
            try {
                val group = explicitGroup ?: repository.read().selectedGroup()
                if (group != null) {
                    val outcome = repository.refresh(group)
                    if (outcome == RefreshOutcome.CHANGED) {
                        try { EverisNotifications(context).scheduleChanged(repository.read()) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { state = state.copy(backgroundFailed = true) }
                    }
                    Log.i("Everis", "Public schedule refresh completed: $outcome")
                }
                state = state.copy(scheduleLoading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!silent) state = state.copy(scheduleLoading = false, scheduleFailed = true) }
            finally {
                refreshing = false
                pendingRefreshGroup?.let { group -> pendingRefreshGroup = null; refreshSchedule(group) }
            }
        }
    }

    fun saveTask(lesson: Lesson, text: String, onSaved: () -> Unit, expectedText: String? = null) {
        val group = state.group?.id ?: return
        val expected = expectedText ?: state.tasks[lesson.id].orEmpty()
        val write = HomeworkWrite(group, lesson.disciplineId, lesson.disciplineName, lesson.id, lesson, lesson.start.toLocalDate(), text, expected, expected)
        mutate({ json -> applyHomeworkWrite(json, write) }) { onSaved(); configureHomework(repository.read()) }
    }

    fun saveHomework(request: HomeworkRequest, target: Lesson?, due: LocalDate, text: String, expectedTargetText: String, expectedSourceText: String?, onSaved: () -> Unit) {
        val expectedGroup = state.group?.id ?: return
        val write = HomeworkWrite(expectedGroup, target?.disciplineId ?: request.disciplineId, target?.disciplineName ?: request.subject, request.editTaskId, target, due, text, expectedTargetText, expectedSourceText)
        mutate({ json -> applyHomeworkWrite(json, write) }) { onSaved(); configureHomework(repository.read()) }
    }

    fun completeTask(id: String, complete: Boolean) {
        mutate({ json ->
            val ids = json.completedTasks().toMutableSet()
            if (complete) ids.add(id) else ids.remove(id)
            json.put("completedTasks", JSONArray(ids.toList()))
        }) { configureHomework(repository.read()) }
    }
    fun completeTasks(ids: Set<String>, onSaved: () -> Unit) {
        val group = state.group?.id ?: return
        val expected = state.homework.filter { it.id in ids && !it.complete }.associate { it.id to it.text }
        mutate({ json ->
            require(expected.size == ids.size)
            completeHomeworkBatch(json, group, expected)
        }) { onSaved(); configureHomework(repository.read()) }
    }
    fun deleteHomework(id: String, expectedText: String, onSaved: () -> Unit) {
        val group = state.group?.id ?: return
        mutate({ json ->
            applyHomeworkWrite(json, HomeworkWrite(group, "", "", id, null, LocalDate.now(), "", "", expectedText))
        }) { onSaved(); configureHomework(repository.read()) }
    }

    fun saveTaskForReview(id: String, subject: String, text: String, onSaved: () -> Unit) {
        val reviewId = id.takeIf { it.startsWith("review:") } ?: "review:${UUID.randomUUID()}"
        mutate({ json ->
            val tasks = json.optJSONObject("tasks") ?: JSONObject()
            tasks.put(reviewId, text.take(20_000)); json.put("tasks", tasks)
            val metadata = json.optJSONObject("taskMetadata") ?: JSONObject()
            metadata.put(reviewId, JSONObject().put("subject", subject).put("disciplineId", subject.lowercase()).put("group", json.selectedGroup()?.id.orEmpty()).put("needsReview", true))
            json.put("taskMetadata", metadata)
        }) { onSaved(); configureHomework(repository.read()) }
    }

    fun clearSaveError() { state = state.copy(saveFailed = false) }

    fun clearBackupResult() { if (!backupBusy) backupResult = null }
    fun dismissLegacyBackup() { legacyBackupUri = null }
    fun consumeSetupImport() { importedSetup = null }
    fun runBackup(uri: Uri, restore: Boolean, scheduleOnly: Boolean = false, legacyPassword: CharArray? = null) {
        if (backupBusy || state.storageFailed || (restore && state.saving)) { legacyPassword?.fill('\u0000'); return }
        backupBusy = true; backupResult = null
        viewModelScope.launch {
            try {
                val manager = BackupManager(context)
                if (restore) {
                    manager.restore(uri, scheduleOnly, legacyPassword)
                    legacyBackupUri = null
                    state = state.copy(homeworkRequest = null, changingGroup = false)
                    val saved = repository.read()
                    configureNotifications(saved, replaceDaily = true); configureHomework(saved)
                    saved.optString("language").takeIf { code -> appLanguages.any { it.code == code } && code != uiLocale(context.resources.configuration).language }?.let {
                        state = state.copy(languageRequest = it)
                    }
                    if (scheduleOnly && !saved.optBoolean("completed")) {
                        importedSetup = ImportedSetup(requireNotNull(saved.selectedGroup()), saved.optString("subgroup"))
                    }
                } else manager.export(uri, scheduleOnly)
                backupResult = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LegacyBackupPasswordRequired) { legacyBackupUri = uri }
            catch (_: Exception) { backupResult = false; Log.w("Everis", "Backup operation failed; no credentials logged") }
            finally { legacyPassword?.fill('\u0000'); backupBusy = false }
        }
    }

    private fun configureNotifications(saved: JSONObject, replaceDaily: Boolean = false) {
        try { NotificationScheduler.configure(context, saved, replaceDaily); state = state.copy(backgroundFailed = false) }
        catch (_: Exception) { state = state.copy(backgroundFailed = true); Log.w("Everis", "Background scheduling unavailable") }
    }
    private fun configureHomework(saved: JSONObject) {
        try { NotificationScheduler.homeworkChanged(context, saved) }
        catch (_: Exception) { state = state.copy(backgroundFailed = true) }
    }

    private fun mutate(change: (JSONObject) -> Unit, optimistic: ((DiaryState) -> DiaryState)? = null,
        after: suspend () -> Unit = {}) {
        if (state.storageFailed) return
        val edit = optimistic?.let(preferenceEdits::begin)
        if (edit != null) state = preferenceEdits.preview(state)
        writes++
        state = state.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            var persisted = false
            try { repository.update(change); persisted = true; after() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state = state.copy(saveFailed = true)
                Log.e("Everis", "Local change failed; original snapshot preserved")
            } finally {
                if (edit != null) {
                    preferenceEdits.complete(edit, persisted)
                    lastCommittedState?.let { saved ->
                        val confirmed = preferenceEdits.reconcile(saved, latest = lastObservedRaw == repository.snapshots.value)
                        state = state.withPreferencesFrom(confirmed)
                    }
                }
                writes--; state = state.copy(saving = writes > 0)
            }
        }
    }
}
