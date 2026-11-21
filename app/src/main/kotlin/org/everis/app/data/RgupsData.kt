package org.everis.app.data

import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Anonymous requests made by rgups.ru/services/time and its own schedule.js. */
class RgupsData(private val client: PublicHttpClient = PublicHttpClient(setOf("www.rgups.ru", "rgups.ru"), maxBytes = 2 * 1024 * 1024)) {
    private val endpoint = URI("https://www.rgups.ru/services/time/")
    private fun body(result: HttpResult): String = when (result) {
        is HttpResult.Body -> result.bytes.toString(Charsets.UTF_8)
        else -> error("RGUPS public source unavailable")
    }
    private fun request(action: String, fields: Map<String, String>) = body(client.postForm(endpoint,
        fields + mapOf("action" to action, "edu-type" to "internal")))

    fun groups(): List<StudentGroup> {
        val faculties = facultyIds(body(client.get(endpoint)))
        // The site has a small, hierarchical catalog. Bound concurrency, and commit only a
        // complete successful catalog; a failed faculty must not silently delete groups.
        val executor = Executors.newFixedThreadPool(3)
        try {
            return executor.invokeAll(faculties.map { faculty -> Callable {
                val courses = courseIds(request("course", mapOf("fac-id" to faculty)))
                courses.flatMap { course -> parseGroups(request("groups", mapOf("fac-id" to faculty, "course-id" to course)), faculty, course) }
            } }).flatMap { it.get() }.distinctBy { it.id }.sortedBy { it.name }.also { require(it.size in 1..10_000) }
        } finally { executor.shutdownNow() }
    }
    fun schedule(group: StudentGroup): String {
        require(group.university == "rgups" && validId(group.id))
        val (_, faculty, course, id) = group.id.split(':')
        val html = request("timetable", mapOf("fac-id" to faculty, "course-id" to course, "group-id" to id))
        return canonicalSchedule(html, group.name, LocalDate.now(RsueData.zone))
    }

    companion object {
        private val numeric = Regex("[1-9][0-9]{0,8}")
        fun validId(id: String): Boolean = id.matches(Regex("rgups:[1-9][0-9]{0,8}:[1-9][0-9]{0,8}:[1-9][0-9]{0,8}"))
        fun facultyIds(html: String): List<String> = Jsoup.parse(html)
            .select(".schedule[data-edu-type=internal] .faculty a[data-fac-id]")
            .map { it.attr("data-fac-id").also { id -> require(numeric.matches(id)) } }.distinct()
            .also { require(it.size in 1..100) }
        fun courseIds(html: String): List<String> {
            val document = Jsoup.parse(html)
            require(document.select(".schedule-section[data-action=groups]").isNotEmpty())
            return document.select("a[data-course-id]").map { it.attr("data-course-id").also { id -> require(numeric.matches(id)) } }
                .distinct().also { require(it.size <= 10) }
        }
        fun parseGroups(html: String, faculty: String, course: String): List<StudentGroup> {
            require(numeric.matches(faculty) && numeric.matches(course))
            val document = Jsoup.parse(html)
            require(document.select(".schedule-section.groups[data-action=timetable]").isNotEmpty())
            return document.select("a[data-group-id]").map { element ->
                require(element.attr("data-fac-id") == faculty && element.attr("data-course-id") == course)
                val id = element.attr("data-group-id").also { require(numeric.matches(it)) }
                val name = element.text().trim().also { require(it.length in 1..200) }
                StudentGroup("rgups:$faculty:$course:$id", name, "rgups")
            }.distinctBy { it.id }.also { require(it.size <= 1000) }
        }

        private data class Cell(val text: String, val disabled: Boolean)
        private data class Row(val day: Int, val number: Int, val start: LocalTime, val end: LocalTime,
            val parity: String, val disabled: Boolean, val subject: String, val teacher: String, val room: String)

        /** The site publishes a cycle, NOT a dated semester. Expose only its next two weeks,
         * anchored to the site's active parity at fetch time, and retain this provenance. */
        fun canonicalSchedule(html: String, name: String, today: LocalDate): String {
            require(html.length in 1..2 * 1024 * 1024 && name.length in 1..200)
            val tables = Jsoup.parse(html).select(".schedule-section table.table")
            require(tables.size == 1) { "Missing public cycle" }
            val dayNames = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
            val timePattern = Regex("(\\d{1,2})[.:](\\d{2})\\s*[-–—]\\s*(\\d{1,2})[.:](\\d{2})")
            val carried = mutableMapOf<Int, Pair<Cell, Int>>()
            val rows = mutableListOf<Row>()
            val seenDays = mutableSetOf<Int>()
            var day = 0
            for (tr in tables.single().select("tr")) {
                val header = tr.selectFirst("th")
                if (header != null) {
                    val number = dayNames.indexOf(header.text().substringBefore('(').trim().lowercase(Locale.ROOT)) + 1
                    require(number > 0 && seenDays.add(number))
                    day = number; carried.clear(); continue
                }
                val elements = tr.children().filter { it.tagName() == "td" }
                if (elements.isEmpty()) {
                    // Empty rows in the source HTML still consume a rowspan row.
                    val remaining = carried.mapValues { it.value.first to it.value.second - 1 }.filterValues { it.second > 0 }
                    carried.clear(); carried.putAll(remaining)
                    continue
                }
                require(day > 0 && rows.size < 10_000)
                val cells = arrayOfNulls<Cell>(6)
                val next = mutableMapOf<Int, Pair<Cell, Int>>()
                carried.forEach { (column, remaining) ->
                    cells[column] = remaining.first
                    if (remaining.second > 1) next[column] = remaining.first to remaining.second - 1
                }
                var column = 0
                for (element in elements) {
                    while (column < 6 && cells[column] != null) column++
                    require(column < 6 && element.attr("colspan").let { it.isBlank() || it == "1" })
                    val cell = Cell(element.text().trim(), element.hasClass("disable"))
                    val span = element.attr("rowspan").let { if (it.isBlank()) 1 else it.toInt() }.also { require(it in 1..100) }
                    cells[column] = cell
                    if (span > 1) next[column] = cell to span - 1
                    column++
                }
                carried.clear(); carried.putAll(next)
                // The original page includes harmless empty <tr>s between rowspan rows.
                if (cells.all { it == null }) continue
                require(cells.all { it != null }) { "Incomplete timetable row" }
                val text = cells.map { requireNotNull(it).text }
                val number = text[0].toInt().also { require(it in 1..20) }
                val time = requireNotNull(timePattern.matchEntire(text[1])) { "Unknown bell time" }.groupValues
                val start = LocalTime.of(time[1].toInt(), time[2].toInt())
                val end = LocalTime.of(time[3].toInt(), time[4].toInt()).also { require(it > start) }
                val parity = text[2].lowercase(Locale.ROOT).also { require(it in setOf("над чертой", "под чертой", "обе недели")) }
                if (text[3].isBlank() || text[3] in setOf("—", "–", "-", "\u0097")) continue
                require(text[3].length <= 1000 && text[4].length <= 500 && text[5].length <= 200)
                rows += Row(day, number, start, end, parity, requireNotNull(cells[2]).disabled, text[3], text[4], text[5])
            }
            require(seenDays.containsAll((1..6).toList()) && rows.isNotEmpty()) { "Incomplete public cycle" }
            val active = rows.filter { !it.disabled && it.parity != "обе недели" }.map { it.parity }.toSet().singleOrNull()
                ?: error("Ambiguous public cycle parity")
            require(rows.filter { it.disabled }.all { it.parity != active })
            val monday = today.minusDays(today.dayOfWeek.value - 1L)
            val dates = JSONArray()
            repeat(14) { offset ->
                val date = monday.plusDays(offset.toLong())
                val parity = if (offset < 7) active else if (active == "над чертой") "под чертой" else "над чертой"
                val merged = linkedMapOf<String, JSONObject>()
                rows.filter { it.day == date.dayOfWeek.value && (it.parity == parity || it.parity == "обе недели") }.distinct().forEach { row ->
                    val kindMatch = Regex("\\s*\\((ЛЕК|ПРАК|ЛАБ)\\)\\s*$", RegexOption.IGNORE_CASE).find(row.subject)
                    val subject = if (kindMatch == null) row.subject else row.subject.removeRange(kindMatch.range).trim()
                    val kind = when (kindMatch?.groupValues?.get(1)?.uppercase(Locale.ROOT)) {
                        "ЛЕК" -> "Лекция"; "ПРАК" -> "Практика"; "ЛАБ" -> "Лабораторная"; else -> "Другое"
                    }
                    val subgroup = Regex("\\[([^\\[\\]]{1,60})]$").find(row.teacher)?.groupValues?.get(1).orEmpty()
                    val teacher = if (subgroup.isEmpty()) row.teacher else row.teacher.substringBeforeLast('[').trim()
                    // Content identity survives reordering of the source table.
                    val id = MessageDigest.getInstance("SHA-256").digest(listOf(row.day, row.number, subject, subgroup).joinToString("|").toByteArray())
                        .take(12).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
                    val entry = JSONObject().put("id", id).put("subject", subject).put("kind", JSONObject().put("name", kind))
                        .put("teacher", JSONObject().put("name", teacher)).put("audience", row.room)
                        .put("subgroup", JSONObject().put("id", if (subgroup.isEmpty()) 0 else 1).put("name", subgroup))
                    val key = "${row.number}:$id"
                    val previous = merged[key]
                    if (previous == null) {
                        entry.put("teachers", JSONArray(listOf(teacher).filter { it.isNotBlank() }))
                        merged[key] = JSONObject().put("id", row.number).put("startTime", row.start.toString()).put("endTime", row.end.toString())
                            .put("lessons", JSONArray().put(entry))
                    } else {
                        require(previous.getString("startTime") == row.start.toString() && previous.getString("endTime") == row.end.toString())
                        val teachers = previous.getJSONArray("lessons").getJSONObject(0).getJSONArray("teachers")
                        if (teacher.isNotBlank() && (0 until teachers.length()).none { teachers.getString(it) == teacher }) teachers.put(teacher)
                    }
                }
                dates.put(JSONObject().put("date", date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))).put("pairs", JSONArray(merged.values.toList())))
            }
            return JSONObject().put("source", "rgups").put("kind", "Group").put("instance", name)
                .put("cycleProjection", true).put("cycleAnchor", monday.toString()).put("cycleParity", active)
                .put("weeks", JSONArray().put(JSONObject().put("days", dates))).toString()
        }
    }
}
