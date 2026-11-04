package org.everis.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.everis.app.data.*
import org.everis.core.nextDailyRun
import org.everis.core.SchedulingGenerationGate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val PERIODIC = "everis.periodic"
    private const val DAILY_TAG = "everis.daily"
    private val generations = SchedulingGenerationGate()

    @Synchronized
    fun configure(context: Context, json: JSONObject, replaceDaily: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        val preferences = notificationPreferences(json)
        if (!generations.accept(context.packageName, preferences.generation)) return
        if (!json.optBoolean("completed") || json.selectedGroup() == null) {
            manager.cancelUniqueWork(PERIODIC); manager.cancelAllWorkByTag(DAILY_TAG)
            return
        }
        if (replaceDaily) manager.cancelAllWorkByTag(DAILY_TAG)
        if (preferences.periodicEnabled) {
            val work = PeriodicWorkRequestBuilder<ScheduleRefreshWorker>(preferences.intervalMinutes, TimeUnit.MINUTES)
                .setInitialDelay(preferences.intervalMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf("generation" to preferences.generation))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, work)
        } else manager.cancelUniqueWork(PERIODIC)
        if (preferences.bedtimeEnabled) scheduleDaily(context, "bedtime", json, catchUp = true)
        if (preferences.homeworkEnabled) scheduleDaily(context, "homework", json, catchUp = true)
    }

    @Synchronized
    fun scheduleDaily(context: Context, kind: String, json: JSONObject, catchUp: Boolean = false) {
        val preferences = notificationPreferences(json)
        if (!generations.accept(context.packageName, preferences.generation)) return
        if (!json.optBoolean("completed")) return
        if ((kind == "bedtime" && !preferences.bedtimeEnabled) || (kind == "homework" && !preferences.homeworkEnabled)) return
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val time = if (kind == "bedtime") preferences.bedtime else preferences.homeworkTime
        val today = now.atZone(zone).toLocalDate()
        val reportExists = json.optJSONObject("deliveries")?.has("bedtime:$today:${json.selectedGroup()?.id}") == true
        val overdue = catchUp && now.atZone(zone).toLocalTime() >= time && (kind == "homework" || !reportExists)
        val next = if (overdue) now else nextDailyRun(now, time, zone)
        val date = next.atZone(zone).toLocalDate()
        val request = OneTimeWorkRequestBuilder<DailyNotificationWorker>()
            .setInputData(workDataOf("kind" to kind, "date" to date.toString(), "generation" to preferences.generation))
            .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(DAILY_TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork("everis.$kind.${preferences.generation}.$date", ExistingWorkPolicy.KEEP, request)
    }

    @Synchronized
    fun homeworkChanged(context: Context, json: JSONObject) {
        if (!generations.accept(context.packageName, notificationPreferences(json).generation)) return
        if (!notificationPreferences(json).homeworkEnabled || !json.optBoolean("completed")) return
        if (LocalTime.now() < notificationPreferences(json).homeworkTime) return
        val request = OneTimeWorkRequestBuilder<DailyNotificationWorker>().setInputData(workDataOf(
            "kind" to "homework", "date" to LocalDate.now().toString(),
            "generation" to notificationPreferences(json).generation, "immediate" to true)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("everis.homework.update", ExistingWorkPolicy.REPLACE, request)
    }
}

class ScheduleRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val repository = DiaryRepository.get(applicationContext)
        val saved = repository.read()
        val preferences = notificationPreferences(saved)
        val group = saved.selectedGroup()
        if (!saved.optBoolean("completed") || !preferences.periodicEnabled || group == null ||
            inputData.getLong("generation", -1) != preferences.generation) Result.success()
        else {
            val outcome = repository.refresh(group)
            val current = repository.read()
            if (outcome == RefreshOutcome.CHANGED && current.selectedGroup() == group &&
                notificationPreferences(current).generation == preferences.generation && notificationPreferences(current).periodicEnabled) {
                EverisNotifications(applicationContext).scheduleChanged(current)
            }
            Result.success()
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}

class DailyNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = DiaryRepository.get(applicationContext)
        return try {
            var saved = repository.read()
            val preferences = notificationPreferences(saved)
            val kind = inputData.getString("kind") ?: return Result.failure()
            if (kind !in setOf("bedtime", "homework")) return Result.failure()
            if (!saved.optBoolean("completed") || inputData.getLong("generation", -1) != preferences.generation) return Result.success()
            val expected = inputData.getString("date")?.let(LocalDate::parse) ?: return Result.failure()
            // Work deferred past midnight must never issue yesterday's all-clear report.
            if (expected == LocalDate.now()) {
                val publisher = EverisNotifications(applicationContext)
                if (kind == "bedtime" && preferences.bedtimeEnabled) {
                    val group = saved.selectedGroup() ?: return Result.success()
                    var failed = false
                    val outcome = try { repository.refresh(group) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true; null }
                    saved = repository.read()
                    if (saved.selectedGroup() == group && outcome != RefreshOutcome.STALE &&
                        notificationPreferences(saved).generation == preferences.generation && notificationPreferences(saved).bedtimeEnabled) publisher.evening(saved, outcome, failed, expected)
                } else if (kind == "homework" && preferences.homeworkEnabled) publisher.homework(saved, expected)
            }
            if (!inputData.getBoolean("immediate", false)) NotificationScheduler.scheduleDaily(applicationContext, kind, repository.read())
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}

/** Re-evaluate wall-clock times when the user's clock/zone changes. WorkManager handles reboot persistence too. */
class ClockChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { NotificationScheduler.configure(context, DiaryRepository.get(context).read(), replaceDaily = true) }
            catch (_: Exception) { /* Credential storage may be unavailable until unlock. Never reset it. */ }
            finally { pending.finish() }
        }
    }
}
