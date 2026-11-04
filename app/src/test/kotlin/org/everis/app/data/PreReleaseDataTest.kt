package org.everis.app.data

import org.everis.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PreReleaseDataTest {
    private val day = LocalDate.of(2026, 10, 29)
    private fun snapshot(): JSONObject {
        val root = JSONObject(javaClass.getResource("/rsue-lessons.json")!!.readText())
        fun entry(id: Int, subject: String, subgroup: Int, type: String) = JSONObject()
            .put("id", id).put("subject", subject).put("kind", JSONObject().put("name", type))
            .put("teacher", JSONObject().put("name", "Teacher $id"))
            .put("subgroup", JSONObject().put("id", subgroup).put("name", if (subgroup == 0) "Нет подгруппы" else subgroup.toString()))
            .put("audience", "101*")
        fun pair(id: Int, start: String, end: String, lesson: JSONObject) = JSONObject().put("id", id)
            .put("startTime", start).put("endTime", end).put("lessons", JSONArray().put(lesson))
        root.put("weeks", JSONArray().put(JSONObject().put("days", JSONArray().put(JSONObject().put("date", "29.10.2026")
            .put("pairs", JSONArray()
                .put(pair(1, "09:00", "10:30", entry(1, "Математика", 0, "Лекция")))
                .put(pair(2, "10:40", "12:10", entry(2, "Математика (лаб.)", 1, "Лабораторная")))
                .put(pair(3, "13:00", "14:30", entry(3, "Математика (лабораторная)", 2, "Лабораторная"))))))))
        return JSONObject().put("group", JSONObject().put("id", "1424").put("name", root.getString("instance")))
            .put("completed", true).put("schedule", root.toString()).put("subgroup", "1")
    }
    private fun lessons(json: JSONObject) = RsueData.parseSchedule(json.getString("schedule"), json.selectedGroup()!!.name).lessons
    private fun tasks(json: JSONObject): JSONObject {
        val all = lessons(json)
        json.put("tasks", JSONObject().apply { all.forEachIndexed { index, lesson -> put(lesson.id, "Task $index") } })
        rememberTaskMetadata(json, all)
        return json
    }
    @Test fun lecturesAndLabsInBothSubgroupsHaveOneDisciplineIdentity() {
        val all = lessons(snapshot())
        assertEquals(1, all.groupBy { disciplineKey(it.disciplineName) }.size)
        assertEquals(3, all.flatMap { it.teachers }.distinct().size)
        assertEquals("Математика", disciplineTitle("  Математика   (лаб.) "))
    }
    @Test fun meaningfulCourseQualifiersAreNeverMerged() {
        assertNotEquals(disciplineKey("Иностранный язык (английский)"), disciplineKey("Иностранный язык (немецкий)"))
        assertNotEquals(disciplineKey("Математика 1"), disciplineKey("Математика 2"))
    }
    @Test fun normalizedIdentityIgnoresCaseWhitespaceAndYo() {
        assertEquals(disciplineKey("Учёт  затрат"), disciplineKey("УЧЕТ\u00a0ЗАТРАТ"))
    }
    @Test fun nextTargetsIncludeLabsButPreserveSourceOccurrenceIds() {
        val all = lessons(snapshot())
        val targets = disciplineTargets(all, all.first().disciplineName, all.first().end.toInstant())
        assertEquals(2, targets.size)
        assertEquals(setOf("1", "2"), targets.mapNotNull { it.subgroup }.toSet())
        assertEquals(all.drop(1).map { it.id }, targets.map { it.id })
    }
    @Test fun selectedSubgroupDoesNotFilterUntilOptionIsEnabled() {
        val json = tasks(snapshot())
        assertEquals("", json.visibleSubgroup())
        assertEquals(3, lessons(json).forSubgroup(json.visibleSubgroup()).size)
        assertEquals(3, json.homeworkEntries().size)
    }
    @Test fun subgroupOptionRemovesOtherLessonsHoursAndHomeworkInEveryPeriod() {
        val json = tasks(snapshot()).put("onlyMySubgroup", true)
        val visible = lessons(json).forSubgroup(json.visibleSubgroup())
        assertEquals(2, visible.size); assertTrue(visible.none { it.subgroup == "2" })
        assertEquals(2, json.homeworkEntries().size)
        val daily = summarizeDay(day, RsueData.zone, visible, Coverage(day, day, true))
        assertEquals(180L, daily.teaching.toMinutes())
        for (period in listOf(DatePeriod.weekContaining(day), DatePeriod.month(YearMonth.from(day)))) {
            val total = summarizePeriod(period, RsueData.zone, visible, Coverage(period.first, period.last, true))
            assertEquals(2, total.lessonCount); assertEquals(180L, total.teaching.toMinutes())
        }
    }
    @Test fun disablingSubgroupOptionRestoresOtherGroupWithoutDeletingAnything() {
        val json = tasks(snapshot()).put("onlyMySubgroup", true)
        val before = json.getJSONObject("tasks").toString()
        json.put("onlyMySubgroup", false)
        assertEquals(3, json.homeworkEntries().size); assertEquals(before, json.getJSONObject("tasks").toString())
    }
    @Test fun batchCompletionMarksOnlyChosenTasks() {
        val json = tasks(snapshot()); val entries = json.homeworkEntries()
        completeHomeworkBatch(json, json.selectedGroup()!!.id, entries.take(2).associate { it.id to it.text })
        assertEquals(entries.take(2).map { it.id }.toSet(), json.completedTasks())
        assertEquals(1, json.homeworkEntries().count { !it.complete })
    }
    @Test fun staleBatchDoesNotPartiallyCompleteOtherTasks() {
        val json = tasks(snapshot()); val entries = json.homeworkEntries(); val before = json.toString()
        try { completeHomeworkBatch(json, json.selectedGroup()!!.id, entries.take(2).associate { it.id to "stale" }); fail() }
        catch (_: IllegalArgumentException) { assertEquals(before, json.toString()) }
    }
    @Test fun batchCannotCompleteOtherSubgroupAfterFilteringChanges() {
        val json = tasks(snapshot()); val entries = json.homeworkEntries()
        json.put("onlyMySubgroup", true)
        try { completeHomeworkBatch(json, json.selectedGroup()!!.id, entries.associate { it.id to it.text }); fail() }
        catch (_: IllegalArgumentException) { assertTrue(json.completedTasks().isEmpty()) }
    }
    @Test fun newBackupIsPlainJsonAndKeepsNewPreferences() {
        val json = tasks(snapshot()).put("onlyMySubgroup", true).put("performanceMode", true)
        val bytes = BackupPayload.encode(json)
        assertTrue(bytes.toString(Charsets.UTF_8).startsWith("{"))
        val restored = BackupPayload.validate(bytes)
        assertTrue(restored.getBoolean("performanceMode")); assertTrue(restored.getBoolean("onlyMySubgroup"))
        assertEquals(json.getJSONObject("tasks").toString(), restored.getJSONObject("tasks").toString())
    }
    @Test fun scheduleExportContainsNoHomeworkOrSettings() {
        val json = tasks(snapshot()).put("palette", "#123456").put("notifications", JSONObject().put("daysAhead", 14))
        val encoded = SchedulePayload.encode(json).toString(Charsets.UTF_8)
        assertFalse(encoded.contains("Task 0")); assertFalse(encoded.contains("#123456")); assertFalse(encoded.contains("daysAhead"))
        val decoded = SchedulePayload.validate(encoded.toByteArray())
        assertEquals(3, lessons(decoded).size)
    }
    @Test fun offlineScheduleImportPreservesPersonalDataAndAddsGroupToCatalogue() {
        val original = JSONObject().put("tasks", JSONObject().put("manual:keep", "Keep me"))
            .put("palette", "#123456").put("performanceMode", true).put("notificationPermissionAsked", true)
        val payload = SchedulePayload.validate(SchedulePayload.encode(snapshot()))
        SchedulePayload.restore(original, payload)
        assertEquals("Keep me", original.getJSONObject("tasks").getString("manual:keep"))
        assertEquals("#123456", original.getString("palette")); assertTrue(original.getBoolean("performanceMode"))
        assertTrue(original.getBoolean("notificationPermissionAsked")); assertFalse(original.optBoolean("completed"))
        assertEquals(1, original.getJSONArray("groups").length()); assertEquals(3, lessons(original).size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun privateBackupIsNotAcceptedAsSharedSchedule() { SchedulePayload.validate(BackupPayload.encode(snapshot())) }
    @Test(expected = IllegalArgumentException::class)
    fun sharedScheduleForAnotherGroupIsRejected() {
        val payload = JSONObject(SchedulePayload.encode(snapshot()).toString(Charsets.UTF_8))
        payload.getJSONObject("group").put("name", "Другая-1")
        SchedulePayload.validate(payload.toString().toByteArray())
    }
    @Test(expected = IllegalArgumentException::class)
    fun malformedBooleanPreferenceIsRejectedBeforeRestore() {
        BackupPayload.validate(BackupPayload.encode(snapshot().put("performanceMode", JSONObject())))
    }
    @Test(expected = IllegalArgumentException::class)
    fun deeplyNestedDocumentsAreRejectedBeforeJsonRecursion() {
        portableJson(("[".repeat(100) + "0" + "]".repeat(100)).toByteArray())
    }
    @Test(expected = IllegalArgumentException::class)
    fun nestedRawSchedulesAreAlsoBounded() {
        RsueData.parseSchedule("{" + "\"a\":[".repeat(100) + "0" + "]".repeat(100) + "}", "X")
    }
    @Test fun bracesAndEscapedQuotesInHomeworkDoNotCountAsNesting() {
        val text = "{[\\\"".repeat(100)
        val parsed = portableJson(JSONObject().put("text", text).toString().toByteArray())
        assertEquals(text, parsed.getString("text"))
    }
    @Test fun teachingAbbreviationsMergeButCourseQualifiersStay() {
        for (suffix in listOf("(ЛР)", "(ПЗ)", "(пр.)", "[Лекция]", "— лабораторная работа", "( лаб. )")) {
            assertEquals(suffix, disciplineKey("Математика"), disciplineKey("Математика $suffix"))
        }
    }
    @Test fun scheduleImportUpdatesAnExistingCatalogueName() {
        val value = snapshot().put("groups", JSONArray().put(JSONObject().put("id", "1424").put("name", "Old name")))
        SchedulePayload.restore(value, SchedulePayload.validate(SchedulePayload.encode(snapshot())))
        assertEquals(1, value.getJSONArray("groups").length())
        assertEquals(value.selectedGroup()!!.name, value.getJSONArray("groups").getJSONObject(0).getString("name"))
    }
    @Test fun cancelledImportedLessonKeepsItsOriginalTaskForReview() {
        val original = tasks(snapshot())
        val removed = lessons(original).last()
        val incoming = snapshot()
        val raw = JSONObject(incoming.getString("schedule"))
        raw.getJSONArray("weeks").getJSONObject(0).getJSONArray("days").getJSONObject(0).getJSONArray("pairs").remove(2)
        incoming.put("schedule", raw.toString())
        SchedulePayload.restore(original, SchedulePayload.validate(SchedulePayload.encode(incoming)))
        assertFalse(original.tasks().containsKey(removed.id))
        assertTrue(original.tasks().values.contains("Task 2"))
        assertTrue(original.getJSONObject("taskMetadata").keys().asSequence().any { it.startsWith("review:") })
    }
    @Test fun wrongPortableCollectionTypesAreRejected() {
        for ((key, invalid) in listOf("tasks" to JSONArray(), "taskMetadata" to "invalid", "completedTasks" to JSONObject(), "notifications" to JSONArray())) {
            try { BackupPayload.validate(BackupPayload.encode(snapshot().put(key, invalid))); fail(key) }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test(expected = java.nio.charset.CharacterCodingException::class)
    fun malformedUtf8IsRejected() { portableJson(byteArrayOf(0xc3.toByte(), 0x28)) }

}
