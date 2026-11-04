package org.everis.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.ZonedDateTime

private val zone = ZoneId.of("Europe/Moscow")
private val day = LocalDate.of(2026, 11, 2)
private fun at(time: String, date: LocalDate = day) = ZonedDateTime.parse("${date}T${time}:00+03:00[Europe/Moscow]")
private fun lesson(id: String, start: String, end: String, date: LocalDate = day) =
    Lesson(id, "math", "Математика", at(start, date), at(end, date), LessonType.LECTURE)

fun main() {
    var passed = 0
    fun test(name: String, action: () -> Unit) {
        try { action(); passed++; println("PASS: $name") }
        catch (error: Throwable) { throw AssertionError(name, error) }
    }
    val coverage = Coverage(day, day.plusDays(30), true)
    val morning = lesson("a", "09:00", "10:30")
    val noon = lesson("b", "10:40", "12:10")
    val afternoon = lesson("c", "13:00", "14:30")
    val schedule = listOf(morning, noon, afternoon)

    test("before first lesson") {
        val state = currentStatus(at("08:00").toInstant(), zone, schedule, coverage) as DayStatus.BeforeFirst
        check(state.remaining == Duration.ofHours(1))
    }
    test("lesson begins at exact start") {
        check(currentStatus(at("09:00").toInstant(), zone, schedule, coverage) is DayStatus.During)
    }
    test("end boundary starts break") {
        val state = currentStatus(at("10:30").toInstant(), zone, schedule, coverage) as DayStatus.Break
        check(state.remaining == Duration.ofMinutes(10))
    }
    test("last end finishes the day") {
        check(currentStatus(at("14:30").toInstant(), zone, schedule, coverage) == DayStatus.Finished)
    }
    test("confirmed empty day is a day off") {
        check(currentStatus(at("08:00").toInstant(), zone, emptyList(), coverage) == DayStatus.DayOff)
    }
    test("unknown day is never a day off") {
        check(currentStatus(at("08:00").toInstant(), zone, emptyList(), coverage.copy(complete = false)) == DayStatus.Unavailable)
    }
    test("cancelled lessons do not affect status") {
        check(currentStatus(at("09:05").toInstant(), zone, listOf(morning.copy(cancelled = true)), coverage) == DayStatus.DayOff)
    }
    test("daily study and breaks") {
        val summary = summarizeDay(day, zone, schedule, coverage)
        check(summary.lessons == 3)
        check(summary.teaching == Duration.ofMinutes(270))
        check(summary.breaks == Duration.ofMinutes(60))
        check(summary.span == Duration.ofMinutes(330))
        check(summary.countsByType[LessonType.LECTURE] == 3)
        check(!summary.hasOverlap)
    }
    test("overlap is explicit and never double counted") {
        val summary = summarizeDay(day, zone, listOf(morning, lesson("overlap", "10:00", "11:00")), coverage)
        check(summary.hasOverlap && summary.teaching == Duration.ofHours(2))
        check(summary.breaks == Duration.ZERO)
    }
    test("partial coverage is reflected in totals") {
        check(!summarizeDay(day, zone, schedule, coverage.copy(complete = false)).complete)
    }
    test("N=5 basic reminders are exactly five and one days") {
        val policy = ReminderPolicy(5)
        val selected = (0L..6L).filter { policy.matches(day.minusDays(it), day) }
        check(selected == listOf(1L, 5L))
    }
    test("N=5 daily reminders cover five through one") {
        val policy = ReminderPolicy(5, ReminderMode.EVERY_DAY)
        check((0L..6L).filter { policy.matches(day.minusDays(it), day) } == listOf(1L, 2L, 3L, 4L, 5L))
    }
    test("N=1 does not duplicate the endpoint") {
        for (mode in ReminderMode.entries) {
            check((0L..3L).count { ReminderPolicy(1, mode).matches(day.minusDays(it), day) } == 1)
        }
    }
    test("due day is separately configurable") {
        check(!ReminderPolicy(5).matches(day, day))
        check(ReminderPolicy(5, onDueDay = true).matches(day, day))
        check(!ReminderPolicy(5, onDueDay = true).matches(day.plusDays(1), day))
    }
    test("homework groups subjects and excludes completed tasks") {
        val tasks = listOf(
            Homework("1", "math", "Математика", at("09:00"), "Задача 1"),
            Homework("2", "math", "Математика", at("09:00"), "Задача 2"),
            Homework("3", "eng", "Английский", at("10:00"), "Текст", completed = true),
        )
        val result = homeworkReminders(at("18:00", day.minusDays(1)).toInstant(), zone, tasks, ReminderPolicy(5))
        check(result.single().subjects == listOf("Математика"))
        check(result.single().homeworkIds == listOf("1", "2"))
    }
    test("expired homework is not reminded on due day") {
        val task = Homework("1", "math", "Математика", at("09:00"), "Задача")
        check(homeworkReminders(at("10:00").toInstant(), zone, listOf(task), ReminderPolicy(onDueDay = true)).isEmpty())
    }
    val previous = ScheduleSnapshot("rsue", "group", coverage, schedule)
    test("network errors are not unchanged") {
        check(assessSchedule(previous, FetchResult.Failure("offline")) is SyncAssessment.Failed)
    }
    test("first snapshot is a baseline") {
        check(assessSchedule(null, FetchResult.Success(previous)) == SyncAssessment.Initial)
    }
    test("partial snapshot is not unchanged") {
        check(assessSchedule(previous, FetchResult.Success(previous.copy(coverage = coverage.copy(complete = false)))) == SyncAssessment.Incomplete)
    }
    test("same snapshot has no changes") {
        check(!(assessSchedule(previous, FetchResult.Success(previous)) as SyncAssessment.Compared).hasChanges)
    }
    test("room change is detected") {
        val next = previous.copy(lessons = listOf(morning.copy(room = "412"), noon, afternoon))
        val result = assessSchedule(previous, FetchResult.Success(next)) as SyncAssessment.Compared
        check(result.changed.single().current.room == "412")
    }
    test("teacher ordering does not create changes") {
        val a = previous.copy(lessons = listOf(morning.copy(teachers = listOf("Иванов", "Петров"))))
        val b = a.copy(lessons = listOf(a.lessons.single().copy(teachers = listOf("Петров", "Иванов"))))
        check(!(assessSchedule(a, FetchResult.Success(b)) as SyncAssessment.Compared).hasChanges)
    }
    test("discipline replacement is flagged for homework review") {
        val next = previous.copy(lessons = listOf(morning.copy(disciplineId = "eng", disciplineName = "Английский"), noon, afternoon))
        check((assessSchedule(previous, FetchResult.Success(next)) as SyncAssessment.Compared).changed.single().disciplineChanged)
    }
    test("shorter coverage cannot cancel later lessons") {
        val later = lesson("later", "09:00", "10:30", day.plusDays(10))
        val old = previous.copy(lessons = schedule + later)
        val fresh = previous.copy(coverage = Coverage(day, day.plusDays(6), true))
        check((assessSchedule(old, FetchResult.Success(fresh)) as SyncAssessment.Compared).removed.isEmpty())
    }
    test("week boundaries are Monday through Sunday") {
        check(DatePeriod.weekContaining(day.plusDays(3)) == DatePeriod(day, day.plusDays(6)))
    }
    test("month boundaries include the leap day") {
        check(DatePeriod.month(YearMonth.of(2028, 2)).last == LocalDate.of(2028, 2, 29))
    }
    test("weekly summary counts actual types and teaching days") {
        val next = lesson("next", "09:00", "10:30", day.plusDays(1)).copy(type = LessonType.LABORATORY)
        val result = summarizePeriod(DatePeriod.weekContaining(day), zone, schedule + next, coverage)
        check(result.lessonCount == 4 && result.teachingDays == 2)
        check(result.countsByType[LessonType.LECTURE] == 3 && result.countsByType[LessonType.LABORATORY] == 1)
        check(result.teaching == Duration.ofMinutes(360) && result.breaks == Duration.ofMinutes(60))
    }
    test("monthly summary reports incomplete coverage") {
        check(!summarizePeriod(DatePeriod.month(YearMonth.from(day)), zone, schedule,
            Coverage(day, day.plusDays(6), true)).complete)
    }
    test("cross-midnight lesson is clipped per day and counted once per period") {
        val overnight = morning.copy(start = at("23:00"), end = at("01:00", day.plusDays(1)))
        check(summarizeDay(day, zone, listOf(overnight), coverage).teaching == Duration.ofHours(1))
        val result = summarizePeriod(DatePeriod(day, day.plusDays(1)), zone, listOf(overnight), coverage)
        check(result.lessonCount == 1 && result.teaching == Duration.ofHours(2))
    }
    val city = CityRef("rostov", "Ростов-на-Дону")
    val university = UniversityRef("rsue", city.id, "rsue", "РИНХ")
    val faculty = FacultyRef("economics", university.id, "Экономический")
    val group = GroupRef("group", university.id, faculty.id, "Группа")
    val draft = OnboardingDraft().chooseCity(city).chooseUniversity(university).chooseFaculty(faculty).chooseGroup(group)
    test("changing city resets all dependent selections") {
        val result = draft.chooseCity(CityRef("moscow", "Москва"))
        check(result.university == null && result.faculty == null && result.group == null)
    }
    test("foreign parent cannot be selected") {
        check(runCatching { draft.chooseFaculty(faculty.copy(universityId = "other")) }.isFailure)
        check(runCatching { draft.chooseGroup(group.copy(facultyId = "other")) }.isFailure)
    }
    test("late catalogue response is rejected after selection changes") {
        val first = OnboardingDraft().chooseCity(city)
        val token = first.requestToken()
        val returned = first.chooseCity(CityRef("moscow", "Москва")).chooseCity(city)
        check(!returned.accepts(token))
    }
    test("source can explicitly skip unsupported faculty level") {
        val result = OnboardingDraft().chooseCity(city).chooseUniversity(university)
            .withoutFaculty().chooseGroup(group.copy(facultyId = null))
        check(result.step == SetupStep.CONFIRM && result.faculty == null)
    }
    val task = Homework("hw", "math", "Математика", morning.start, "Сохранить задание", lessonId = morning.id)
    test("removed lesson never deletes homework") {
        val result = reconcileHomework(listOf(task), SyncAssessment.Compared(emptyList(), emptyList(), listOf(morning)))
        check(result.homework == listOf(task))
        check(result.attention.single().reason == HomeworkAttentionReason.LESSON_REMOVED)
    }
    test("confident lesson move updates a following deadline") {
        val moved = morning.copy(start = at("09:00", day.plusDays(2)), end = at("10:30", day.plusDays(2)))
        val result = reconcileHomework(listOf(task), SyncAssessment.Compared(emptyList(), listOf(LessonChange(morning, moved)), emptyList()))
        check(result.homework.single().due == moved.start)
        check(result.homework.single().text == task.text && result.attention.isEmpty())
    }
    test("fixed homework deadline survives a lesson move") {
        val moved = morning.copy(start = at("09:00", day.plusDays(2)), end = at("10:30", day.plusDays(2)))
        val fixed = task.copy(followLesson = false)
        val result = reconcileHomework(listOf(fixed), SyncAssessment.Compared(emptyList(), listOf(LessonChange(morning, moved)), emptyList()))
        check(result.homework == listOf(fixed))
    }
    test("discipline replacement preserves homework for review") {
        val changed = morning.copy(disciplineId = "economics", disciplineName = "Экономика")
        val result = reconcileHomework(listOf(task), SyncAssessment.Compared(emptyList(), listOf(LessonChange(morning, changed)), emptyList()))
        check(result.homework == listOf(task))
        check(result.attention.single().reason == HomeworkAttentionReason.DISCIPLINE_CHANGED)
    }
    test("same discipline ID with a changed name requires review") {
        check(LessonChange(morning, morning.copy(disciplineName = "Другой предмет")).disciplineChanged)
    }
    test("empty Sunday is a day off without a published Sunday row") {
        val sunday = day.plusDays(6)
        check(dayCoverage(sunday, emptySet(), emptyList(), zone).confirms(sunday))
        check(currentStatus(at("12:00", sunday).toInstant(), zone, emptyList(), dayCoverage(sunday, emptySet(), emptyList(), zone)) == DayStatus.DayOff)
    }
    test("unpublished weekday remains unknown") {
        check(!dayCoverage(day, emptySet(), emptyList(), zone).complete)
    }
    test("Sunday with a lesson is never converted into an empty holiday") {
        val sunday = day.plusDays(6)
        val lesson = lesson("sun", "09:00", "10:30", sunday)
        val coverage = dayCoverage(sunday, setOf(sunday), listOf(lesson), zone)
        check(currentStatus(at("09:30", sunday).toInstant(), zone, listOf(lesson), coverage) is DayStatus.During)
    }
    test("day selection cannot change a status computed from the real instant") {
        val now = at("08:10").toInstant()
        val future = lesson("tomorrow", "08:00", "09:30", day.plusDays(1))
        val status = currentStatus(now, zone, schedule + future, coverage) as DayStatus.BeforeFirst
        check(status.remaining == Duration.ofMinutes(50) && status.lesson == morning)
    }
    test("source IDs and teacher ordering are not semantic timetable changes") {
        val old = morning.copy(teachers = listOf("B", "A"))
        val incoming = old.copy(id = "regenerated", teachers = listOf("A", "B"))
        check(!publishedScheduleChanged(listOf(old), listOf(incoming), setOf(day)))
    }
    test("room change is a semantic timetable change") {
        check(publishedScheduleChanged(listOf(morning), listOf(morning.copy(room = "999")), setOf(day)))
    }
    test("retired dates outside a response are not timetable cancellations") {
        val later = lesson("later", "09:00", "10:30", day.plusDays(20))
        check(!publishedScheduleChanged(listOf(morning, later), listOf(morning), setOf(day)))
    }
    test("confirmed empty day does detect a removed lesson") {
        check(publishedScheduleChanged(listOf(morning), emptyList(), setOf(day)))
    }
    test("daily scheduler rolls forward at the exact selected time") {
        val now = at("22:00").toInstant()
        val next = nextDailyRun(now, java.time.LocalTime.of(22, 0), zone)
        check(next == at("22:00", day.plusDays(1)).toInstant())
    }
    test("daily scheduler observes DST instead of adding 24 hours") {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, berlin).toInstant()
        val next = nextDailyRun(now, java.time.LocalTime.of(22, 0), berlin)
        check(Duration.between(now, next) == Duration.ofHours(23))
    }
    test("notification settings enforce the Android periodic minimum") {
        check(runCatching { NotificationPreferences(intervalMinutes = 14) }.isFailure)
        check(runCatching { NotificationPreferences(daysAhead = 0) }.isFailure)
    }
    test("notification settings preserve N and one versus daily modes") {
        val due = day.plusDays(5)
        val regular = NotificationPreferences(daysAhead = 5).reminderPolicy()
        check(regular.matches(day, due) && regular.matches(due.minusDays(1), due) && !regular.matches(day.plusDays(1), due))
        check(NotificationPreferences(daysAhead = 5, dailyHomework = true).reminderPolicy().matches(day.plusDays(1), due))
    }
    test("tomorrow page never highlights a lesson at the same clock time") {
        val future = morning.copy(id = "tomorrow", start = morning.start.plusDays(1), end = morning.end.plusDays(1))
        check(activeLessonIds(day.plusDays(1), morning.start.toInstant(), listOf(future), zone).isEmpty())
        check(activeLessonIds(day, morning.start.toInstant(), listOf(morning), zone) == setOf(morning.id))
    }
    test("current lesson highlight includes start and excludes end") {
        check(activeLessonIds(day, morning.start.toInstant(), listOf(morning), zone) == setOf(morning.id))
        check(activeLessonIds(day, morning.end.toInstant(), listOf(morning), zone).isEmpty())
    }
    test("homework next target excludes overlapping and cancelled lessons") {
        val overlap = morning.copy(id = "overlap", start = morning.start.plusMinutes(30), end = morning.end.plusMinutes(30))
        val cancelled = noon.copy(id = "cancelled", cancelled = true)
        val next = morning.copy(id = "nextmath", start = morning.start.plusDays(1), end = morning.end.plusDays(1))
        val targets = homeworkTargets(listOf(morning, overlap, cancelled, noon, afternoon, next), morning.disciplineId, morning.end.toInstant())
        check(targets.none { it.id in setOf(morning.id, overlap.id, cancelled.id) })
        check(targets.all { it.disciplineId == morning.disciplineId && it.start.toInstant() >= morning.end.toInstant() })
        check(targets.any { it.id == next.id })
    }
    test("subgroup retains common lessons and removes other subgroups") {
        val one = noon.copy(id = "one", subgroup = "1")
        val two = noon.copy(id = "two", subgroup = "2")
        check(listOf(morning, one, two).forSubgroup("1").map { it.id } == listOf(morning.id, one.id))
        check(listOf(one, two).forSubgroup("").size == 2)
    }
    test("subgroup changes are semantic schedule changes") {
        check(publishedScheduleChanged(listOf(morning.copy(subgroup = "1")), listOf(morning.copy(subgroup = "2")), setOf(day)))
    }
    test("week arrows target Monday across month and year boundaries") {
        val sunday = LocalDate.of(2027, 1, 3)
        check(weekStart(sunday) == LocalDate.of(2026, 12, 28))
        check(weekStart(sunday).plusWeeks(1) == LocalDate.of(2027, 1, 4))
        check(weekStart(sunday).minusWeeks(1).dayOfWeek == java.time.DayOfWeek.MONDAY)
    }
    test("next study day skips weekends and cancelled dates") {
        val next = morning.copy(id = "later", start = morning.start.plusDays(4), end = morning.end.plusDays(4))
        val cancelled = morning.copy(id = "cancelled-later", start = morning.start.plusDays(1), end = morning.end.plusDays(1), cancelled = true)
        check(nextStudyDay(day, listOf(morning, cancelled, next), zone) == day.plusDays(4))
        check(nextStudyDay(day.plusDays(4), listOf(next), zone) == null)
    }
    test("late old notification settings cannot cancel newly configured work") {
        val gate = SchedulingGenerationGate()
        check(gate.accept("app", 5))
        check(gate.accept("app", 6))
        check(!gate.accept("app", 5))
        check(gate.accept("app", 6))
        check(gate.accept("other-app", 1))
    }
    test("concurrent scheduling accepts only the latest completed generation afterwards") {
        val gate = SchedulingGenerationGate()
        val threads = listOf(10L, 40L, 20L, 30L).map { generation -> Thread { gate.accept("app", generation) }.apply { start() } }
        threads.forEach { it.join() }
        check(!gate.accept("app", 39)); check(gate.accept("app", 40))
    }
    println("Domain checks passed: $passed")
}
