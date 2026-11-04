package org.everis.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun List<Lesson>.forSubgroup(subgroup: String?): List<Lesson> =
    if (subgroup.isNullOrBlank()) this else filter { it.subgroup == null || it.subgroup == subgroup }

fun homeworkTargets(lessons: List<Lesson>, disciplineId: String, after: Instant): List<Lesson> =
    lessons.filter { !it.cancelled && it.disciplineId == disciplineId && it.start.toInstant() >= after }
        .sortedWith(compareBy<Lesson> { it.start.toInstant() }.thenBy { it.id })

fun nextStudyDay(after: LocalDate, lessons: List<Lesson>, zone: ZoneId): LocalDate? =
    lessons.asSequence().filterNot { it.cancelled }.map { it.start.withZoneSameInstant(zone).toLocalDate() }
        .filter { it > after }.minOrNull()

/** Explicit selected-day guard prevents highlighting matching times on tomorrow's page. */
fun activeLessonIds(day: LocalDate, now: Instant, lessons: List<Lesson>, zone: ZoneId): Set<String> =
    if (day != now.atZone(zone).toLocalDate()) emptySet() else lessons.asSequence()
        .filter { !it.cancelled && now >= it.start.toInstant() && now < it.end.toInstant() }
        .map { it.id }.toSet()
