package org.everis.app.data

import org.everis.core.Lesson
import org.everis.core.LessonType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class DiaryExtrasTest {
    private val start = ZonedDateTime.parse("2026-10-29T10:10:00+03:00[Europe/Moscow]")
    private val lesson = Lesson("rsue:A-1:one", "math", "Математика", start, start.plusMinutes(90), LessonType.PRACTICE, listOf("Иванов", "Петров"), room = "210")
    private fun schedule(lessons: List<Lesson>) = PublishedSchedule("A-1", setOf(start.toLocalDate()), lessons)
    private fun snapshot() = JSONObject().put("group", JSONObject().put("id", "1").put("name", "A-1"))

    @Test fun regeneratedIdsAndTeacherOrderDoNotCreateHistory() {
        val changedId = lesson.copy(id = "regenerated", teachers = lesson.teachers.reversed())
        assertTrue(scheduleChanges(schedule(listOf(lesson)), schedule(listOf(changedId))).isEmpty())
    }
    @Test fun uniqueSlotMatchesEvenWhenSubjectAndIdChange() {
        val changed = lesson.copy(id = "new", disciplineId = "physics", disciplineName = "Физика")
        val diff = scheduleChanges(schedule(listOf(lesson)), schedule(listOf(changed)))
        assertEquals(1, diff.size); assertEquals(lesson, diff.single().before); assertEquals(changed, diff.single().after)
    }
    @Test fun retiredDatesAreNotReportedAsCancelled() {
        val nextWeek = lesson.copy(id = "next", start = start.plusWeeks(1), end = lesson.end.plusWeeks(1))
        assertTrue(scheduleChanges(schedule(listOf(nextWeek)), schedule(emptyList())).isEmpty())
    }
    @Test fun emptyConfirmedDateRecordsCancellation() {
        val diff = scheduleChanges(schedule(listOf(lesson)), schedule(emptyList()))
        assertEquals(lesson, diff.single().before); assertNull(diff.single().after)
    }
    @Test fun historyIsBoundedAndSurvivesJsonRoundtrip() {
        val json = snapshot()
        repeat(25) { json.appendHistory(schedule(listOf(lesson)), schedule(listOf(lesson.copy(room = "${it + 300}")))) }
        assertEquals(20, json.getJSONArray("history").length())
        assertEquals("324", JSONObject(json.toString()).scheduleHistory().first().changes.single().after?.room)
    }
    @Test fun manualHomeworkStaysVisibleWithoutAnySchedule() {
        val json = snapshot().put("tasks", JSONObject().put("manual:one", "Задачи 1–5"))
            .put("taskMetadata", JSONObject().put("manual:one", JSONObject().put("group", "1").put("disciplineId", "math")
                .put("subject", "Математика").put("due", start.toString()).put("manual", true)))
        val entry = json.homeworkEntries().single()
        assertNull(entry.lessonId); assertEquals("Задачи 1–5", entry.text)
    }
    @Test fun groupReviewAndSubgroupFiltersNeverDeleteStoredHomework() {
        val json = snapshot().put("subgroup", "1").put("onlyMySubgroup", true)
        val tasks = JSONObject(); val metadata = JSONObject()
        for ((id, group, subgroup) in listOf(Triple("a", "1", ""), Triple("b", "1", "1"), Triple("c", "1", "2"), Triple("d", "2", ""))) {
            tasks.put(id, "task")
            metadata.put(id, JSONObject().put("group", group).put("subgroup", subgroup).put("disciplineId", "math").put("subject", "Math").put("due", start.toString()))
        }
        json.put("tasks", tasks).put("taskMetadata", metadata).put("completedTasks", JSONArray(listOf("b")))
        assertEquals(setOf("a", "b"), json.homeworkEntries().map { it.id }.toSet())
        assertTrue(json.homeworkEntries().first { it.id == "b" }.complete)
        metadata.getJSONObject("a").put("needsReview", true)
        assertEquals(listOf("b"), json.homeworkEntries().map { it.id })
        assertEquals(4, json.tasks().size)
    }
    @Test fun replacedLessonIdCannotShowOldHomeworkUnderNewSubject() {
        val json = snapshot().put("tasks", JSONObject().put(lesson.id, "Original homework"))
            .put("completedTasks", JSONArray(listOf(lesson.id)))
        rememberTaskMetadata(json, listOf(lesson))
        json.archiveHomeworkForReview(lesson)
        assertFalse(json.tasks().containsKey(lesson.id))
        val retained = json.tasks().entries.single()
        assertTrue(retained.key.startsWith("review:")); assertEquals("Original homework", retained.value)
        assertTrue(json.getJSONObject("taskMetadata").getJSONObject(retained.key).getBoolean("needsReview"))
        assertTrue(retained.key in json.completedTasks()); assertTrue(json.homeworkEntries().isEmpty())
        // A new task for the replacement gets its own metadata while the old text stays intact.
        json.getJSONObject("tasks").put(lesson.id, "Physics homework")
        rememberTaskMetadata(json, listOf(lesson.copy(disciplineId = "physics", disciplineName = "Physics")), force = true)
        assertEquals("physics", json.homeworkEntries().single().disciplineId)
        assertEquals("Original homework", json.tasks()[retained.key])
    }
    @Test fun oldReviewFlagsDetachOnceAndPreserveOriginalSubject() {
        val json = snapshot().put("tasks", JSONObject().put(lesson.id, "Old text"))
        rememberTaskMetadata(json, listOf(lesson))
        json.getJSONObject("taskMetadata").getJSONObject(lesson.id).put("needsReview", true).put("previousSubject", "Original subject")
        assertTrue(json.detachLegacyReviewTasks())
        assertFalse(json.detachLegacyReviewTasks())
        val id = json.tasks().keys.single()
        assertEquals("Old text", json.tasks()[id])
        assertEquals("Original subject", json.getJSONObject("taskMetadata").getJSONObject(id).getString("previousSubject"))
    }
    @Test fun returningToAGroupCannotRebindAnOldTaskWhenPreviousScheduleCacheWasCleared() {
        val json = snapshot().put("tasks", JSONObject().put(lesson.id, "Original math task"))
        rememberTaskMetadata(json, listOf(lesson))
        val replacement = lesson.copy(disciplineId = "physics", disciplineName = "Physics")
        json.protectHomeworkBindings(listOf(replacement))
        rememberTaskMetadata(json, listOf(replacement))
        assertFalse(json.tasks().containsKey(lesson.id))
        val id = json.tasks().keys.single()
        assertEquals("Original math task", json.tasks()[id])
        assertEquals(lesson.disciplineName, json.getJSONObject("taskMetadata").getJSONObject(id).getString("subject"))
        assertEquals(lesson.disciplineId, json.getJSONObject("taskMetadata").getJSONObject(id).getString("disciplineId"))
    }
}
