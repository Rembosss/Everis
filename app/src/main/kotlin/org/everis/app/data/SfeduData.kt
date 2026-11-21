package org.everis.app.data

import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Public service embedded by ictis.sfedu.ru/schedule. No Modeus login/cookies are used. */
class SfeduData(private val client: PublicHttpClient = PublicHttpClient(setOf("schedule.rdcenter.ru"), maxBytes = 8 * 1024 * 1024)) {
    private fun body(result: HttpResult): String = when (result) {
        is HttpResult.Body -> result.bytes.toString(Charsets.UTF_8)
        else -> error("SFEDU public timetable unavailable")
    }
    fun groups(query: String = ""): List<StudentGroup> = parseGroups(body(client.get(URI(
        "https://schedule.rdcenter.ru/api/ictis/groups?query=${URLEncoder.encode(query, "UTF-8")}&size=100"))))
    fun teachers(query: String = ""): List<TeacherRef> = parseTeachers(body(client.get(URI(
        "https://schedule.rdcenter.ru/api/schedule/searchemployee?fullName=${URLEncoder.encode(query, "UTF-8")}"))))

    fun schedule(group: StudentGroup): String {
        require(group.university == "sfedu")
        return schedule(group.id.removePrefix("sfedu:"), group.name, "Group")
    }
    fun teacherSchedule(teacher: TeacherRef): String {
        require(teacher.university == "sfedu")
        return schedule(teacher.id, teacher.sourceName, "Teacher")
    }
    private fun schedule(personId: String, name: String, kind: String): String {
        require(isUuid(personId))
        val today = LocalDate.now(RsueData.zone)
        val first = LocalDate.of(if (today.monthValue >= 9) today.year else today.year - 1, 9, 1)
        val pages = range(personId, first, first.plusYears(1))
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        // Only the explicitly refreshed current week confirms empty weekdays. The year query
        // supplies every published occurrence without inventing the rest of the academic calendar.
        val current = runCatching { range(personId, monday, monday.plusWeeks(1)) }.getOrNull()
        return canonicalSchedule(pages, name, kind, current, if (current != null) monday else null)
    }
    private fun range(id: String, first: LocalDate, until: LocalDate): List<String> {
        val result = mutableListOf<String>()
        var totalPages = 1
        var totalElements: Int? = null
        var bytes = 0L
        val ids = hashSetOf<String>()
        var page = 0
        while (page < totalPages) {
            val request = JSONObject().put("size", 500).put("page", page)
                .put("timeMin", first.atStartOfDay(RsueData.zone).toOffsetDateTime().toString())
                .put("timeMax", until.atStartOfDay(RsueData.zone).toOffsetDateTime().toString())
                .put("attendeePersonId", JSONArray().put(id))
            val response = client.postJson(URI("https://schedule.rdcenter.ru/api/ictis"), request.toString())
            // Verified public API contract: a genuinely empty range is HTTP 204, not empty JSON.
            // An empty/malformed 200 response must still fail and preserve the previous cache.
            val raw = if (response is HttpResult.Body && response.statusCode == 204) {
                require(response.bytes.isEmpty())
                JSONObject().put("_embedded", JSONObject().put("events", JSONArray()))
                    .put("page", JSONObject().put("size", 500).put("number", page).put("totalElements", 0).put("totalPages", 0)).toString()
            } else body(response)
            val parsed = parsePage(raw)
            val pagination = parsed.getJSONObject("page")
            require(pagination.getInt("number") == page)
            val count = pagination.getInt("totalElements").also { require(it in 0..20_000) }
            val pages = pagination.getInt("totalPages").also { require(it in 0..40) }
            val size = pagination.getInt("size").also { require(it in 1..1000) }
            require(pages == (count + size - 1) / size)
            if (totalElements != null) require(totalElements == count && totalPages == pages)
            totalElements = count; totalPages = pages
            bytes += raw.toByteArray(Charsets.UTF_8).size
            require(bytes <= 16 * 1024 * 1024)
            val events = parsed.getJSONObject("_embedded").getJSONArray("events")
            require(events.length() <= size)
            for (index in 0 until events.length()) {
                val event = events.getJSONObject(index)
                require(ids.add(event.getString("id"))) { "Repeated page/event in public timetable" }
                val day = OffsetDateTime.parse(event.getString("start")).atZoneSameInstant(RsueData.zone).toLocalDate()
                require(day >= first && day < until)
            }
            result += raw
            page++
        }
        require(ids.size == totalElements)
        return result
    }
    companion object {
        fun isUuid(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
        fun parseGroups(raw: String): List<StudentGroup> {
            validateJsonStructure(raw)
            val rows = JSONArray(raw); require(rows.length() <= 100)
            return (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
                val id = row.getString("personId").lowercase(Locale.ROOT).also { require(isUuid(it)) }
                val name = row.getString("name").trim().also { require(it.length in 1..200) }
                StudentGroup("sfedu:$id", name, "sfedu")
            }.distinctBy { it.id }.sortedBy { it.name }
        }
        fun parseTeachers(raw: String): List<TeacherRef> {
            validateJsonStructure(raw)
            val rows = JSONArray(raw); require(rows.length() <= 1000)
            return (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
                val id = row.getString("person_id").lowercase(Locale.ROOT).also { require(isUuid(it)) }
                val name = row.getString("name").trim().also { require(it.length in 3..180) }
                TeacherRef(id, name, name, "sfedu")
            }.distinctBy { it.id }
        }
        private fun parsePage(raw: String): JSONObject {
            validateJsonStructure(raw)
            return JSONObject(raw).also { it.getJSONObject("_embedded").getJSONArray("events"); it.getJSONObject("page") }
        }
        private fun JSONObject.rows(name: String): List<JSONObject> = (optJSONArray(name) ?: JSONArray()).let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
        }
        private fun JSONObject.link(relation: String): String = optJSONObject("_links")?.optJSONObject(relation)
            ?.optString("href")?.substringAfterLast('/').orEmpty()
        private fun JSONObject.text(name: String): String = (opt(name) as? String)?.trim().orEmpty()

        fun canonicalSchedule(pages: List<String>, name: String, kind: String = "Group",
            currentPages: List<String>? = null, currentWeek: LocalDate? = null): String {
            require(kind in setOf("Group", "Teacher") && pages.isNotEmpty())
            val days = sortedMapOf<LocalDate, MutableMap<String, JSONObject>>()
            fun ingest(blocks: List<String>) {
                for (raw in blocks) {
                    val embedded = parsePage(raw).getJSONObject("_embedded")
                    val courses = embedded.rows("course-unit-realizations").associateBy { it.getString("id") }
                    val people = embedded.rows("persons").associateBy { it.getString("id") }
                    val rooms = embedded.rows("rooms").associateBy { it.getString("id") }
                    val buildings = embedded.rows("buildings").associateBy { it.getString("id") }
                    val teachers = embedded.rows("event-attendees").filter { it.optString("roleId") == "TEACH" }.groupBy { it.link("event") }
                    val roomLinks = embedded.rows("event-rooms").groupBy { it.link("event") }
                    val locations = embedded.rows("event-locations").associateBy { it.optString("eventId") }
                    for (event in embedded.rows("events")) {
                        val id = event.getString("id").also { require(isUuid(it)) }
                        val start = OffsetDateTime.parse(event.getString("start")).atZoneSameInstant(RsueData.zone)
                        val end = OffsetDateTime.parse(event.getString("end")).atZoneSameInstant(RsueData.zone)
                        require(start.year in 1900..2200 && end > start && end <= start.plusDays(1))
                        val course = event.link("course-unit-realization")
                        val subject = (if (course.isNotBlank()) requireNotNull(courses[course]).getString("name")
                            else event.getString("name")).trim().also { require(it.length in 1..1000) }
                        val type = when (event.optString("typeId")) {
                            "LECT" -> "Лекция"
                            "SEMI", "PRACT" -> "Практика"
                            "LAB" -> "Лабораторная"
                            else -> "Другое"
                        }
                        val staff = teachers[id].orEmpty().mapNotNull { people[it.link("person")]?.text("fullName") }
                            .filter { it.isNotBlank() && it != "modeus_system_user" }.distinct()
                        val eventRooms = roomLinks[id].orEmpty().mapNotNull { rooms[it.link("room")] }
                        val room = eventRooms.map { it.text("name") }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                            .ifBlank { locations[id]?.text("customLocation").orEmpty() }
                        val building = eventRooms.mapNotNull { it.optJSONObject("building") ?: buildings[it.link("building")] }
                            .map { it.text("address").ifBlank { it.text("name") } }.filter { it.isNotBlank() }.distinct().joinToString("; ")
                        val lesson = JSONObject().put("id", id).put("subject", subject)
                            .put("kind", JSONObject().put("name", type)).put("teachers", JSONArray(staff))
                            .put("audience", room).put("building", building).put("group", event.text("description"))
                            .put("cancelled", event.optJSONObject("holdingStatus")?.optString("id") in setOf("CANCELLED", "CANCELED"))
                        val pair = JSONObject().put("id", "${start.toLocalTime()}-${end.toLocalTime()}")
                            .put("startTime", start.toLocalTime().toString()).put("endTime", end.toLocalTime().toString())
                            .put("lessons", JSONArray().put(lesson))
                        days.getOrPut(start.toLocalDate()) { linkedMapOf() }[id] = pair
                    }
                }
            }
            ingest(pages)
            if (currentPages != null && currentWeek != null) {
                repeat(7) { days[currentWeek.plusDays(it.toLong())] = linkedMapOf() }
                ingest(currentPages)
            }
            val dates = JSONArray()
            days.forEach { (day, pairs) -> dates.put(JSONObject().put("date", day.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")))
                .put("pairs", JSONArray(pairs.values.toList()))) }
            return JSONObject().put("source", "sfedu").put("kind", kind).put("instance", name).put("empty", days.isEmpty())
                .put("weeks", JSONArray().put(JSONObject().put("days", dates))).toString()
        }
    }
}
