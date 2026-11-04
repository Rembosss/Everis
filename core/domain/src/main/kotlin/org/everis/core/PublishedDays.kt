package org.everis.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

fun lessonsForDay(day: LocalDate, zone: ZoneId, lessons: List<Lesson>): List<Lesson> {
    val from = day.atStartOfDay(zone).toInstant()
    val until = day.plusDays(1).atStartOfDay(zone).toInstant()
    return lessons.filter { !it.cancelled && it.start.toInstant() < until && it.end.toInstant() > from }
        .sortedWith(compareBy<Lesson> { it.start.toInstant() }.thenBy { it.id })
}

/** Product policy: an empty Sunday is a day off; unknown weekdays remain unknown. */
fun dayCoverage(day: LocalDate, publishedDays: Set<LocalDate>, lessons: List<Lesson>, zone: ZoneId): Coverage =
    Coverage(day, day, day in publishedDays ||
        (day.dayOfWeek == DayOfWeek.SUNDAY && lessonsForDay(day, zone, lessons).isEmpty()))

/** Compare semantic occurrences, ignoring order/source ID regeneration and retired dates. */
fun publishedScheduleChanged(previous: List<Lesson>, incoming: List<Lesson>, confirmedDays: Set<LocalDate>): Boolean {
    fun signature(lesson: Lesson): String = listOf(
        lesson.disciplineId, lesson.disciplineName, lesson.start.toInstant(), lesson.end.toInstant(),
        lesson.type, lesson.teachers.distinct().sorted(), lesson.building, lesson.room, lesson.cancelled, lesson.onlineUrl, lesson.subgroup,
    ).joinToString("\u001f")
    fun normalized(lessons: List<Lesson>) = lessons.filter { it.start.toLocalDate() in confirmedDays }
        .map(::signature).sorted()
    return normalized(previous) != normalized(incoming)
}
