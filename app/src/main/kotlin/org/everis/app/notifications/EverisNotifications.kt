package org.everis.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.net.toUri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.everis.app.MainActivity
import org.everis.app.R
import org.everis.app.uiLocale
import org.everis.app.data.*
import org.everis.core.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class EverisNotifications(private val application: Context) {
    private val repository = DiaryRepository.get(application)
    private fun localized(json: JSONObject): Context {
        val language = json.optString("language")
        if (language !in setOf("ru", "uk", "be", "kk", "en")) return application
        return application.createConfigurationContext(Configuration(application.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }

    fun permissionGranted(): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(application).areNotificationsEnabled()

    fun createChannels(json: JSONObject) {
        val context = localized(json)
        val manager = application.getSystemService(NotificationManager::class.java)
        listOf(CHANGES to R.string.channel_changes, BEDTIME to R.string.channel_bedtime, HOMEWORK to R.string.channel_homework).forEach { (id, label) ->
            manager.createNotificationChannel(NotificationChannel(id, context.getString(label), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
                // Visibility is selected per notification; leave channel overrides to Android/user.
            })
        }
    }

    suspend fun scheduleChanged(json: JSONObject) {
        if (!notificationPreferences(json).changesEnabled || !json.optBoolean("completed")) return
        val group = json.selectedGroup() ?: return
        val context = localized(json)
        send("change:${group.id}:${json.optLong("changeCounter")}", CHANGES,
            context.getString(R.string.schedule_changed), context.getString(R.string.schedule_changed_body), json, LocalDate.now(RsueData.zone))
    }

    suspend fun evening(json: JSONObject, outcome: RefreshOutcome?, failed: Boolean, reportDay: LocalDate) {
        val group = json.selectedGroup() ?: return
        send("bedtime:$reportDay:${group.id}", BEDTIME, "", "", json, reportDay, deliverOn = reportDay,
            compose = { current ->
                // Compose while holding the repository transaction: a concurrent refresh must
                // never turn a stale snapshot into a false all-clear notification.
                val context = localized(current)
                val changed = current.optLong("changeCounter") > current.optLong("bedtimeReportedCounter")
                val title = context.getString(when {
                    failed -> R.string.schedule_check_failed
                    changed -> R.string.schedule_changed
                    outcome == RefreshOutcome.INITIAL -> R.string.schedule_first_check
                    else -> R.string.schedule_unchanged
                })
                val body = context.getString(when {
                    failed -> R.string.schedule_check_failed_body
                    changed -> R.string.schedule_changed_body
                    outcome == RefreshOutcome.INITIAL -> R.string.schedule_first_check_body
                    else -> R.string.schedule_unchanged_body
                })
                title to body
            },
            onPosted = { current -> if (!failed) current.put("bedtimeReportedCounter", current.optLong("changeCounter")) })
    }

    suspend fun homework(json: JSONObject, reportDay: LocalDate = LocalDate.now()) {
        if (reportDay != LocalDate.now()) return
        val group = json.selectedGroup() ?: return
        val preferences = notificationPreferences(json)
        if (!preferences.homeworkEnabled) return
        val context = localized(json)
        val metadata = json.optJSONObject("taskMetadata") ?: JSONObject()
        val completed = json.completedTasks()
        val tasks = json.tasks().mapNotNull { (id, text) ->
            if (text.isBlank() || id in completed) return@mapNotNull null
            val item = metadata.optJSONObject(id) ?: return@mapNotNull null
            if (item.optString("group") != group.id || item.optBoolean("needsReview")) return@mapNotNull null
            val subgroup = if (json.optBoolean("onlyMySubgroup")) json.optString("subgroup") else ""
            val taskSubgroup = item.optString("subgroup")
            if (subgroup.isNotBlank() && taskSubgroup.isNotBlank() && taskSubgroup != subgroup) return@mapNotNull null
            runCatching { Homework(id, item.getString("disciplineId"), item.getString("subject"),
                ZonedDateTime.parse(item.getString("due")), text, lessonId = id) }.getOrNull()
        }
        val now = Instant.now()
        val today = now.atZone(RsueData.zone).toLocalDate()
        val locale = uiLocale(context.resources.configuration)
        val reminders = homeworkReminders(now, RsueData.zone, tasks, preferences.reminderPolicy())
        val liveKeys = reminders.map { "${it.deliveryKey(today)}:${group.id}" }.toSet()
        // Completed/removed tasks stop contributing even if a summary was posted earlier today.
        val journal = json.optJSONObject("deliveries") ?: JSONObject()
        val cancelledKeys = journal.keys().asSequence().filter { it.startsWith("homework:$today:") && it.endsWith(":${group.id}") && it !in liveKeys }.toList()
        if (cancelledKeys.isNotEmpty()) repository.update { current ->
            if (current.selectedGroup() != group || current.tasks() != json.tasks() || current.completedTasks() != completed ||
                notificationPreferences(current).generation != preferences.generation ||
                current.optJSONObject("taskMetadata")?.toString() != metadata.toString()) return@update
            val entries = current.optJSONObject("deliveries") ?: JSONObject()
            cancelledKeys.forEach {
                NotificationManagerCompat.from(application).cancel(it, 0)
                entries.optJSONObject(it)?.put("fingerprint", "cancelled")
            }
            current.put("deliveries", entries)
        }
        reminders.forEach { reminder ->
            val defaultWeekday = reminder.dueDate.format(DateTimeFormatter.ofPattern("EEEE", locale))
            val weekdayName = when (locale.language) {
                "ru" -> mapOf(3 to "среду", 5 to "пятницу", 6 to "субботу")[reminder.dueDate.dayOfWeek.value]
                "uk" -> mapOf(3 to "середу", 5 to "п’ятницю", 6 to "суботу", 7 to "неділю")[reminder.dueDate.dayOfWeek.value]
                "be" -> mapOf(3 to "сераду", 5 to "пятніцу", 6 to "суботу", 7 to "нядзелю")[reminder.dueDate.dayOfWeek.value]
                else -> null
            } ?: defaultWeekday
            val weekday = weekdayName + ", " + reminder.dueDate.format(DateTimeFormatter.ofPattern("d MMMM", locale))
            val title = context.getString(R.string.homework_notification, weekday)
            send("${reminder.deliveryKey(today)}:${group.id}", HOMEWORK, title, reminder.subjects.joinToString("\n"), json, reminder.dueDate, deliverOn = reportDay)
        }
    }

    suspend fun test(json: JSONObject): Boolean {
        val context = localized(json)
        return send("test:${Instant.now().epochSecond}", CHANGES, "Everis", context.getString(R.string.test_notification_body), json, LocalDate.now(), respectPreferences = false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun send(key: String, channel: String, title: String, body: String, json: JSONObject, day: LocalDate,
        compose: ((JSONObject) -> Pair<String, String>)? = null, onPosted: ((JSONObject) -> Unit)? = null,
        deliverOn: LocalDate? = null, respectPreferences: Boolean = true): Boolean {
        if (!permissionGranted()) return false
        createChannels(json)
        if (application.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        var posted = false
        repository.update { current ->
            if (deliverOn != null && deliverOn != LocalDate.now()) return@update
            if (current.selectedGroup() != json.selectedGroup()) return@update
            if (notificationPreferences(current).generation != notificationPreferences(json).generation) return@update
            val preferences = notificationPreferences(current)
            if (respectPreferences && ((channel == CHANGES && !preferences.changesEnabled) ||
                    (channel == BEDTIME && !preferences.bedtimeEnabled) || (channel == HOMEWORK && !preferences.homeworkEnabled))) return@update
            if (channel == HOMEWORK && (current.tasks() != json.tasks() || current.completedTasks() != json.completedTasks() ||
                    current.optJSONObject("taskMetadata")?.toString() != json.optJSONObject("taskMetadata")?.toString())) return@update
            val (actualTitle, actualBody) = compose?.invoke(current) ?: (title to body)
            val fingerprint = MessageDigest.getInstance("SHA-256").digest((actualTitle + "\n" + actualBody).toByteArray()).joinToString("") { "%02x".format(it) }
            val journal = current.optJSONObject("deliveries") ?: JSONObject()
            if (journal.optJSONObject(key)?.optString("fingerprint") == fingerprint) { onPosted?.invoke(current); posted = true; return@update }
            val intent = Intent(application, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = "everis://day/$day".toUri()
            }
            val pending = PendingIntent.getActivity(application, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = NotificationCompat.Builder(application, channel)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(actualTitle).setContentText(actualBody.take(6000))
                .setStyle(NotificationCompat.BigTextStyle().bigText(actualBody.take(6000))).setContentIntent(pending)
                .setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            NotificationManagerCompat.from(application).notify(key, 0, builder.build())
            journal.put(key, JSONObject().put("fingerprint", fingerprint).put("recorded", LocalDate.now().toString()))
            // Bound the encrypted delivery journal; don't accumulate a permanent activity history.
            journal.keys().asSequence().toList().sortedBy { journal.optJSONObject(it)?.optString("recorded") }.dropLast(256).forEach(journal::remove)
            current.put("deliveries", journal)
            onPosted?.invoke(current)
            posted = true
        }
        return posted
    }

    companion object {
        const val CHANGES = "everis.schedule.changes"
        const val BEDTIME = "everis.schedule.bedtime"
        const val HOMEWORK = "everis.homework"
    }
}
