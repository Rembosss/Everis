package org.everis.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.everis.core.LessonType
import java.time.LocalDate

class RsueDataTest {
    private fun fixture(): String = javaClass.getResource("/rsue-lessons.json")!!.readText()

    @Test fun mixedPublicDirectoryContainsOnlyStudentGroups() {
        val raw = javaClass.getResource("/rsue-search.json")!!.readText()
        val groups = RsueData.parseGroups(raw)
        assertTrue(groups.any { it.name == "МЕН-144" })
        assertFalse(groups.any { it.name.contains("Комиссия") || it.name.contains("преп.") })
        assertEquals(groups.size, groups.distinctBy { it.id }.size)
    }

    @Test fun explicitPublishedDatesAreUsedWithoutRepeatingWeekTemplates() {
        val schedule = RsueData.parseSchedule(fixture(), "МЕН-144")
        assertEquals(setOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)), schedule.days)
        assertEquals(4, schedule.lessons.size)
        assertEquals("08:30", schedule.lessons.first().start.toLocalTime().toString())
        assertEquals(LessonType.LECTURE, schedule.lessons.first().type)
        assertEquals("Островского, 62", schedule.lessons.first().building)
        assertEquals("411", schedule.lessons.first().room)
        assertTrue(schedule.lessons.all { it.start.toLocalDate() == LocalDate.of(2026, 9, 1) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchingGroupIsNeverAccepted() { RsueData.parseSchedule(fixture(), "OTHER-1") }

    @Test(expected = IllegalArgumentException::class)
    fun missingCalendarDateIsNotInvented() {
        val data = JSONObject(fixture())
        data.getJSONArray("weeks").getJSONObject(0).getJSONArray("days").getJSONObject(0).put("date", JSONObject.NULL)
        RsueData.parseSchedule(data.toString(), "МЕН-144")
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyResponseIsNotAnEmptySuccessfulSchedule() {
        RsueData.parseSchedule("""{"kind":"Group","instance":"МЕН-144","weeks":[]}""", "МЕН-144")
    }

    @Test fun actualSourceSubgroupsArePreservedAndCommonLessonsRemainVisible() {
        val json = JSONObject(fixture())
        val lesson = json.getJSONArray("weeks").getJSONObject(0).getJSONArray("days").getJSONObject(1)
            .getJSONArray("pairs").getJSONObject(0).getJSONArray("lessons").getJSONObject(0)
        lesson.put("subgroup", JSONObject().put("id", 1).put("name", "Подгруппа 1"))
        val parsed = RsueData.parseSchedule(json.toString(), "МЕН-144")
        assertEquals("Подгруппа 1", parsed.lessons.first().subgroup)
        assertEquals(3, parsed.forSubgroup("Подгруппа 2").lessons.size)
        assertEquals(4, parsed.forSubgroup("Подгруппа 1").lessons.size)
    }
    @Test(expected = java.time.format.DateTimeParseException::class)
    fun invalidSourceDateIsRejectedInsteadOfSilentlyMovingToFebruary28() {
        val data = JSONObject(fixture())
        data.getJSONArray("weeks").getJSONObject(0).getJSONArray("days").getJSONObject(0).put("date", "31.02.2026")
        RsueData.parseSchedule(data.toString(), "МЕН-144")
    }
}
