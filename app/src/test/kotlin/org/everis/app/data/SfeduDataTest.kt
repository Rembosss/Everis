package org.everis.app.data

import org.everis.core.LessonType
import org.everis.sources.PublicHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate

class SfeduDataTest {
    private val groupId = "01e0e853-0266-4ee2-95c8-e5dc23a786c0"
    private val eventId = "0801bda0-49a0-4d1d-a4f2-16ef1d157571"
    private val group = StudentGroup("sfedu:$groupId", "КТбо1-1", "sfedu")
    private fun link(id: String) = JSONObject().put("href", "/$id")
    private fun event(id: String = eventId, date: String = "2026-11-16", type: String = "LECT") = JSONObject()
        .put("id", id).put("name", "Тема занятия, а не дисциплина").put("typeId", type)
        .put("start", "${date}T10:10:00+03:00").put("end", "${date}T11:40:00+03:00")
        .put("description", group.name).put("_links", JSONObject().put("course-unit-realization", link("course")))
    private fun page(events: List<JSONObject>, number: Int = 0, total: Int = events.size, size: Int = 500): String {
        val embedded = JSONObject().put("events", JSONArray(events))
            .put("course-unit-realizations", JSONArray().put(JSONObject().put("id", "course").put("name", "Математический анализ")))
            .put("persons", JSONArray().put(JSONObject().put("id", "teacher").put("fullName", "Иванов Иван Иванович")))
            .put("event-attendees", JSONArray(events.map { JSONObject().put("roleId", "TEACH")
                .put("_links", JSONObject().put("event", link(it.getString("id"))).put("person", link("teacher"))) }))
            .put("rooms", JSONArray().put(JSONObject().put("id", "room").put("name", "Г-101")
                .put("building", JSONObject().put("address", "Открытый адрес корпуса"))))
            .put("event-rooms", JSONArray(events.map { JSONObject().put("_links", JSONObject()
                .put("event", link(it.getString("id"))).put("room", link("room"))) }))
        return JSONObject().put("_embedded", embedded).put("page", JSONObject().put("number", number)
            .put("size", size).put("totalElements", total).put("totalPages", (total + size - 1) / size)).toString()
    }
    private fun normalized(raw: String) = SfeduData.canonicalSchedule(listOf(raw), group.name)

    @Test fun groupUuidHasAProviderScopedIdentityAndSurvivesStorage() {
        val parsed = SfeduData.parseGroups("""[{"name":"КТбо1-1","personId":"$groupId"}]""").single()
        assertEquals(group, parsed)
        assertEquals(group, studentGroup(group.toJson()))
        assertEquals(group, studentGroup(group.toJson().apply { remove("university") }))
        assertTrue(SfeduData.parseGroups("[]").isEmpty())
    }
    @Test fun usesDisciplineRatherThanTopicAndPreservesTimesTeacherAndLocation() {
        val lesson = parseStudentSchedule(normalized(page(listOf(event()))), group).lessons.single()
        assertEquals("Математический анализ", lesson.disciplineName)
        assertEquals(LessonType.LECTURE, lesson.type)
        assertEquals("10:10", lesson.start.toLocalTime().toString())
        assertEquals("11:40", lesson.end.toLocalTime().toString())
        assertEquals(listOf("Иванов Иван Иванович"), lesson.teachers)
        assertEquals("Открытый адрес корпуса", lesson.building)
        assertEquals("Г-101", lesson.room)
        assertTrue(lesson.id.startsWith("sfedu:"))
    }
    @Test fun recognizesLaboratoriesAndSeminarsAndCancellation() {
        val lab = event(type = "LAB").put("holdingStatus", JSONObject().put("id", "CANCELLED"))
        val seminar = event("0801bda0-49a0-4d1d-a4f2-16ef1d157572", type = "SEMI")
        val lessons = parseStudentSchedule(normalized(page(listOf(lab, seminar))), group).lessons
        assertEquals(setOf(LessonType.LABORATORY, LessonType.PRACTICE), lessons.map { it.type }.toSet())
        assertTrue(lessons.first { it.type == LessonType.LABORATORY }.cancelled)
    }
    @Test fun explicitCurrentWeekClearsCancelledOccurrencesWithoutLosingLaterDates() {
        val later = event("0801bda0-49a0-4d1d-a4f2-16ef1d157572", "2026-11-30")
        val normalized = SfeduData.canonicalSchedule(listOf(page(listOf(event(), later))), group.name,
            currentPages = listOf(page(emptyList())), currentWeek = LocalDate.parse("2026-11-16"))
        val result = parseStudentSchedule(normalized, group)
        assertEquals(LocalDate.parse("2026-11-30"), result.lessons.single().start.toLocalDate())
        assertEquals(8, result.days.size)
        assertFalse(result.days.contains(LocalDate.parse("2026-11-24")))
    }
    @Test fun publicScheduleAndBackupUseFormatThreeAndRetainTheirProvider() {
        val snapshot = JSONObject().put("group", group.toJson()).put("schedule", normalized(page(listOf(event()))))
            .put("groups", JSONArray().put(group.toJson()))
        for (encoded in listOf(SchedulePayload.encode(snapshot), BackupPayload.encode(snapshot))) {
            assertEquals(3, JSONObject(encoded.toString(Charsets.UTF_8)).getInt("version"))
        }
        assertEquals(group, BackupPayload.validate(BackupPayload.encode(snapshot)).selectedGroup())
        assertEquals(group, SchedulePayload.validate(SchedulePayload.encode(snapshot)).selectedGroup())
    }
    @Test fun groupSourceAndTeacherFileIdsCannotBeConfusedOrEscapeTheirCache() {
        assertThrows(IllegalArgumentException::class.java) { StudentGroup("sfedu:$groupId", group.name, "rsue") }
        assertThrows(IllegalArgumentException::class.java) { StudentGroup("sfedu:../catalog", group.name, "sfedu") }
        assertTrue(UniversitySources.validTeacherId("sfedu", groupId))
        assertFalse(UniversitySources.validTeacherId("sfedu", "../catalog"))
        assertFalse(UniversitySources.validTeacherId("dstu", groupId))
        assertThrows(IllegalArgumentException::class.java) { parseStudentSchedule(normalized(page(listOf(event()))), StudentGroup("1", group.name)) }
    }
    @Test fun fetchesEveryPageAndThenConfirmsTheCurrentWeek() {
        var calls = 0
        val source = source { request ->
            calls++
            if (request.getString("timeMin").contains("-09-01T")) {
                val number = request.getInt("page")
                page(listOf(event(if (number == 0) eventId else "0801bda0-49a0-4d1d-a4f2-16ef1d157572")), number, total = 2, size = 1)
            } else page(emptyList())
        }
        val result = parseStudentSchedule(source.schedule(group), group)
        assertEquals(3, calls)
        assertTrue(result.days.size >= 7)
    }
    @Test fun rejectsDuplicatePagesInsteadOfSavingATruncatedSchedule() {
        val source = source { request -> page(listOf(event()), request.getInt("page"), total = 2, size = 1) }
        assertThrows(IllegalArgumentException::class.java) { source.schedule(group) }
    }
    @Test fun emptyRangeIsConfirmedOnlyByThePublic204Contract() {
        val result = parseStudentSchedule(source(status = 204) { "" }.schedule(group), group)
        assertTrue(result.lessons.isEmpty())
        assertEquals(7, result.days.size)
        assertThrows(IllegalArgumentException::class.java) { source { "" }.schedule(group) }
    }
    @Test fun absentOptionalFieldsDoNotAppearAsNullText() {
        val raw = JSONObject(page(listOf(event().put("description", JSONObject.NULL))))
        val embedded = raw.getJSONObject("_embedded")
        embedded.getJSONArray("persons").getJSONObject(0).put("fullName", JSONObject.NULL)
        embedded.getJSONArray("rooms").getJSONObject(0).put("name", JSONObject.NULL)
            .getJSONObject("building").put("address", JSONObject.NULL)
        val normalized = SfeduData.canonicalSchedule(listOf(raw.toString()), "Teacher", "Teacher")
        val lesson = RsueData.parseSchedule(normalized, "Teacher", "Teacher").lessons.single()
        assertTrue(lesson.studentGroups.isEmpty())
        assertTrue(lesson.teachers.isEmpty())
        assertNull(lesson.room)
        assertNull(lesson.building)
    }
    private fun source(status: Int = 200, reply: (JSONObject) -> String): SfeduData = SfeduData(PublicHttpClient(setOf("schedule.rdcenter.ru"), connection = { uri ->
        object : HttpURLConnection(uri.toURL()) {
            private val sent = ByteArrayOutputStream()
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getOutputStream() = sent
            override fun getResponseCode() = status
            override fun getInputStream() = ByteArrayInputStream(reply(JSONObject(sent.toString("UTF-8"))).toByteArray())
            override fun getContentLengthLong() = -1L
        }
    }))
}
