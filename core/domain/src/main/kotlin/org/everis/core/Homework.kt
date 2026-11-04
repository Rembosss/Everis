package org.everis.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class ReminderMode { FIRST_AND_DAY_BEFORE, EVERY_DAY }

data class ReminderPolicy(
    val daysAhead: Int = 3,
    val mode: ReminderMode = ReminderMode.FIRST_AND_DAY_BEFORE,
    val onDueDay: Boolean = false,
) {
    init { require(daysAhead >= 1) }

    fun matches(today: LocalDate, due: LocalDate): Boolean {
        val remaining = ChronoUnit.DAYS.between(today, due)
        if (remaining == 0L) return onDueDay
        return when (mode) {
            ReminderMode.FIRST_AND_DAY_BEFORE -> remaining == daysAhead.toLong() || remaining == 1L
            ReminderMode.EVERY_DAY -> remaining in 1L..daysAhead.toLong()
        }
    }
}

data class Homework(
    val id: String,
    val disciplineId: String,
    val disciplineName: String,
    val due: ZonedDateTime,
    val text: String,
    val completed: Boolean = false,
    val lessonId: String? = null,
    val followLesson: Boolean = true,
) {
    init {
        require(id.isNotBlank() && disciplineId.isNotBlank() && disciplineName.isNotBlank())
        require(text.isNotBlank())
    }
}

data class HomeworkReminder(val dueDate: LocalDate, val subjects: List<String>, val homeworkIds: List<String>) {
    fun deliveryKey(today: LocalDate): String = "homework:$today:$dueDate"
}

fun homeworkReminders(
    now: Instant,
    zone: ZoneId,
    homework: List<Homework>,
    policy: ReminderPolicy,
): List<HomeworkReminder> {
    require(homework.map { it.id }.distinct().size == homework.size)
    val today = now.atZone(zone).toLocalDate()
    return homework.asSequence()
        .filter { !it.completed && it.due.toInstant() > now }
        .filter { policy.matches(today, it.due.withZoneSameInstant(zone).toLocalDate()) }
        .sortedBy { it.due.toInstant() }
        .groupBy { it.due.withZoneSameInstant(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, tasks) ->
            HomeworkReminder(date, tasks.distinctBy { it.disciplineId }.map { it.disciplineName }, tasks.map { it.id })
        }
}
