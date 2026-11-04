package org.everis.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class DatePeriod(val first: LocalDate, val last: LocalDate) {
    init { require(last >= first) }
    companion object {
        fun weekContaining(day: LocalDate): DatePeriod {
            val first = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return DatePeriod(first, first.plusDays(6))
        }
        fun month(month: YearMonth): DatePeriod = DatePeriod(month.atDay(1), month.atEndOfMonth())
    }
}

data class PeriodSummary(
    val period: DatePeriod,
    val teachingDays: Int,
    val lessonCount: Int,
    val countsByType: Map<LessonType, Int>,
    val teaching: Duration,
    val breaks: Duration,
    val complete: Boolean,
)

fun summarizePeriod(period: DatePeriod, zone: ZoneId, lessons: List<Lesson>, coverage: Coverage): PeriodSummary {
    val days = generateSequence(period.first) { it.plusDays(1) }.takeWhile { it <= period.last }
        .map { summarizeDay(it, zone, lessons, coverage) }.toList()
    // Count a cross-midnight occurrence once in the period, while duration is
    // clipped to calendar boundaries by summarizeDay.
    val from = period.first.atStartOfDay(zone).toInstant()
    val until = period.last.plusDays(1).atStartOfDay(zone).toInstant()
    val occurrences = lessons.filter { !it.cancelled && it.start.toInstant() < until && it.end.toInstant() > from }
    return PeriodSummary(period, days.count { it.lessons > 0 }, occurrences.size,
        occurrences.groupingBy { it.type }.eachCount(),
        days.fold(Duration.ZERO) { sum, value -> sum.plus(value.teaching) },
        days.fold(Duration.ZERO) { sum, value -> sum.plus(value.breaks) },
        days.all { it.complete })
}
