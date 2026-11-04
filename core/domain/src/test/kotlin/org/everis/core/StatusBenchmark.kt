package org.everis.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.system.measureNanoTime

/** Repeatable host-side comparison, not an Android frame-rate benchmark. */
fun main() {
    val zone = ZoneId.of("Europe/Moscow")
    val first = LocalDate.of(2026, 1, 1)
    val all = (0L until 200L).flatMap { offset -> (0 until 6).map { slot ->
        val start = first.plusDays(offset).atTime(LocalTime.of(8,30).plusMinutes(slot * 100L)).atZone(zone)
        Lesson("$offset-$slot", "math", "Math", start, start.plusMinutes(90))
    } }
    val day = first.plusDays(100)
    val cached = lessonsForDay(day, zone, all)
    val coverage = Coverage(day, day, true)
    val times = (0 until 1440 step 3).map { day.atStartOfDay(zone).plusMinutes(it.toLong()).toInstant() }
    times.forEach { check(currentStatus(it, zone, all, coverage) == currentStatus(it, zone, cached, coverage)) }
    var sink = 0
    repeat(3000) { sink += currentStatus(times[it % times.size], zone, if (it % 2 == 0) all else cached, coverage).hashCode() }
    fun duration(input: List<Lesson>) = measureNanoTime {
        repeat(5000) { sink += currentStatus(times[it % times.size], zone, input, coverage).hashCode() }
    } / 1_000_000.0
    val full = mutableListOf<Double>(); val small = mutableListOf<Double>()
    repeat(3) { full += duration(all); small += duration(cached) }
    val slow = full.sorted()[1]; val fast = small.sorted()[1]
    println("STATUS_BENCHMARK {\"allLessons\":${all.size},\"cachedLessons\":${cached.size},\"iterations\":5000,\"fullScanMs\":$slow,\"cachedDayMs\":$fast,\"ratio\":${slow / fast},\"equivalentInstants\":${times.size},\"sink\":$sink,\"platform\":\"host JVM; not Android FPS\"}")
}
