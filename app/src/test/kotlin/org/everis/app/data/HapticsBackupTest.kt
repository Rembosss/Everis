package org.everis.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HapticsBackupTest {
    @Test fun oldBackupWithoutHapticsRemainsCompatible() {
        val restored = BackupPayload.validate(BackupPayload.encode(JSONObject()))
        assertTrue(restored.optBoolean("hapticsEnabled", true))
    }
    @Test fun disabledHapticsSurviveTransferWithoutImportingDeviceJournals() {
        val snapshot = JSONObject().put("hapticsEnabled", false).put("deliveries", JSONObject().put("private", true))
        val restored = BackupPayload.validate(BackupPayload.encode(snapshot))
        assertFalse(restored.getBoolean("hapticsEnabled"))
        assertFalse(restored.has("deliveries"))
    }
    @Test fun malformedHapticsTypeCannotReplaceAValidSnapshot() {
        val document = JSONObject(String(BackupPayload.encode(JSONObject())))
        document.getJSONObject("payload").put("hapticsEnabled", "false")
        try { BackupPayload.validate(document.toString().toByteArray()); fail() }
        catch (_: IllegalArgumentException) { }
    }
}
