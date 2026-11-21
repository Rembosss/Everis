package org.everis.app.data

import org.everis.core.LessonType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RgupsDataTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val group = StudentGroup("rgups:1:1:29754", "ААС-1-231", "rgups")
    private val today = LocalDate.of(2026, 11, 21)
    private fun canonical(html: String = fixture("rgups-timetable.html"), day: LocalDate = today) =
        RgupsData.canonicalSchedule(html, group.name, day)

    @Test fun officialHierarchicalCatalogRetainsFacultyCourseAndGroupIdentity() {
        assertEquals(14, RgupsData.facultyIds(fixture("rgups-time.html")).size)
        assertEquals(listOf("1", "2", "3", "4", "5"), RgupsData.courseIds(fixture("rgups-course.html")))
        val groups = RgupsData.parseGroups(fixture("rgups-groups.html"), "1", "1")
        assertEquals(10, groups.size)
        assertTrue(group in groups)
        assertEquals(group, studentGroup(group.toJson().apply { remove("university") }))
        assertThrows(IllegalArgumentException::class.java) { RgupsData.parseGroups(fixture("rgups-groups.html"), "2", "1") }
        assertThrows(IllegalArgumentException::class.java) { RgupsData.parseGroups("login page", "1", "1") }
    }
    @Test fun recurringCycleIsExplicitAndNeverPretendsToBeADatedSemester() {
        val raw = canonical()
        val json = JSONObject(raw)
        assertTrue(json.getBoolean("cycleProjection"))
        assertEquals("2026-11-16", json.getString("cycleAnchor"))
        assertEquals("над чертой", json.getString("cycleParity"))
        val schedule = parseStudentSchedule(raw, group)
        assertEquals(14, schedule.days.size)
        assertEquals(LocalDate.of(2026, 11, 16), schedule.days.min())
        assertEquals(LocalDate.of(2026, 11, 29), schedule.days.max())
        assertTrue(schedule.lessons.size > 20)
        assertTrue(schedule.lessons.any { it.type == LessonType.LECTURE })
        assertTrue(schedule.lessons.any { it.type == LessonType.PRACTICE })
        assertTrue(schedule.lessons.all { it.building == null })
        assertFalse(UniversitySources.supportsTeacherTimetable("rgups"))
    }
    @Test fun rowspanTimesAndMultipleSubgroupsRemainAttachedToTheRightLesson() {
        val parsed = parseStudentSchedule(canonical(), group)
        val lesson = parsed.lessons.first { it.disciplineName == "Иностранный язык" && it.subgroup == "нем.1" && it.start.toLocalTime().toString() == "10:00" }
        assertEquals("10:00", lesson.start.toLocalTime().toString())
        assertEquals("11:30", lesson.end.toLocalTime().toString())
        assertEquals("М212", lesson.room)
        assertEquals(listOf("Одарюк И.В."), lesson.teachers)
        assertTrue(parsed.lessons.none { it.disciplineName in setOf("—", "–", "-", "\u0097") })
    }
    @Test fun rollingCycleKeepsStableOccurrenceIdsInOverlappingWeeks() {
        val before = parseStudentSchedule(canonical(), group)
        val after = parseStudentSchedule(canonical(day = today.minusDays(1)), group)
        assertEquals(before, after)
        val flipped = fixture("rgups-timetable.html").replace("class=\"disable \"", "class=\"ACTIVE\"")
            .replace("class=\" \"", "class=\"disable \"").replace("class=\"ACTIVE\"", "class=\" \"")
        val next = parseStudentSchedule(canonical(flipped, today.plusWeeks(1)), group)
        val overlapping = LocalDate.of(2026, 11, 23)..LocalDate.of(2026, 11, 29)
        assertEquals(before.lessons.filter { it.start.toLocalDate() in overlapping }, next.lessons.filter { it.start.toLocalDate() in overlapping })
    }
    @Test fun malformedOrAmbiguousTablesDoNotEraseTheSavedTimetable() {
        assertThrows(IllegalArgumentException::class.java) { canonical("<html>Ошибка</html>") }
        assertThrows(IllegalStateException::class.java) { canonical(fixture("rgups-timetable.html").replace("disable", "")) }
        assertThrows(java.time.DateTimeException::class.java) { canonical(fixture("rgups-timetable.html").replace("10.00-11.30", "99.00-11.30")) }
    }
    @Test fun publicCycleSurvivesOfflineExportAndBackup() {
        val source = JSONObject().put("group", group.toJson()).put("schedule", canonical())
        val restored = SchedulePayload.validate(SchedulePayload.encode(source))
        assertEquals(group, studentGroup(restored.getJSONObject("group")))
        assertTrue(JSONObject(restored.getString("schedule")).getBoolean("cycleProjection"))
        val backup = BackupPayload.validate(BackupPayload.encode(source))
        assertEquals(group, backup.selectedGroup())
    }
    @Test fun jointTeachersAreCombinedAndTeacherReplacementKeepsHomeworkBinding() {
        val original = fixture("rgups-timetable.html")
        val replacement = original.replace("Одарюк И.В.", "Новый преподаватель")
        val before = parseStudentSchedule(canonical(original), group)
        val after = parseStudentSchedule(canonical(replacement), group)
        assertEquals(before.lessons.map { it.id }, after.lessons.map { it.id })
        val joint = original.replaceFirst("Одарюк И.В.", "Другой преподаватель")
        val schedule = parseStudentSchedule(canonical(joint), group)
        assertTrue(schedule.lessons.any { "Другой преподаватель" in it.teachers && "Одарюк И.В." in it.teachers })
    }

}
