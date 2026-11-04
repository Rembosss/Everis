package org.everis.app.data

import org.everis.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID

data class HomeworkEntry(val id: String, val disciplineId: String, val subject: String, val text: String,
    val due: ZonedDateTime, val lessonId: String?, val complete: Boolean)
data class ScheduleChange(val before: Lesson?, val after: Lesson?)
data class ScheduleHistoryEntry(val at: Instant, val changes: List<ScheduleChange>)

/** Upgrade 0.0.2 review flags without changing encryption or discarding the original metadata. */
fun JSONObject.detachLegacyReviewTasks(): Boolean {
    val tasks = optJSONObject("tasks") ?: return false
    val metadata = optJSONObject("taskMetadata") ?: return false
    val completed = completedTasks().toMutableSet()
    val flagged = tasks.keys().asSequence().filter { it.startsWith("rsue:") && metadata.optJSONObject(it)?.optBoolean("needsReview") == true }.toList()
    flagged.forEach { old ->
        val id = "review:${UUID.randomUUID()}"
        tasks.put(id, tasks.getString(old)); tasks.remove(old)
        metadata.put(id, metadata.getJSONObject(old)); metadata.remove(old)
        if (completed.remove(old)) completed.add(id)
    }
    if (flagged.isNotEmpty()) put("completedTasks", JSONArray(completed.toList()))
    return flagged.isNotEmpty()
}

/** Detach before reusing an occurrence ID for a different subject. Nothing is deleted. */
fun JSONObject.archiveHomeworkForReview(lesson: Lesson) {
    val tasks = optJSONObject("tasks") ?: return
    val text = tasks.optString(lesson.id)
    if (text.isBlank()) return
    val metadata = optJSONObject("taskMetadata") ?: JSONObject()
    val original = metadata.optJSONObject(lesson.id) ?: JSONObject()
    val id = "review:${UUID.randomUUID()}"
    val subject = original.optString("previousSubject", original.optString("subject")).ifBlank { lesson.disciplineName }
    val item = JSONObject(original.toString()).put("subject", subject).put("previousSubject", subject)
        .put("disciplineId", original.optString("disciplineId").ifBlank { lesson.disciplineId })
        .put("due", original.optString("due").ifBlank { lesson.start.toString() }).put("group", selectedGroup()?.id)
        .put("needsReview", true)
    if (!item.has("subgroup")) item.put("subgroup", lesson.subgroup)
    tasks.put(id, text); tasks.remove(lesson.id)
    metadata.put(id, item); metadata.remove(lesson.id)
    val completed = completedTasks().toMutableSet()
    if (completed.remove(lesson.id)) completed.add(id)
    put("tasks", tasks).put("taskMetadata", metadata).put("completedTasks", JSONArray(completed.toList()))
}

/** Still protects old tasks after switching away/back, when the old schedule cache is gone. */
fun JSONObject.protectHomeworkBindings(lessons: List<Lesson>) {
    detachLegacyReviewTasks()
    val metadata = optJSONObject("taskMetadata") ?: return
    lessons.forEach { fresh ->
        val old = metadata.optJSONObject(fresh.id) ?: return@forEach
        val id = old.optString("disciplineId")
        val name = old.optString("subject")
        if ((id.isNotBlank() && id != fresh.disciplineId) || (name.isNotBlank() && name != fresh.disciplineName)) archiveHomeworkForReview(fresh)
    }
}

fun JSONObject.homeworkEntries(): List<HomeworkEntry> {
    val group = selectedGroup() ?: return emptyList()
    val metadata = optJSONObject("taskMetadata") ?: JSONObject()
    val completed = completedTasks()
    val subgroup = visibleSubgroup().takeIf { it.isNotBlank() }
    return tasks().mapNotNull { (id, text) ->
        val item = metadata.optJSONObject(id) ?: return@mapNotNull null
        if (text.isBlank() || item.optBoolean("needsReview") || item.optString("group") != group.id) return@mapNotNull null
        val taskSubgroup = item.optString("subgroup").takeIf { it.isNotBlank() }
        if (subgroup != null && taskSubgroup != null && subgroup != taskSubgroup) return@mapNotNull null
        runCatching { HomeworkEntry(id, item.getString("disciplineId"), item.getString("subject"), text,
            ZonedDateTime.parse(item.getString("due")), id.takeUnless { item.optBoolean("manual") }, id in completed) }.getOrNull()
    }.sortedBy { it.due }
}

fun Lesson.toJson() = JSONObject().put("id", id).put("disciplineId", disciplineId).put("subject", disciplineName)
    .put("start", start.toString()).put("end", end.toString()).put("type", type.name)
    .put("teachers", JSONArray(teachers)).put("building", building).put("room", room).put("subgroup", subgroup)

internal fun lessonFromJson(item: JSONObject): Lesson = Lesson(item.getString("id"), item.getString("disciplineId"), item.getString("subject"),
    ZonedDateTime.parse(item.getString("start")), ZonedDateTime.parse(item.getString("end")), LessonType.valueOf(item.getString("type")),
    item.optJSONArray("teachers")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
    item.optString("building").takeIf { it.isNotBlank() }, item.optString("room").takeIf { it.isNotBlank() },
    subgroup = item.optString("subgroup").takeIf { it.isNotBlank() })

fun JSONObject.scheduleHistory(): List<ScheduleHistoryEntry> {
    val history = optJSONArray("history") ?: return emptyList()
    val group = selectedGroup()?.id ?: return emptyList()
    return (0 until history.length()).mapNotNull { i -> runCatching {
        val item = history.getJSONObject(i)
        if (item.optString("group") != group) return@mapNotNull null
        val changes = item.getJSONArray("changes")
        ScheduleHistoryEntry(Instant.parse(item.getString("at")), (0 until changes.length()).map { ci ->
            val change = changes.getJSONObject(ci)
            ScheduleChange(change.optJSONObject("before")?.let(::lessonFromJson), change.optJSONObject("after")?.let(::lessonFromJson))
        })
    }.getOrNull() }.reversed()
}

/** Matches IDs first, then unique slots; ignores regenerated source IDs and teacher order. */
fun scheduleChanges(previous: PublishedSchedule, incoming: PublishedSchedule): List<ScheduleChange> {
    val old = previous.lessons.filter { it.start.toLocalDate() in incoming.days }.toMutableList()
    val changes = mutableListOf<ScheduleChange>()
    fun same(a: Lesson, b: Lesson) = a.copy(id = b.id, teachers = a.teachers.distinct().sorted()) == b.copy(teachers = b.teachers.distinct().sorted())
    for (fresh in incoming.lessons) {
        val exact = old.indexOfFirst { same(it, fresh) }
        if (exact >= 0) { old.removeAt(exact); continue }
        val byId = old.indexOfFirst { it.id == fresh.id }
        val slots = old.withIndex().filter { it.value.start == fresh.start && it.value.subgroup == fresh.subgroup }
        val match = if (byId >= 0) byId else slots.singleOrNull()?.index ?: -1
        changes += ScheduleChange(if (match >= 0) old.removeAt(match) else null, fresh)
    }
    changes += old.map { ScheduleChange(it, null) }
    return changes
}

fun JSONObject.appendHistory(previous: PublishedSchedule, incoming: PublishedSchedule) {
    val changes = scheduleChanges(previous, incoming)
    if (changes.isEmpty()) return
    val history = optJSONArray("history") ?: JSONArray()
    val next = JSONArray()
    for (i in (history.length() - 19).coerceAtLeast(0) until history.length()) next.put(history.getJSONObject(i))
    next.put(JSONObject().put("at", Instant.now().toString()).put("group", selectedGroup()?.id)
        .put("changes", JSONArray().apply { changes.take(100).forEach { change ->
            put(JSONObject().put("before", change.before?.toJson()).put("after", change.after?.toJson()))
        } }))
    put("history", next)
}
