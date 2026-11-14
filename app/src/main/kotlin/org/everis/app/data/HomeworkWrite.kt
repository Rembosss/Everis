package org.everis.app.data

import org.everis.core.Lesson
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class HomeworkWrite(val groupId: String, val disciplineId: String, val subject: String,
    val sourceId: String?, val target: Lesson?, val due: LocalDate, val text: String,
    val expectedTargetText: String, val expectedSourceText: String?)

/** Validate everything first. Runs inside one authenticated repository transaction. */
fun applyHomeworkWrite(json: JSONObject, write: HomeworkWrite) {
    require(json.selectedGroup()?.id == write.groupId)
    require(write.text.length <= 20_000)
    val tasks = json.optJSONObject("tasks") ?: JSONObject()
    val metadata = json.optJSONObject("taskMetadata") ?: JSONObject()
    val completed = json.completedTasks().toMutableSet()
    write.sourceId?.let { source ->
        require(tasks.optString(source) == write.expectedSourceText.orEmpty()) { "The source task changed while editing" }
        metadata.optJSONObject(source)?.let { require(it.optString("group") == write.groupId) }
    }
    if (write.text.isBlank()) {
        val source = requireNotNull(write.sourceId)
        tasks.remove(source); metadata.remove(source); completed.remove(source)
    } else {
        require(write.disciplineId.isNotBlank() && write.subject.isNotBlank() && write.due.year in 1900..2200)
        val target = write.target
        if (target != null) {
            val group = requireNotNull(json.selectedGroup())
            require(target.disciplineId == write.disciplineId)
            require(parseStudentSchedule(json.getString("schedule"), group).lessons.any {
                it.id == target.id && it.disciplineId == target.disciplineId && it.disciplineName == target.disciplineName && !it.cancelled
            }) { "The target lesson is no longer available" }
        }
        val id = target?.id ?: write.sourceId?.takeIf { it.startsWith("manual:") } ?: "manual:${UUID.randomUUID()}"
        require(tasks.optString(id) == write.expectedTargetText) { "The target task changed while editing" }
        write.sourceId?.takeIf { it != id }?.let { source -> tasks.remove(source); metadata.remove(source); completed.remove(source) }
        if (tasks.optString(id) != write.text) completed.remove(id)
        tasks.put(id, write.text)
        if (target == null) metadata.put(id, JSONObject().put("subject", write.subject).put("disciplineId", write.disciplineId)
            .put("due", write.due.atTime(23, 59, 59).atZone(RsueData.zone).toString()).put("group", write.groupId)
            .put("manual", true).put("subgroup", json.optString("subgroup")))
    }
    json.put("tasks", tasks).put("taskMetadata", metadata).put("completedTasks", JSONArray(completed.toList()))
    if (write.text.isNotBlank() && write.target != null) rememberTaskMetadata(json, listOf(write.target), force = true)
}

fun completeHomeworkBatch(json: JSONObject, groupId: String, expected: Map<String, String>) {
    require(expected.isNotEmpty() && json.selectedGroup()?.id == groupId)
    val available = json.homeworkEntries().associateBy { it.id }
    require(expected.all { (id, text) -> available[id]?.text == text && available[id]?.complete == false })
    json.put("completedTasks", JSONArray((json.completedTasks() + expected.keys).toList()))
}
