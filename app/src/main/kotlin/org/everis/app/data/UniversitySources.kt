package org.everis.app.data

import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Source identity travels with a group; old snapshots default to RSUE without rewriting IDs. */
data class TeacherRef(val id: String, val sourceName: String, val name: String, val university: String)

object UniversitySources {
    val universities = listOf("rsue", "dstu")
    fun groups(university: String): List<StudentGroup> = when (university) {
        "rsue" -> RsueData().groups()
        "dstu" -> DstuData().groups()
        else -> error("Unsupported public source")
    }
    fun schedule(group: StudentGroup): String = when (group.university) {
        "rsue" -> RsueData().schedule(group)
        "dstu" -> DstuData().schedule(group)
        else -> error("Unsupported public source")
    }
    fun teachers(university: String): List<TeacherRef> = when (university) {
        "rsue" -> RsueData().teachers()
        "dstu" -> DstuData().teachers()
        else -> error("Unsupported public source")
    }
    fun teacherSchedule(teacher: TeacherRef): String = when (teacher.university) {
        "rsue" -> RsueData().teacherSchedule(teacher)
        "dstu" -> DstuData().teacherSchedule(teacher)
        else -> error("Unsupported public source")
    }
}

/** Public DSTU APIs, checked against edu.donstu.ru. No credentials or app intermediary. */
class DstuData {
    private val client = PublicHttpClient(setOf("edu.donstu.ru"), maxBytes = 6 * 1024 * 1024)
    private fun get(path: String): String = when (val result = client.get(URI("https://edu.donstu.ru/api/$path"))) {
        is HttpResult.Body -> result.bytes.toString(Charsets.UTF_8)
        else -> error("DSTU public source unavailable")
    }
    fun groups() = parseGroups(get("raspGrouplist"))
    fun teachers() = parseTeachers(get("raspTeacherlist"))
    fun schedule(group: StudentGroup): String {
        require(group.university == "dstu")
        val id = group.id.removePrefix("dstu:").also { require(it.matches(Regex("[0-9]{1,12}"))) }
        val all = get("Rasp?idGroup=$id")
        // Default provides all dated rows; an explicit current range also confirms empty days.
        val current = get("Rasp?idGroup=$id&sdate=${LocalDate.now(RsueData.zone)}")
        return canonicalSchedule(all, group.name, "Group", current, id.toLong())
    }
    fun teacherSchedule(teacher: TeacherRef): String {
        require(teacher.id.matches(Regex("[0-9]{1,12}")))
        val all = get("Rasp?idTeacher=${teacher.id}")
        val current = get("Rasp?idTeacher=${teacher.id}&sdate=${LocalDate.now(RsueData.zone)}")
        return canonicalSchedule(all, teacher.sourceName, "Teacher", current, teacher.id.toLong())
    }
    companion object {
        private fun response(raw: String): JSONObject {
            validateJsonStructure(raw)
            return JSONObject(raw).also { require(it.optInt("state") == 1) }
        }
        fun parseGroups(raw: String): List<StudentGroup> {
            val rows = response(raw).getJSONArray("data")
            require(rows.length() <= 30_000)
            return (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
                val id = row.getLong("id").also { require(it > 0) }
                val name = row.getString("name").trim().also { require(it.length in 1..200) }
                StudentGroup("dstu:$id", name, "dstu")
            }.distinctBy { it.id }.sortedBy { it.name }.also { require(it.isNotEmpty()) }
        }
        fun parseTeachers(raw: String): List<TeacherRef> {
            val rows = response(raw).getJSONArray("data")
            require(rows.length() <= 30_000)
            return (0 until rows.length()).map { rows.getJSONObject(it) }.mapNotNull { row ->
                val name = row.optString("name").trim()
                val id = row.optLong("id")
                if (id <= 0 || name.length !in 3..180) null else TeacherRef(id.toString(), name, name, "dstu")
            }.distinctBy { it.id }.sortedBy { it.name }
        }
        /** Convert the source contract once; existing diff, backup and homework logic stays shared. */
        fun canonicalSchedule(raw: String, instance: String, kind: String = "Group", current: String? = null, expectedId: Long? = null): String {
            require(kind in setOf("Group", "Teacher"))
            val blocks = listOfNotNull(raw, current).map { response(it).getJSONObject("data") }
            val days = sortedMapOf<LocalDate, MutableMap<String, JSONObject>>()
            for ((blockIndex, block) in blocks.withIndex()) {
                require(!block.optBoolean("isCyclicalSchedule")) { "Undated cyclical templates are not supported" }
                var groupConfirmed = false
                block.optJSONObject("info")?.let { info ->
                    if (kind == "Group") info.optJSONObject("group")?.let { group ->
                        group.optString("name").takeIf { it.isNotBlank() }?.let { require(it == instance) { "Schedule belongs to a different group" } }
                        if (expectedId != null && !group.isNull("groupID")) require(group.getLong("groupID") == expectedId)
                        groupConfirmed = group.optString("name") == instance &&
                            expectedId != null && group.optLong("groupID") == expectedId
                    }
                    val first = runCatching { LocalDate.parse(info.getString("date").take(10)) }.getOrNull()
                    val until = runCatching { LocalDate.parse(info.getString("lastDate").take(10)) }.getOrNull()
                    if (first != null && until != null && until > first && until <= first.plusDays(14)) {
                        var date: LocalDate = first
                        // A fresh explicit week is authoritative, including newly cancelled classes.
                        while (date < until) {
                            if (blockIndex > 0) days[date] = linkedMapOf() else days.getOrPut(date) { linkedMapOf() }
                            date = date.plusDays(1)
                        }
                    }
                }
                val rows = block.getJSONArray("rasp"); require(rows.length() <= 50_000)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    if (expectedId != null) {
                        val rowId = row.getLong(if (kind == "Group") "кодГруппы" else "кодПреподавателя")
                        // The official group response uses zero for some shared/sport classes.
                        // Accept that only with both matching response metadata and an exact group label.
                        val sharedGroup = kind == "Group" && rowId == 0L && groupConfirmed &&
                            row.optString("группа").split(',', ';', '\n').any { it.trim() == instance }
                        require(rowId == expectedId || sharedGroup) { "Wrong public timetable identity" }
                    }
                    val date = LocalDate.parse(row.getString("дата").take(10)); require(date.year in 1900..2200)
                    val start = LocalTime.parse(row.getString("начало")); val end = LocalTime.parse(row.getString("конец"))
                    val title = row.getString("дисциплина").trim(); require(title.length in 1..1000)
                    val subgroupSuffix = Regex("(?iu)(?:,\\s*)?(?:п\\s*[/\\.]\\s*г\\.?|подгруппа)\\s*(\\d+)\\s*$").find(title)
                    val explicitSubgroup = row.optInt("номерПодгруппы")
                    val subgroup = explicitSubgroup.takeIf { it > 0 }
                        ?: subgroupSuffix?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 0
                    val subject = if (subgroupSuffix != null) title.substring(0, subgroupSuffix.range.first).trimEnd(' ', ',') else title
                    val match = Regex("(?iu)^(лек(?:ция)?|лаб(?:ораторная)?|пр(?:актика)?|практ?)[. ]+(.+)$").matchEntire(subject)
                    val form = match?.groupValues?.get(1).orEmpty().lowercase()
                    val type = when { form.startsWith("лек") -> "Лекция"; form.startsWith("лаб") -> "Лабораторная"; form.startsWith("пр") -> "Практика"; else -> "Другое" }
                    val room = row.optString("аудитория").takeUnless { it == "null" }.orEmpty()
                    val code = row.get("код").toString()
                    val teacher = row.optString("фиоПреподавателя").takeIf { it.isNotBlank() && it != "null" }
                        ?: row.optString("преподаватель")
                    val lesson = JSONObject().put("id", code).put("subject", match?.groupValues?.get(2) ?: subject)
                        .put("kind", JSONObject().put("name", type)).put("teacher", JSONObject().put("id", row.optLong("кодПреподавателя")).put("name", teacher))
                        .put("audience", room).put("group", row.optString("группа"))
                        .put("subgroup", JSONObject().put("id", subgroup).put("name", if (subgroup > 0) subgroup.toString() else ""))
                    // Preserve the university's room code; do not invent campus street addresses.
                    val entry = JSONObject().put("id", "$start-$end").put("startTime", start.toString()).put("endTime", end.toString())
                        .put("lessons", JSONArray().put(lesson))
                    days.getOrPut(date) { linkedMapOf() }[code] = entry
                }
            }
            val dates = JSONArray()
            days.forEach { (date, pairs) -> dates.put(JSONObject().put("date", date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")))
                .put("pairs", JSONArray(pairs.values.toList()))) }
            return JSONObject().put("source", "dstu").put("kind", kind).put("instance", instance).put("empty", days.isEmpty())
                .put("weeks", JSONArray().put(JSONObject().put("days", dates))).toString()
        }
    }
}

fun StudentGroup.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("university", university)
fun studentGroup(json: JSONObject): StudentGroup = StudentGroup(json.getString("id"), json.getString("name"),
    json.optString("university").takeIf { it.isNotBlank() } ?: if (json.getString("id").startsWith("dstu:")) "dstu" else "rsue")

/** A portable schedule cannot silently change provider while keeping a familiar group name. */
fun parseStudentSchedule(raw: String, group: StudentGroup): PublishedSchedule = RsueData.parseSchedule(raw, group.name).also {
    require(it.university == group.university) { "Timetable source does not match the selected university" }
}
