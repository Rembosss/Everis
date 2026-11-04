package org.everis.app.data

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class HomeworkWriteTest {
    private val raw = javaClass.getResource("/rsue-lessons.json")!!.readText()
    private val lesson = RsueData.parseSchedule(raw, "МЕН-144").lessons.first()
    private fun snapshot() = JSONObject().put("group", JSONObject().put("id", "1").put("name", "МЕН-144"))
        .put("schedule", raw).put("tasks", JSONObject())
    private fun write(text: String = "new") = HomeworkWrite("1", lesson.disciplineId, lesson.disciplineName,
        null, lesson, lesson.start.toLocalDate(), text, "", null)
    private fun rejectWithoutMutation(json: JSONObject, action: () -> Unit) {
        val before = json.toString()
        assertTrue(runCatching(action).isFailure)
        assertEquals(before, json.toString())
    }
    @Test fun newHomeworkBindsToTheConfirmedSourceLesson() {
        val json = snapshot()
        applyHomeworkWrite(json, write())
        assertEquals("new", json.tasks()[lesson.id])
        assertEquals(lesson.disciplineId, json.homeworkEntries().single().disciplineId)
        assertEquals(lesson.start, json.homeworkEntries().single().due)
    }
    @Test fun editingCompletedHomeworkMakesChangedTextIncomplete() {
        val json = snapshot(); applyHomeworkWrite(json, write("before"))
        json.put("completedTasks", JSONArray(listOf(lesson.id)))
        applyHomeworkWrite(json, write("after").copy(sourceId = lesson.id, expectedSourceText = "before", expectedTargetText = "before"))
        assertFalse(lesson.id in json.completedTasks())
    }
    @Test fun unchangedCompletedTextPreservesTheCheckmark() {
        val json = snapshot(); applyHomeworkWrite(json, write("before"))
        json.put("completedTasks", JSONArray(listOf(lesson.id)))
        applyHomeworkWrite(json, write("before").copy(sourceId = lesson.id, expectedSourceText = "before", expectedTargetText = "before"))
        assertTrue(lesson.id in json.completedTasks())
    }
    @Test fun staleTargetCannotOverwriteANewerTask() {
        val json = snapshot(); applyHomeworkWrite(json, write("already saved"))
        rejectWithoutMutation(json) { applyHomeworkWrite(json, write("stale")) }
    }
    @Test fun aGroupChangeDuringEditingCannotWriteIntoTheNewGroup() {
        val json = snapshot()
        rejectWithoutMutation(json) { applyHomeworkWrite(json, write().copy(groupId = "other")) }
    }
    @Test fun wrongSubjectAtSameSourceIdIsRejectedBeforeAnyWrite() {
        val json = snapshot()
        rejectWithoutMutation(json) { applyHomeworkWrite(json, write().copy(target = lesson.copy(disciplineName = "Wrong subject"))) }
    }
    @Test fun movedHomeworkAndItsCompletionStateChangeAtomically() {
        val json = snapshot(); applyHomeworkWrite(json, write("old"))
        json.put("completedTasks", JSONArray(listOf(lesson.id)))
        applyHomeworkWrite(json, write("moved").copy(sourceId = lesson.id, target = null, expectedTargetText = "", expectedSourceText = "old"))
        val entry = json.homeworkEntries().single()
        assertTrue(entry.id.startsWith("manual:")); assertNull(entry.lessonId)
        assertEquals("moved", entry.text); assertEquals(23, entry.due.hour); assertEquals(59, entry.due.minute)
        assertFalse(json.tasks().containsKey(lesson.id)); assertFalse(lesson.id in json.completedTasks())
    }
    @Test fun deletingAnEditedTaskDoesNotDeleteTheOtherSelectedTargetsHomework() {
        val json = snapshot(); applyHomeworkWrite(json, write("keep"))
        val manual = write("delete me").copy(target = null)
        applyHomeworkWrite(json, manual)
        val id = json.tasks().keys.first { it.startsWith("manual:") }
        applyHomeworkWrite(json, write("").copy(sourceId = id, expectedSourceText = "delete me", expectedTargetText = "keep"))
        assertEquals(mapOf(lesson.id to "keep"), json.tasks())
    }
    @Test fun missingTargetLessonCannotAcceptHomework() {
        val json = snapshot()
        rejectWithoutMutation(json) { applyHomeworkWrite(json, write().copy(target = lesson.copy(id = "removed"))) }
    }
    @Test fun aNewBlankTaskCannotAccidentallyDeleteAnExistingTarget() {
        val json = snapshot(); applyHomeworkWrite(json, write("keep"))
        rejectWithoutMutation(json) { applyHomeworkWrite(json, write("").copy(expectedTargetText = "keep")) }
    }
}
