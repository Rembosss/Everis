package org.everis.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.everis.core.NotificationPreferences
import org.everis.core.publishedScheduleChanged
import org.everis.core.forSubgroup
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Single process transaction boundary shared by UI and WorkManager. Never writes stale full snapshots. */
class DiaryRepository private constructor(context: Context) {
    private val store = LocalDiary(context.applicationContext)
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private val mutable = MutableStateFlow<String?>(null)
    val snapshots = mutable.asStateFlow()

    suspend fun read(): JSONObject = mutex.withLock {
        withContext(Dispatchers.IO) {
            val raw = mutable.value ?: store.load().let { value ->
                if (value.detachLegacyReviewTasks()) store.save(value)
                value.toString().also { mutable.value = it }
            }
            JSONObject(raw)
        }
    }

    suspend fun update(transform: (JSONObject) -> Unit): JSONObject = mutex.withLock {
        withContext(Dispatchers.IO) {
            val original = mutable.value ?: store.load().toString()
            val value = JSONObject(original)
            transform(value)
            // Publish only after authenticated AtomicFile write has completed successfully.
            val encoded = value.toString()
            if (encoded != original) { store.save(value); mutable.value = encoded }
            JSONObject(encoded)
        }
    }

    suspend fun applySchedule(group: StudentGroup, raw: String): RefreshOutcome {
        val incoming = withContext(Dispatchers.Default) { RsueData.parseSchedule(raw, group.name) }
        var outcome = RefreshOutcome.STALE
        update { json ->
            if (json.selectedGroup() != group) return@update
            val oldRaw = json.optString("schedule")
            val previous = oldRaw.takeIf { it.isNotBlank() }?.let { RsueData.parseSchedule(it, group.name) }
            val subgroup = json.visibleSubgroup()
            val changed = previous != null && publishedScheduleChanged(previous.lessons.forSubgroup(subgroup), incoming.lessons.forSubgroup(subgroup), incoming.days)
            outcome = if (previous == null) RefreshOutcome.INITIAL else if (changed) RefreshOutcome.CHANGED else RefreshOutcome.UNCHANGED
            if (changed) {
                json.put("changeCounter", json.optLong("changeCounter") + 1)
                json.appendHistory(previous.forSubgroup(subgroup), incoming.forSubgroup(subgroup))
            }
            rememberTaskMetadata(json, previous?.lessons.orEmpty())
            val currentById = incoming.lessons.associateBy { it.id }
            previous?.lessons?.forEach { old ->
                val fresh = currentById[old.id]
                if ((fresh != null && (fresh.disciplineId != old.disciplineId || fresh.disciplineName != old.disciplineName || fresh.cancelled)) ||
                    (fresh == null && old.start.toLocalDate() in incoming.days)) {
                    json.archiveHomeworkForReview(old)
                }
            }
            json.protectHomeworkBindings(incoming.lessons)
            json.put("schedule", raw).put("lastVerified", Instant.now().toString())
            rememberTaskMetadata(json, incoming.lessons)
        }
        return outcome
    }

    suspend fun refresh(group: StudentGroup): RefreshOutcome = refreshMutex.withLock {
        if (read().selectedGroup() != group) return@withLock RefreshOutcome.STALE
        val raw = withContext(Dispatchers.IO) { RsueData().schedule(group) }
        applySchedule(group, raw)
    }

    companion object {
        private val instances = mutableMapOf<String, DiaryRepository>()
        @Synchronized fun get(context: Context): DiaryRepository = instances.getOrPut(context.packageName) { DiaryRepository(context) }
    }
}

enum class RefreshOutcome { INITIAL, CHANGED, UNCHANGED, STALE }

fun notificationPreferences(json: JSONObject): NotificationPreferences {
    val value = json.optJSONObject("notifications") ?: JSONObject()
    fun time(key: String, fallback: String) = runCatching { LocalTime.parse(value.optString(key, fallback)) }.getOrElse { LocalTime.parse(fallback) }
    return NotificationPreferences(
        value.optBoolean("periodicEnabled", true), listOf(120L, 240L, 360L, 480L, 600L, 720L).minBy { kotlin.math.abs(it - value.optLong("intervalMinutes", 120).coerceIn(15, 10080)) },
        value.optBoolean("changesEnabled", true), value.optBoolean("bedtimeEnabled", true), time("bedtime", "22:00"),
        value.optBoolean("homeworkEnabled", true), time("homeworkTime", "19:00"), listOf(1, 2, 3, 5, 7, 12, 14).minBy { kotlin.math.abs(it - value.optInt("daysAhead", 3).coerceIn(1, 30)) },
        value.optBoolean("dailyHomework"), value.optBoolean("onDueDay"),
        value.optLong("generation").coerceAtLeast(0),
    )
}

fun NotificationPreferences.toJson(): JSONObject = JSONObject()
    .put("periodicEnabled", periodicEnabled).put("intervalMinutes", intervalMinutes).put("changesEnabled", changesEnabled)
    .put("bedtimeEnabled", bedtimeEnabled).put("bedtime", bedtime.toString()).put("homeworkEnabled", homeworkEnabled)
    .put("homeworkTime", homeworkTime.toString()).put("daysAhead", daysAhead).put("dailyHomework", dailyHomework)
    .put("onDueDay", onDueDay).put("generation", generation)

fun JSONObject.selectedGroup(): StudentGroup? = optJSONObject("group")?.let { StudentGroup(it.getString("id"), it.getString("name")) }
fun JSONObject.tasks(): Map<String, String> = (optJSONObject("tasks") ?: JSONObject()).let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
fun JSONObject.completedTasks(): Set<String> = (optJSONArray("completedTasks") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
fun JSONObject.visibleSubgroup(): String = if (optBoolean("onlyMySubgroup")) optString("subgroup") else ""

fun rememberTaskMetadata(json: JSONObject, lessons: List<org.everis.core.Lesson>, force: Boolean = false) {
    val tasks = json.tasks()
    val metadata = json.optJSONObject("taskMetadata") ?: JSONObject()
    lessons.filter { !tasks[it.id].isNullOrBlank() }.forEach { lesson ->
        if (!force && metadata.optJSONObject(lesson.id)?.optBoolean("needsReview") == true) return@forEach
        metadata.put(lesson.id, JSONObject().put("subject", lesson.disciplineName).put("disciplineId", lesson.disciplineId)
            .put("due", lesson.start.toString()).put("group", json.selectedGroup()?.id.orEmpty()).put("subgroup", lesson.subgroup))
    }
    json.put("taskMetadata", metadata)
}
