package org.everis.app.data

import org.everis.core.LessonType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class IubipDataTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val name = "ПД101"
    private val group = StudentGroup(IubipData.groupId(name), name, "iubip")
    private val times get() = IubipData.parseBellTimes(fixture("iubip-bells.txt"))
    private fun canonical(raw: String = fixture("iubip-schedule.json")) = IubipData.canonicalSchedule(raw, name, times)
    private fun firstRow(root: JSONObject) = root.getJSONArray(name).getJSONObject(1).getJSONArray("10")
        .getJSONObject(1).getJSONObject("1").getJSONArray("3").getJSONObject(0)

    @Test fun publicCatalogPreservesCombinedGroupNamesAndStableProviderIdentity() {
        val groups = IubipData.parseGroups(fixture("iubip-groups.json"))
        assertTrue(groups.size > 20)
        assertTrue(group in groups)
        assertTrue(groups.any { it.name.contains(',') })
        assertEquals(group, studentGroup(group.toJson()))
        assertEquals(group, studentGroup(group.toJson().apply { remove("university") }))
        assertThrows(IllegalArgumentException::class.java) { StudentGroup("iubip:bad", name, "iubip") }
    }
    @Test fun allPublishedWeeksHaveExactSourceDatesTimesAndTypes() {
        val parsed = parseStudentSchedule(canonical(), group)
        assertEquals(35, parsed.days.size)
        assertEquals(57, parsed.lessons.size)
        assertEquals(LocalDate.of(2026, 10, 26), parsed.days.min())
        assertEquals(LocalDate.of(2026, 11, 29), parsed.days.max())
        assertEquals("11:40", parsed.lessons.first().start.toLocalTime().toString())
        assertEquals("13:10", parsed.lessons.first().end.toLocalTime().toString())
        assertEquals(32, parsed.lessons.count { it.type == LessonType.PRACTICE })
        assertEquals(8, parsed.lessons.count { it.type == LessonType.LABORATORY })
        assertEquals(12, parsed.lessons.count { it.type == LessonType.LECTURE })
        assertTrue(parsed.lessons.all { it.building == null && it.id.startsWith("iubip:") })
    }
    @Test fun subgroupAndCancelledRowsArePreserved() {
        val raw = JSONObject(fixture("iubip-schedule.json"))
        firstRow(raw).put("SUBG", "2").put("deleted", 1)
        val lesson = parseStudentSchedule(canonical(raw.toString()), group).lessons.first()
        assertEquals("2", lesson.subgroup)
        assertTrue(lesson.cancelled)
        assertFalse(parseStudentSchedule(canonical(raw.toString()), group).forSubgroup("1").lessons.contains(lesson))
    }
    @Test fun wrongIdentityOrDateNeverBecomesSomeoneElsesSchedule() {
        assertThrows(IllegalArgumentException::class.java) { IubipData.canonicalSchedule(fixture("iubip-schedule.json"), "OTHER", times) }
        val raw = JSONObject(fixture("iubip-schedule.json"))
        firstRow(raw).put("GROUP", "ПД1010")
        assertThrows(IllegalArgumentException::class.java) { canonical(raw.toString()) }
        firstRow(raw).put("GROUP", name).put("DATE", "27-10-2026")
        assertThrows(IllegalArgumentException::class.java) { canonical(raw.toString()) }
    }
    @Test fun missingOrConflictingBellGridFailsInsteadOfGuessingTimes() {
        assertThrows(IllegalArgumentException::class.java) { IubipData.parseBellTimes("no table") }
        assertThrows(IllegalArgumentException::class.java) { IubipData.parseBellTimes(fixture("iubip-bells.txt") + " data=\"09:00-10:00\" data-td=\"d1l1\"") }
        assertThrows(IllegalArgumentException::class.java) { IubipData.canonicalSchedule(fixture("iubip-schedule.json"), name, emptyMap()) }
    }
    @Test fun portableScheduleAndBackupRetainTheNewProvider() {
        val source = JSONObject().put("group", group.toJson()).put("schedule", canonical())
        val exported = SchedulePayload.encode(source)
        assertEquals(3, JSONObject(exported.toString(Charsets.UTF_8)).getInt("version"))
        assertEquals(group, studentGroup(SchedulePayload.validate(exported).getJSONObject("group")))
        val backup = BackupPayload.validate(BackupPayload.encode(source))
        assertEquals(group, backup.selectedGroup())
        assertFalse(UniversitySources.supportsTeacherTimetable("iubip"))
    }
    @Test fun sharedGroupSourceKeysAreNotSplitOrLost() {
        val source = fixture("iubip-combined.json")
        val name = JSONObject(source).keys().asSequence().single()
        val shared = StudentGroup(IubipData.groupId(name), name, "iubip")
        val parsed = parseStudentSchedule(IubipData.canonicalSchedule(source, name, times), shared)
        assertEquals("ПЗу301,ПЗу302", parsed.group)
        assertEquals(19, parsed.lessons.size)
        assertTrue(parsed.lessons.any { it.type == LessonType.LECTURE })
    }

}
