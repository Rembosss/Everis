package org.everis.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class LessonType { LECTURE, PRACTICE, LABORATORY, OTHER, UNKNOWN }

data class Lesson(
    val id: String,
    val disciplineId: String,
    val disciplineName: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val type: LessonType = LessonType.UNKNOWN,
    val teachers: List<String> = emptyList(),
    val building: String? = null,
    val room: String? = null,
    val cancelled: Boolean = false,
    val onlineUrl: String? = null,
    val subgroup: String? = null,
    val studentGroups: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank())
        require(disciplineId.isNotBlank())
        require(disciplineName.isNotBlank())
        require(end.toInstant() > start.toInstant()) { "A lesson must end after it starts" }
    }
}

data class Coverage(val first: LocalDate, val last: LocalDate, val complete: Boolean) {
    init { require(last >= first) }
    fun contains(day: LocalDate): Boolean = day >= first && day <= last
    fun confirms(day: LocalDate): Boolean = complete && contains(day)
}

sealed interface DayStatus {
    data object Unavailable : DayStatus
    data object DayOff : DayStatus
    data object Finished : DayStatus
    data class BeforeFirst(val lesson: Lesson, val remaining: Duration) : DayStatus
    data class During(val lessons: List<Lesson>, val untilNextEnd: Duration) : DayStatus
    data class Break(val next: Lesson, val remaining: Duration) : DayStatus
}

private fun lessonsOn(day: LocalDate, zone: ZoneId, lessons: List<Lesson>): List<Lesson> {
    val from = day.atStartOfDay(zone).toInstant()
    val until = day.plusDays(1).atStartOfDay(zone).toInstant()
    return lessons.filter { !it.cancelled && it.start.toInstant() < until && it.end.toInstant() > from }
        .sortedWith(compareBy<Lesson> { it.start.toInstant() }.thenBy { it.id })
}

fun currentStatus(now: Instant, zone: ZoneId, lessons: List<Lesson>, coverage: Coverage): DayStatus {
    val day = now.atZone(zone).toLocalDate()
    if (!coverage.confirms(day)) return DayStatus.Unavailable
    val today = lessonsOn(day, zone, lessons)
    if (today.isEmpty()) return DayStatus.DayOff
    val active = today.filter { now >= it.start.toInstant() && now < it.end.toInstant() }
    if (active.isNotEmpty()) {
        return DayStatus.During(active, Duration.between(now, active.minOf { it.end.toInstant() }))
    }
    val next = today.firstOrNull { it.start.toInstant() > now } ?: return DayStatus.Finished
    val remaining = Duration.between(now, next.start.toInstant())
    return if (now < today.first().start.toInstant()) DayStatus.BeforeFirst(next, remaining)
    else DayStatus.Break(next, remaining)
}

data class DaySummary(
    val lessons: Int,
    val countsByType: Map<LessonType, Int>,
    val teaching: Duration,
    val breaks: Duration,
    val span: Duration,
    val hasOverlap: Boolean,
    val complete: Boolean,
)

fun summarizeDay(day: LocalDate, zone: ZoneId, lessons: List<Lesson>, coverage: Coverage): DaySummary {
    require(lessons.map { it.id }.distinct().size == lessons.size) { "Lesson IDs must be unique" }
    val today = lessonsOn(day, zone, lessons)
    if (today.isEmpty()) {
        return DaySummary(0, emptyMap(), Duration.ZERO, Duration.ZERO, Duration.ZERO, false, coverage.confirms(day))
    }
    val from = day.atStartOfDay(zone).toInstant()
    val until = day.plusDays(1).atStartOfDay(zone).toInstant()
    val intervals = today.map { maxOf(from, it.start.toInstant()) to minOf(until, it.end.toInstant()) }
    var segmentStart = intervals.first().first
    var segmentEnd = intervals.first().second
    var occupied = Duration.ZERO
    var overlap = false
    for ((start, end) in intervals.drop(1)) {
        if (start <= segmentEnd) {
            overlap = overlap || start < segmentEnd
            segmentEnd = maxOf(segmentEnd, end)
        } else {
            occupied = occupied.plus(Duration.between(segmentStart, segmentEnd))
            segmentStart = start
            segmentEnd = end
        }
    }
    occupied = occupied.plus(Duration.between(segmentStart, segmentEnd))
    val span = Duration.between(intervals.first().first, intervals.maxOf { it.second })
    return DaySummary(today.size, today.groupingBy { it.type }.eachCount(), occupied,
        span.minus(occupied), span, overlap, coverage.confirms(day))
}
