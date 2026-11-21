package org.everis.app.data

import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Public read-only form endpoint and bell grid used by iubip.ru/schedule itself. */
class IubipData(private val client: PublicHttpClient = PublicHttpClient(setOf("iubip.ru", "www.iubip.ru"), maxBytes = 2 * 1024 * 1024)) {
    private val endpoint = URI("https://iubip.ru/local/templates/univer/include/schedule/ajax/read-file-groups.php")
    private fun body(result: HttpResult): String = when (result) {
        is HttpResult.Body -> result.bytes.toString(Charsets.UTF_8)
        else -> error("IUBIP public source unavailable")
    }
    fun groups(): List<StudentGroup> = parseGroups(body(client.postForm(endpoint, mapOf("do" to "groups"))))
    fun schedule(group: StudentGroup): String {
        require(group.university == "iubip" && group.id == groupId(group.name))
        val raw = body(client.postForm(endpoint, mapOf("do" to "schedule", "group" to group.name)))
        val javascript = body(client.get(URI("https://iubip.ru/local/templates/univer/include/schedule/js/ajax.js")))
        return canonicalSchedule(raw, group.name, parseBellTimes(javascript))
    }

    companion object {
        fun groupId(name: String): String = "iubip:" + MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
        private val dateFormat = DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT)
        private val periodFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
        fun parseGroups(raw: String): List<StudentGroup> {
            validateJsonStructure(raw)
            val root = JSONObject(raw)
            require(root.length() in 1..100)
            return root.keys().asSequence().flatMap { faculty ->
                root.getJSONObject(faculty).keys().asSequence().map { name ->
                    require(name.isNotBlank() && name.length <= 200)
                    StudentGroup(groupId(name), name, "iubip")
                }
            }.toList().distinctBy { it.id }.sortedBy { it.name }.also { require(it.size in 1..10_000) }
        }
        /** Fetch rather than guess the bell grid: a site change must not silently keep old times. */
        fun parseBellTimes(javascript: String): Map<Int, Pair<LocalTime, LocalTime>> {
            require(javascript.length in 1..2 * 1024 * 1024)
            val result = linkedMapOf<Int, Pair<LocalTime, LocalTime>>()
            val cells = Regex("""data="(\d{2}:\d{2})-(\d{2}:\d{2})"\s+data-td="d1l(\d{1,2})"""")
            for (match in cells.findAll(javascript)) {
                val number = match.groupValues[3].toInt().also { require(it in 1..20) }
                val time = LocalTime.parse(match.groupValues[1]) to LocalTime.parse(match.groupValues[2])
                require(time.second > time.first)
                require(result[number] == null || result[number] == time)
                result[number] = time
            }
            require(result.isNotEmpty() && result.keys == (1..result.size).toSet())
            return result
        }
        fun canonicalSchedule(raw: String, name: String, times: Map<Int, Pair<LocalTime, LocalTime>>): String {
            validateJsonStructure(raw)
            val root = JSONObject(raw)
            require(root.length() == 1 && root.has(name)) { "Wrong public timetable identity" }
            val group = root.getJSONArray(name).also { require(it.length() == 2) }
            val weeks = group.getJSONObject(1).also { require(it.length() in 1..60) }
            val days = sortedMapOf<LocalDate, MutableMap<String, JSONObject>>()
            fun groups(value: String) = value.split(',').map { it.trim().replace(Regex("\\s+"), " ") }.toSet()
            val expectedGroups = groups(name)
            var count = 0
            for (key in weeks.keys()) {
                val week = weeks.getJSONArray(key).also { require(it.length() == 2) }
                val metadata = week.getJSONObject(0)
                val period = metadata.getString("per").split(" - ")
                require(period.size == 2)
                val first = LocalDate.parse(period[0].trim(), periodFormat)
                require(first.year in 1900..2200 && first.dayOfWeek.value == 1)
                require(LocalDate.parse(period[1].trim(), periodFormat) == first.plusDays(6))
                require(metadata.getJSONArray("days").length() == 7)
                repeat(7) { days.getOrPut(first.plusDays(it.toLong())) { linkedMapOf() } }
                val schedule = week.getJSONObject(1)
                for (dayKey in schedule.keys()) {
                    val dayNumber = dayKey.toInt().also { require(it in 1..7) }
                    val day = first.plusDays(dayNumber - 1L)
                    val pairs = schedule.getJSONObject(dayKey)
                    for (pairKey in pairs.keys()) {
                        val pairNumber = pairKey.toInt()
                        val time = requireNotNull(times[pairNumber]) { "Unknown bell slot" }
                        val rows = pairs.getJSONArray(pairKey)
                        for (index in 0 until rows.length()) {
                            require(++count <= 50_000)
                            val row = rows.getJSONObject(index)
                            require(LocalDate.parse(row.getString("DATE").trim(), dateFormat) == day)
                            require(row.getString("DAY").trim().toInt() == dayNumber && row.getString("LES").trim().toInt() == pairNumber)
                            val rowGroups = groups(row.getString("GROUP"))
                            require(rowGroups.isNotEmpty() && rowGroups.all { it in expectedGroups })
                            val subject = row.getString("SUBJECT").trim().also { require(it.length in 1..1000) }
                            val id = row.getString("ID").trim().also { require(it.length in 1..200) }
                            val subgroup = row.getString("SUBG").trim().toInt().also { require(it in 0..100) }
                            val type = when (row.optString("SUBJ_TYPE").trim().lowercase(Locale.ROOT).removeSuffix(".")) {
                                "лек", "лекция" -> "Лекция"
                                "пр", "практ", "практика" -> "Практика"
                                "лаб", "лабораторная" -> "Лабораторная"
                                else -> "Другое"
                            }
                            fun text(key: String) = (row.opt(key) as? String)?.trim()?.takeUnless { it == "-" }.orEmpty()
                            val lesson = JSONObject().put("id", "$id:$subgroup").put("subject", subject)
                                .put("kind", JSONObject().put("name", type)).put("teacher", JSONObject().put("name", text("NAME")))
                                .put("audience", text("AUD")).put("group", text("GROUP"))
                                .put("cancelled", row.optInt("deleted") != 0 || row.optBoolean("deleted"))
                                .put("subgroup", JSONObject().put("id", subgroup).put("name", if (subgroup > 0) subgroup.toString() else ""))
                            val pair = JSONObject().put("id", pairNumber).put("startTime", time.first.toString())
                                .put("endTime", time.second.toString()).put("lessons", JSONArray().put(lesson))
                            val previous = days.getValue(day).putIfAbsent("$id:$subgroup", pair)
                            require(previous == null || previous.toString() == pair.toString()) { "Conflicting timetable rows" }
                        }
                    }
                }
            }
            val dates = JSONArray()
            days.forEach { (day, pairs) -> dates.put(JSONObject().put("date", day.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")))
                .put("pairs", JSONArray(pairs.values.toList()))) }
            return JSONObject().put("source", "iubip").put("kind", "Group").put("instance", name)
                .put("weeks", JSONArray().put(JSONObject().put("days", dates))).toString()
        }
    }
}
