package org.everis.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Public university metadata only, separate from private homework and backup transactions. */
class TeacherRepository(context: Context) {
    private val directory = File(context.filesDir, "public-teachers").apply { mkdirs() }
    private val assetManager = context.assets
    private val diskLock = Any()
    private val catalogLocks = ConcurrentHashMap<String, Any>()
    private val catalogMemory = ConcurrentHashMap<String, List<TeacherRef>>()
    private val indexes = ConcurrentHashMap<String, Map<String, String>>()
    private val officialNames by lazy {
        assetManager.open("rsue-teachers.json").bufferedReader().use { reader ->
            val rows = JSONArray(reader.readText())
            (0 until rows.length()).map { rows.getJSONObject(it).getString("name") }
        }
    }
    private val rsueIndex by lazy { TeacherNames.index(officialNames) }
    fun fullName(name: String, university: String): String = TeacherNames.resolve(name,
        if (university == "rsue") rsueIndex else indexes.getOrPut(university) { TeacherNames.index(cachedCatalog(university).map { it.name }) })
    fun cachedCatalog(university: String): List<TeacherRef> {
        require(university in UniversitySources.universities)
        catalogMemory[university]?.let { return it }
        val rows = read("catalog-$university.json")?.let { runCatching { validateJsonStructure(it); JSONArray(it) }.getOrNull() } ?: return emptyList()
        if (rows.length() > 30_000) return emptyList()
        return (0 until rows.length()).mapNotNull { i -> runCatching {
            val row = rows.getJSONObject(i)
            val id = row.getString("id").also { require(it.matches(Regex("[1-9][0-9]{0,11}"))) }
            val sourceName = row.getString("sourceName").also { require(it.length in 3..180) }
            val name = row.getString("name").also { require(it.length in 3..180) }
            TeacherRef(id, sourceName, name, university)
        }.getOrNull() }.also { catalogMemory[university] = it }
    }
    fun refreshCatalog(university: String, force: Boolean = false): List<TeacherRef> {
        require(university in UniversitySources.universities)
        return synchronized(catalogLocks.computeIfAbsent(university) { Any() }) {
            val previous = cachedCatalog(university)
            val age = System.currentTimeMillis() - File(directory, "catalog-$university.json").lastModified()
            if (!force && previous.isNotEmpty() && age in 0 until 6 * 60 * 60 * 1000L) return@synchronized previous
            loadCatalog(university)
        }
    }
    private fun loadCatalog(university: String): List<TeacherRef> {
        val list = UniversitySources.teachers(university).map { teacher ->
            if (university == "rsue") teacher.copy(name = TeacherNames.resolve(teacher.sourceName, rsueIndex)) else teacher
        }.sortedBy { it.name }
        require(list.isNotEmpty())
        val rows = JSONArray()
        list.forEach { rows.put(JSONObject().put("id", it.id).put("sourceName", it.sourceName).put("name", it.name)) }
        write("catalog-$university.json", rows.toString())
        catalogMemory[university] = list
        indexes[university] = TeacherNames.index(list.map { it.name })
        return list
    }
    private fun scheduleFile(teacher: TeacherRef): String {
        require(teacher.university in UniversitySources.universities && teacher.id.matches(Regex("[0-9]{1,12}")))
        return "schedule-${teacher.university}-${teacher.id}.json"
    }
    fun cachedSchedule(teacher: TeacherRef): PublishedSchedule? = read(scheduleFile(teacher))?.let { raw ->
        runCatching { RsueData.parseSchedule(raw, teacher.sourceName, "Teacher").also { require(it.university == teacher.university) } }.getOrNull()
    }
    fun refreshSchedule(teacher: TeacherRef): PublishedSchedule {
        val raw = UniversitySources.teacherSchedule(teacher)
        val result = RsueData.parseSchedule(raw, teacher.sourceName, "Teacher").also { require(it.university == teacher.university) }
        write(scheduleFile(teacher), raw)
        synchronized(diskLock) {
            directory.listFiles { file -> file.name.startsWith("schedule-") && file.extension == "json" }
                ?.sortedByDescending { it.lastModified() }?.drop(12)?.forEach { it.delete() }
        }
        return result
    }
    private fun read(name: String): String? = synchronized(diskLock) {
        runCatching {
            val file = File(directory, name)
            val backup = File(directory, "$name.bak")
            if (file.length() > 8 * 1024 * 1024 || backup.length() > 8 * 1024 * 1024) null
            else AtomicFile(file).readFully().also { require(it.size <= 8 * 1024 * 1024) }.toString(Charsets.UTF_8)
        }.getOrNull()
    }
    private fun write(name: String, text: String) = synchronized(diskLock) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 8 * 1024 * 1024)
        val file = AtomicFile(File(directory, name)); val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
