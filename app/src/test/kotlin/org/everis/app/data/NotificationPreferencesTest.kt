package org.everis.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.everis.core.NotificationPreferences
import java.time.LocalTime

class NotificationPreferencesTest {
    @Test fun olderSnapshotsReceiveTheDocumentedNotificationDefaults() {
        assertEquals(NotificationPreferences(), notificationPreferences(JSONObject()))
    }
    @Test fun allUserSettingsSurviveAnEncryptedSnapshotPayloadRoundTrip() {
        val preferences = NotificationPreferences(periodicEnabled = false, intervalMinutes = 240,
            changesEnabled = false, bedtimeEnabled = false, bedtime = LocalTime.of(23, 15),
            homeworkEnabled = false, homeworkTime = LocalTime.of(17, 40), daysAhead = 5,
            dailyHomework = true, onDueDay = true, generation = 18)
        val payload = JSONObject(JSONObject().put("notifications", preferences.toJson()).toString())
        assertEquals(preferences, notificationPreferences(payload))
    }
    @Test fun invalidClockStringsFallBackWithoutDisablingAllNotifications() {
        val payload = JSONObject().put("notifications", JSONObject().put("bedtime", "25:99").put("homeworkTime", ""))
        val settings = notificationPreferences(payload)
        assertEquals(LocalTime.of(22, 0), settings.bedtime)
        assertEquals(LocalTime.of(19, 0), settings.homeworkTime)
    }
    @Test fun importedNumericSettingsRespectSupportedSchedulingBounds() {
        val payload = JSONObject().put("notifications", JSONObject().put("intervalMinutes", 1).put("daysAhead", 99).put("generation", -4))
        val settings = notificationPreferences(payload)
        assertEquals(120L, settings.intervalMinutes)
        assertEquals(14, settings.daysAhead)
        assertEquals(0L, settings.generation)
    }
}
