package org.everis.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupPayloadTest {
    private fun snapshot() = JSONObject().put("group", JSONObject().put("id", "1").put("name", "A-1"))
        .put("completed", true).put("palette", "#8855DD").put("language", "ru")
        .put("tasks", JSONObject().put("manual:one", "Example"))
        .put("notifications", JSONObject().put("generation", 42).put("daysAhead", 5))
    @Test fun portablePayloadRoundtripKeepsHomeworkAndExcludesDeviceState() {
        val original = snapshot().put("deliveries", JSONObject().put("private", "log")).put("notificationPermissionAsked", true)
        val decoded = BackupPayload.validate(BackupPayload.encode(original))
        assertEquals("Example", decoded.getJSONObject("tasks").getString("manual:one"))
        assertFalse(decoded.has("deliveries")); assertFalse(decoded.has("notificationPermissionAsked"))
        assertFalse(decoded.getJSONObject("notifications").has("generation"))
        assertEquals(42, original.getJSONObject("notifications").getInt("generation"))
    }
    @Test fun restoreReplacesPortableDataButPreservesLocalPermissionAndAcknowledgement() {
        val target = snapshot().put("notificationPermissionAsked", true).put("acknowledged", true).put("deliveries", JSONObject())
        val payload = BackupPayload.validate(BackupPayload.encode(snapshot().put("palette", "dynamic")))
        BackupPayload.restore(target, payload)
        assertEquals("dynamic", target.getString("palette"))
        assertTrue(target.getBoolean("notificationPermissionAsked")); assertTrue(target.getBoolean("acknowledged"))
        assertEquals(43L, notificationPreferences(target).generation); assertFalse(target.has("deliveries"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedFormatIsRejected() { BackupPayload.validate("{}".toByteArray()) }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedThemeIsRejected() { BackupPayload.validate(BackupPayload.encode(snapshot().put("themeMode", "unknown"))) }
    @Test(expected = IllegalArgumentException::class)
    fun completedSetupWithoutAGroupIsRejected() {
        val value = snapshot(); value.remove("group"); BackupPayload.validate(BackupPayload.encode(value))
    }
    @Test(expected = IllegalArgumentException::class)
    fun oversizeHomeworkIsRejectedBeforeRestore() {
        val value = snapshot().put("tasks", JSONObject().put("one", "x".repeat(20_001)))
        BackupPayload.validate(BackupPayload.encode(value))
    }
    @Test(expected = IllegalArgumentException::class)
    fun tooMuchHistoryIsRejectedBeforeRestore() {
        val value = snapshot().put("history", JSONArray().apply { repeat(21) { put(JSONObject()) } })
        BackupPayload.validate(BackupPayload.encode(value))
    }
    @Test(expected = IllegalArgumentException::class)
    fun responseForAnotherGroupIsRejected() {
        val raw = javaClass.getResource("/rsue-lessons.json")!!.readText()
        BackupPayload.validate(BackupPayload.encode(snapshot().put("schedule", raw)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun aBlankGroupCannotRestoreAsACompletedSetup() {
        BackupPayload.validate(BackupPayload.encode(snapshot().put("group", JSONObject().put("id", "").put("name", " "))))
    }
}
