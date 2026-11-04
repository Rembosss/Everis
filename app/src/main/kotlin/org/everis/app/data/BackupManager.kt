package org.everis.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.everis.security.SnapshotBackup
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant

/** Whitelist only portable user data. Never export keystore keys or delivery/device journals. */
object BackupPayload {
    private val keys = setOf("groups", "group", "subgroup", "onlyMySubgroup", "performanceMode", "hapticsEnabled", "schedule", "tasks", "taskMetadata", "completedTasks",
        "palette", "themeMode", "blackTheme", "language", "completed", "notifications", "history", "lastVerified")

    fun encode(snapshot: JSONObject): ByteArray {
        val source = JSONObject(snapshot.toString())
        val payload = JSONObject()
        keys.forEach { if (source.has(it)) payload.put(it, source.get(it)) }
        payload.optJSONObject("notifications")?.remove("generation")
        return JSONObject().put("format", "everis-backup").put("version", 1).put("created", Instant.now().toString())
            .put("payload", payload).toString().toByteArray(Charsets.UTF_8)
    }

    fun validate(bytes: ByteArray): JSONObject {
        require(bytes.size in 1..SnapshotBackup.MAX_PLAINTEXT_BYTES)
        val root = portableJson(bytes)
        require(root.optString("format") == "everis-backup" && root.optInt("version") == 1)
        val original = root.getJSONObject("payload")
        val value = JSONObject()
        keys.forEach { if (original.has(it)) value.put(it, original.get(it)) }
        listOf("group", "tasks", "taskMetadata", "notifications").forEach { key -> if (value.has(key)) require(value.get(key) is JSONObject) }
        listOf("groups", "completedTasks", "history").forEach { key -> if (value.has(key)) require(value.get(key) is org.json.JSONArray) }
        listOf("subgroup", "schedule", "palette", "themeMode", "language", "lastVerified").forEach { key -> if (value.has(key)) require(value.get(key) is String) }
        listOf("onlyMySubgroup", "performanceMode", "hapticsEnabled", "blackTheme", "completed").forEach { key ->
            if (value.has(key)) require(value.get(key) is Boolean)
        }
        val group = value.selectedGroup()
        group?.let { require(it.id.isNotBlank() && it.id.length <= 200 && it.name.isNotBlank() && it.name.length <= 200) }
        require(!value.optBoolean("completed") || group != null)
        val raw = value.optString("schedule")
        if (raw.isNotBlank()) {
            require(group != null)
            require(RsueData.parseSchedule(raw, group.name).lessons.size <= 50_000)
        }
        val groups = value.optJSONArray("groups")
        require((groups?.length() ?: 0) <= 30_000)
        if (groups != null) for (i in 0 until groups.length()) {
            val item = groups.getJSONObject(i)
            require(item.getString("id").length in 1..200 && item.getString("name").length in 1..200)
        }
        val tasks = value.optJSONObject("tasks") ?: JSONObject()
        require(tasks.length() <= 10_000)
        tasks.keys().forEach { id -> require(id.length in 1..500 && tasks.getString(id).length <= 20_000) }
        val metadata = value.optJSONObject("taskMetadata") ?: JSONObject()
        require(metadata.length() <= 20_000)
        metadata.keys().forEach { id ->
            val item = metadata.getJSONObject(id)
            require(item.optString("subject").length <= 1000 && item.optString("disciplineId").length <= 1000)
            if (item.has("due")) require(java.time.ZonedDateTime.parse(item.getString("due")).year in 1900..2200)
        }
        val palette = value.optString("palette", "dynamic")
        require(palette in setOf("dynamic", "blue", "green") || Regex("#[0-9a-fA-F]{6}").matches(palette))
        require(value.optString("themeMode", "system") in setOf("system", "light", "dark"))
        require(value.optString("language", "ru") in setOf("ru", "uk", "be", "kk", "en"))
        require(value.optString("subgroup").length <= 200)
        require(!value.optBoolean("onlyMySubgroup") || value.optString("subgroup").isNotBlank())
        val completed = value.completedTasks()
        require(completed.size <= 10_000 && completed.all { it.length <= 500 })
        val history = value.optJSONArray("history")
        require((history?.length() ?: 0) <= 20)
        if (history != null) for (i in 0 until history.length()) {
            val entry = history.getJSONObject(i)
            Instant.parse(entry.getString("at"))
            require(entry.getJSONArray("changes").length() <= 100)
            val changes = entry.getJSONArray("changes")
            for (ci in 0 until changes.length()) {
                val change = changes.getJSONObject(ci)
                require(change.has("before") || change.has("after"))
                change.optJSONObject("before")?.let(::lessonFromJson)
                change.optJSONObject("after")?.let(::lessonFromJson)
            }
        }
        // Parse all projections before any encrypted transaction can replace current data.
        value.homeworkEntries(); value.scheduleHistory(); notificationPreferences(value)
        return value
    }

    fun restore(target: JSONObject, payload: JSONObject) {
        val generation = notificationPreferences(target).generation + 1
        keys.forEach { target.remove(it); if (payload.has(it)) target.put(it, payload.get(it)) }
        target.put("notifications", notificationPreferences(target).copy(generation = generation).toJson())
        target.remove("deliveries")
        target.put("changeCounter", 0).put("bedtimeReportedCounter", 0)
    }
}

class BackupManager(private val context: Context) {
    private val repository = DiaryRepository.get(context)
    suspend fun export(uri: Uri, scheduleOnly: Boolean = false) = withContext(Dispatchers.IO) {
        val snapshot = repository.read()
        val bytes = if (scheduleOnly) SchedulePayload.encode(snapshot) else BackupPayload.encode(snapshot)
        require(bytes.size <= SnapshotBackup.MAX_PLAINTEXT_BYTES)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }
    suspend fun restore(uri: Uri, scheduleOnly: Boolean = false, legacyPassword: CharArray? = null) = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= SnapshotBackup.MAX_FILE_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val legacy = bytes.take(8).toByteArray().contentEquals("EVRSBKP1".toByteArray())
        if (legacy && legacyPassword == null) throw LegacyBackupPasswordRequired()
        val plaintext = if (legacy) SnapshotBackup.decrypt(bytes, requireNotNull(legacyPassword)) else bytes
        try {
            if (scheduleOnly) {
                val payload = SchedulePayload.validate(plaintext)
                repository.update { SchedulePayload.restore(it, payload) }
            } else {
                val payload = BackupPayload.validate(plaintext)
                repository.update { BackupPayload.restore(it, payload) }
            }
        } finally { plaintext.fill(0); bytes.fill(0); legacyPassword?.fill('\u0000') }
    }
}

class LegacyBackupPasswordRequired : Exception()

/** A public-schedule document deliberately contains no homework, theme or notification settings. */
object SchedulePayload {
    fun encode(snapshot: JSONObject): ByteArray {
        val group = requireNotNull(snapshot.selectedGroup())
        val raw = snapshot.getString("schedule")
        RsueData.parseSchedule(raw, group.name)
        return JSONObject().put("format", "everis-schedule").put("version", 1)
            .put("created", Instant.now().toString()).put("group", JSONObject().put("id", group.id).put("name", group.name))
            .put("schedule", raw).toString().toByteArray(Charsets.UTF_8)
    }
    fun validate(bytes: ByteArray): JSONObject {
        require(bytes.size in 1..SnapshotBackup.MAX_PLAINTEXT_BYTES)
        val value = portableJson(bytes)
        require(value.optString("format") == "everis-schedule" && value.optInt("version") == 1)
        val group = requireNotNull(value.selectedGroup())
        require(group.id.isNotBlank() && group.id.length <= 200 && group.name.isNotBlank() && group.name.length <= 200)
        require(RsueData.parseSchedule(value.getString("schedule"), group.name).lessons.size <= 50_000)
        return JSONObject().put("group", value.getJSONObject("group")).put("schedule", value.getString("schedule"))
    }
    fun restore(target: JSONObject, payload: JSONObject) {
        val group = requireNotNull(payload.selectedGroup())
        val schedule = RsueData.parseSchedule(payload.getString("schedule"), group.name)
        val old = target.selectedGroup()
        val raw = target.optString("schedule")
        if (old != null && raw.isNotBlank()) rememberTaskMetadata(target, RsueData.parseSchedule(raw, old.name).lessons)
        val oldLessons = if (old != null && raw.isNotBlank()) RsueData.parseSchedule(raw, old.name).lessons else emptyList()
        val byId = schedule.lessons.associateBy { it.id }
        oldLessons.filter { it.start.toLocalDate() in schedule.days && (byId[it.id] == null || byId[it.id]?.disciplineId != it.disciplineId) }
            .forEach { target.archiveHomeworkForReview(it) }
        target.put("group", payload.getJSONObject("group")).put("schedule", payload.getString("schedule"))
        val groups = target.optJSONArray("groups") ?: org.json.JSONArray()
        val index = (0 until groups.length()).firstOrNull { groups.getJSONObject(it).getString("id") == group.id }
        if (index == null) groups.put(payload.getJSONObject("group")) else groups.put(index, payload.getJSONObject("group"))
        target.put("groups", groups)
        if (old != group || schedule.lessons.none { it.subgroup == target.optString("subgroup") }) target.put("subgroup", "").put("onlyMySubgroup", false)
        target.protectHomeworkBindings(schedule.lessons)
        rememberTaskMetadata(target, schedule.lessons)
        target.remove("history"); target.remove("lastVerified"); target.remove("deliveries")
        target.put("changeCounter", 0).put("bedtimeReportedCounter", 0)
        val prefs = notificationPreferences(target)
        target.put("notifications", prefs.copy(generation = prefs.generation + 1).toJson())
    }
}
