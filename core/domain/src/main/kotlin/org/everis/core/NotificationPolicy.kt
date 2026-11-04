package org.everis.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class NotificationPreferences(
    val periodicEnabled: Boolean = true,
    val intervalMinutes: Long = 120,
    val changesEnabled: Boolean = true,
    val bedtimeEnabled: Boolean = true,
    val bedtime: LocalTime = LocalTime.of(22, 0),
    val homeworkEnabled: Boolean = true,
    val homeworkTime: LocalTime = LocalTime.of(19, 0),
    val daysAhead: Int = 3,
    val dailyHomework: Boolean = false,
    val onDueDay: Boolean = false,
    val generation: Long = 0,
) {
    init { require(intervalMinutes >= 15 && daysAhead in 1..30 && generation >= 0) }
    fun reminderPolicy() = ReminderPolicy(daysAhead,
        if (dailyHomework) ReminderMode.EVERY_DAY else ReminderMode.FIRST_AND_DAY_BEFORE, onDueDay)
}

/** Calendar time, not fixed 24 hours: survives DST transitions without cumulative drift. */
fun nextDailyRun(now: Instant, time: LocalTime, zone: ZoneId): Instant {
    val local = now.atZone(zone)
    var next = local.toLocalDate().atTime(time).atZone(zone)
    if (next.toInstant() <= now) next = local.toLocalDate().plusDays(1).atTime(time).atZone(zone)
    return next.toInstant()
}

fun initialDelay(now: Instant, next: Instant): Duration = Duration.between(now, next).coerceAtLeast(Duration.ZERO)
