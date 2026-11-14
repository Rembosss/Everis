package org.everis.app.data

import org.everis.app.DiaryState
import org.everis.core.LessonType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UniversitySourcesTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val group get() = StudentGroup("dstu:71397", "AMCE11", "dstu")
    private fun weekly() = DstuData.canonicalSchedule(fixture("dstu-week.json"), group.name, expectedId = 71397)
    private fun emptyWindow() = JSONObject().put("state", 1).put("data", JSONObject().put("rasp", JSONArray())
        .put("info", JSONObject().put("group", JSONObject().put("name", group.name).put("groupID", 71397))
            .put("date", "2026-11-09T00:00:00").put("lastDate", "2026-11-16T00:00:00"))).toString()

    @Test fun dstuGroupsHaveNamespacedIdsAndPreserveTheirUniversity() {
        val groups = DstuData.parseGroups(fixture("dstu-groups.json"))
        assertEquals(3, groups.size)
        assertTrue(groups.all { it.university == "dstu" && it.id.startsWith("dstu:") })
        assertTrue(groups.contains(group))
    }
    @Test fun legacyGroupJsonRemainsRsueAndRoundTripRetainsDstu() {
        assertEquals(StudentGroup("1", "МЕН-144"), studentGroup(JSONObject().put("id", "1").put("name", "МЕН-144")))
        assertEquals(group, studentGroup(group.toJson()))
        assertEquals(group, studentGroup(JSONObject().put("id", group.id).put("name", group.name)))
    }
    @Test fun datesTypesAndSourceIdsFollowTheCapturedOfficialDstuResponse() {
        val schedule = parseStudentSchedule(weekly(), group)
        assertEquals("dstu", schedule.university)
        assertEquals(5, schedule.lessons.size)
        assertEquals(7, schedule.days.size)
        val first = schedule.lessons.first()
        assertEquals(LocalDate.of(2026, 11, 9), first.start.toLocalDate())
        assertEquals("17:45", first.start.toLocalTime().toString())
        assertEquals("19:20", first.end.toLocalTime().toString())
        assertEquals(LessonType.LECTURE, first.type)
        assertEquals("Geotechnical engineering", first.disciplineName)
        assertTrue(first.id.startsWith("dstu:AMCE11:"))
        assertEquals("25-115", first.room)
        assertNull(first.building) // No invented street address.
    }
    @Test fun explicitEmptyRangeConfirmsDaysWithoutInventingLessons() {
        val parsed = parseStudentSchedule(DstuData.canonicalSchedule(emptyWindow(), group.name, expectedId = 71397), group)
        assertEquals(7, parsed.days.size)
        assertTrue(parsed.lessons.isEmpty())
        assertTrue(LocalDate.of(2026, 11, 15) in parsed.days)
        assertFalse(LocalDate.of(2026, 11, 16) in parsed.days)
    }
    @Test fun sourceWithoutDatesDoesNotPretendToKnowAnEmptyWeek() {
        val raw = JSONObject().put("state", 1).put("data", JSONObject().put("rasp", JSONArray())).toString()
        val parsed = parseStudentSchedule(DstuData.canonicalSchedule(raw, group.name), group)
        assertTrue(parsed.days.isEmpty())
        assertTrue(parsed.lessons.isEmpty())
    }
    @Test fun mergingCurrentRangeDoesNotDuplicateOccurrencesOrChangeBindings() {
        val once = parseStudentSchedule(weekly(), group)
        val twice = parseStudentSchedule(DstuData.canonicalSchedule(fixture("dstu-week.json"), group.name,
            current = fixture("dstu-week.json"), expectedId = 71397), group)
        assertEquals(once, twice)
    }
    @Test fun actualDstuSharedClassAndTitleSubgroupsDoNotRejectTheWholeTimetable() {
        val student = StudentGroup("dstu:73349", "ВПР11", "dstu")
        val raw = DstuData.canonicalSchedule(fixture("dstu-shared-subgroups.json"), student.name, expectedId = 73349)
        val schedule = parseStudentSchedule(raw, student)
        assertEquals(4, schedule.lessons.size)
        assertEquals(setOf("1", "2"), schedule.lessons.mapNotNull { it.subgroup }.toSet())
        assertEquals(2, schedule.lessons.count { it.disciplineName == "Физика" })
        assertTrue(schedule.lessons.none { it.disciplineName.contains("п/г") })
        val first = schedule.forSubgroup("1").lessons
        assertEquals(3, first.size)
        assertTrue(first.any { it.disciplineName == "Физическая культура и спорт" && it.type == LessonType.PRACTICE })
        assertTrue(first.none { it.subgroup == "2" })
    }
    @Test fun explicitDstuSubgroupTakesPriorityAndKeepsOccurrenceIdentity() {
        val root = JSONObject(fixture("dstu-shared-subgroups.json"))
        val rows = root.getJSONObject("data").getJSONArray("rasp")
        val before = RsueData.parseSchedule(DstuData.canonicalSchedule(root.toString(), "ВПР11", expectedId = 73349), "ВПР11")
        rows.getJSONObject(1).put("номерПодгруппы", 3)
        val after = RsueData.parseSchedule(DstuData.canonicalSchedule(root.toString(), "ВПР11", expectedId = 73349), "ВПР11")
        assertEquals(before.lessons.map { it.id }, after.lessons.map { it.id })
        assertTrue(after.lessons.any { it.subgroup == "3" && it.disciplineName == "Физика" })
    }
    @Test(expected = IllegalArgumentException::class) fun zeroGroupIdNeedsAnExactMatchingGroupLabel() {
        val root = JSONObject(fixture("dstu-shared-subgroups.json"))
        root.getJSONObject("data").getJSONArray("rasp").getJSONObject(0).put("группа", "ВПР111")
        DstuData.canonicalSchedule(root.toString(), "ВПР11", expectedId = 73349)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroGroupIdNeedsConfirmedResponseMetadata() {
        val root = JSONObject(fixture("dstu-shared-subgroups.json"))
        root.getJSONObject("data").remove("info")
        DstuData.canonicalSchedule(root.toString(), "ВПР11", expectedId = 73349)
    }
    @Test fun freshEmptyWeekRemovesCancelledClassesFromTheFullResponse() {
        val combined = DstuData.canonicalSchedule(fixture("dstu-week.json"), group.name,
            current = emptyWindow(), expectedId = 71397)
        val parsed = parseStudentSchedule(combined, group)
        assertEquals(7, parsed.days.size)
        assertTrue(parsed.lessons.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTheWrongGroupReturnedBySource() {
        DstuData.canonicalSchedule(fixture("dstu-week.json"), "OTHER", expectedId = 71397)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongNumericIdentityDespiteMatchingName() {
        DstuData.canonicalSchedule(fixture("dstu-week.json"), group.name, expectedId = 123)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsAProviderMismatchInPortableData() {
        parseStudentSchedule(weekly(), StudentGroup("71397", "AMCE11"))
    }
    @Test(expected = IllegalArgumentException::class) fun sourceFailureIsNotASuccessfulEmptySchedule() {
        DstuData.canonicalSchedule("""{"state":-1,"data":{}}""", group.name)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUndatedCyclicalTemplates() {
        val root = JSONObject(emptyWindow());root.getJSONObject("data").put("isCyclicalSchedule", true)
        DstuData.canonicalSchedule(root.toString(), group.name)
    }
    @Test fun rsueTeacherTimetableIsSeparateFromStudentScheduleAndContainsGroups() {
        val raw = fixture("rsue-teacher.json")
        val teacher = RsueData.parseSchedule(raw, "Возиянов Д.", "Teacher")
        assertEquals(4, teacher.lessons.size)
        assertTrue(teacher.lessons.all { it.studentGroups.isNotEmpty() })
        assertEquals("rsue", teacher.university)
        assertThrows(IllegalArgumentException::class.java) { RsueData.parseSchedule(raw, "Возиянов Д.") }
    }
    @Test fun scheduleExportRestoresTheCorrectUniversityAndUsesAnExplicitNewSchema() {
        val source = JSONObject().put("group", group.toJson()).put("schedule", weekly())
        val encoded = SchedulePayload.encode(source)
        assertEquals(2, JSONObject(encoded.toString(Charsets.UTF_8)).getInt("version"))
        val parsed = SchedulePayload.validate(encoded)
        val restored = JSONObject()
        SchedulePayload.restore(restored, parsed)
        assertEquals(group, restored.selectedGroup())
        assertEquals(5, parseStudentSchedule(restored.getString("schedule"), group).lessons.size)
    }
    @Test fun dockPreferenceIsPortableAndOldSnapshotsDefaultToTheNewStyle() {
        assertTrue(DiaryState().newDock)
        val source = JSONObject().put("newDock", false)
        val decoded = BackupPayload.validate(BackupPayload.encode(source))
        assertFalse(decoded.getBoolean("newDock"))
        assertEquals(1, JSONObject(BackupPayload.encode(source).toString(Charsets.UTF_8)).getInt("version"))
    }
    @Test fun newSourceBackupsAreVersionedButOldRsueBackupsStillImport() {
        val source = JSONObject().put("group", group.toJson()).put("schedule", weekly()).put("newDock", false)
        val bytes = BackupPayload.encode(source)
        assertEquals(2, JSONObject(bytes.toString(Charsets.UTF_8)).getInt("version"))
        assertEquals(group, BackupPayload.validate(bytes).selectedGroup())
        val old = """{"format":"everis-backup","version":1,"payload":{"palette":"dynamic"}}""".toByteArray()
        assertEquals("dynamic", BackupPayload.validate(old).getString("palette"))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedDockPreferenceCannotEnterStoredSettings() {
        val bad = """{"format":"everis-backup","version":1,"payload":{"newDock":"false"}}""".toByteArray()
        BackupPayload.validate(bad)
    }
}
