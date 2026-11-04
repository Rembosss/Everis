package org.everis.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.everis.core.Lesson
import org.everis.core.LessonType
import org.everis.core.forSubgroup
import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class StudentGroup(val id: String, val name: String)
data class PublishedSchedule(val group: String, val days: Set<LocalDate>, val lessons: List<Lesson>) {
    fun forSubgroup(subgroup: String?) = if (subgroup.isNullOrBlank()) this else copy(lessons = lessons.forSubgroup(subgroup))
}

/** Public endpoints verified in the university's own IndexPage/axios JS bundles. */
class RsueData {
    private val client = PublicHttpClient(setOf("rasp-api.rsue.ru"))
    private fun fetch(path: String): String {
        // A short retry tolerates the source's intermittent TLS disconnects. TLS is never weakened.
        repeat(2) { attempt ->
            when (val response = client.get(URI("https://rasp-api.rsue.ru/api/v1/$path"))) {
                is HttpResult.Body -> return response.bytes.toString(Charsets.UTF_8)
                is HttpResult.Failure -> if (attempt == 1 || response.kind != HttpResult.Kind.NETWORK) {
                    error("Source unavailable: ${response.kind}")
                }
                HttpResult.NotModified -> error("Unexpected empty cache validation")
            }
        }
        error("Source unavailable")
    }
    fun groups(): List<StudentGroup> = parseGroups(fetch("schedule/search/"))
    fun schedule(group: StudentGroup): String {
        val encoded = URLEncoder.encode(group.name, "UTF-8").replace("+", "%20")
        return fetch("schedule/lessons/$encoded/").also { parseSchedule(it, group.name) }
    }

    companion object {
        val zone: ZoneId = ZoneId.of("Europe/Moscow")
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
        private val groupPattern = Regex("^[\\p{L}][\\p{L}\\p{N} _-]*-\\d[\\p{L}\\p{N} _-]*$")
        fun parseGroups(raw: String): List<StudentGroup> {
            validateJsonStructure(raw)
            val array = JSONArray(raw)
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val name = item.getString("name").trim()
                    if (groupPattern.matches(name)) add(StudentGroup(item.get("id").toString(), name))
                }
            }.distinctBy { it.id }.sortedBy { it.name }.also { require(it.isNotEmpty()) }
        }

        fun parseSchedule(raw: String, group: String): PublishedSchedule {
            validateJsonStructure(raw)
            val root = JSONObject(raw)
            require(root.getString("kind") == "Group" && root.getString("instance") == group)
            val days = linkedSetOf<LocalDate>()
            val lessons = linkedMapOf<String, Lesson>()
            val weeks = root.getJSONArray("weeks")
            require(weeks.length() > 0)
            for (wi in 0 until weeks.length()) {
                val weekDays = weeks.getJSONObject(wi).getJSONArray("days")
                for (di in 0 until weekDays.length()) {
                    val day = weekDays.getJSONObject(di)
                    // Undated recurrent templates must not become invented calendar occurrences.
                    require(!day.isNull("date")) { "Undated schedule is not supported yet" }
                    val date = LocalDate.parse(day.getString("date"), dateFormat)
                    require(date.year in 1900..2200)
                    days.add(date)
                    require(days.size <= 10_000)
                    val pairs = day.getJSONArray("pairs")
                    for (pi in 0 until pairs.length()) {
                        val pair = pairs.getJSONObject(pi)
                        val start = LocalTime.parse(pair.getString("startTime"))
                        val end = LocalTime.parse(pair.getString("endTime"))
                        val entries = pair.getJSONArray("lessons")
                        for (li in 0 until entries.length()) {
                            val entry = entries.getJSONObject(li)
                            val subject = entry.getString("subject").trim()
                            require(subject.isNotBlank() && subject.length <= 1000)
                            val kind = entry.optJSONObject("kind")?.optString("name").orEmpty().lowercase()
                            val audience = entry.optString("audience").takeUnless { it == "null" }.orEmpty()
                            val building = when {
                                '*' in audience -> "Островского, 62"
                                '#' in audience -> "Горького, 166"
                                '&' in audience -> "Тургеневская, 49"
                                else -> null // No guessed building for unmarked rooms.
                            }
                            val type = when {
                                "лек" in kind -> LessonType.LECTURE
                                "прак" in kind -> LessonType.PRACTICE
                                "лаб" in kind -> LessonType.LABORATORY
                                else -> LessonType.OTHER
                            }
                            val id = "rsue:$group:$date:${entry.get("id")}:${pair.get("id")}"
                            require(lessons.size < 50_000 || lessons.containsKey(id))
                            lessons[id] = Lesson(id, subject.lowercase(), subject,
                                date.atTime(start).atZone(zone),
                                (if (end <= start) date.plusDays(1) else date).atTime(end).atZone(zone),
                                type, listOfNotNull(entry.optJSONObject("teacher")?.optString("name")?.takeIf { it.isNotBlank() }),
                                building, audience.replace(Regex("[*#&]"), "").trim().ifEmpty { null },
                                subgroup = entry.optJSONObject("subgroup")?.takeIf { it.optInt("id") != 0 }
                                    ?.optString("name")?.trim()?.takeIf { it.isNotBlank() && it != "null" })
                        }
                    }
                }
            }
            require(days.isNotEmpty())
            return PublishedSchedule(group, days, lessons.values.sortedBy { it.start })
        }
    }
}
