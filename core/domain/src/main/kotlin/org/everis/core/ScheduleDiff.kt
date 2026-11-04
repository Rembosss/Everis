package org.everis.core

data class ScheduleSnapshot(
    val sourceId: String,
    val groupId: String,
    val coverage: Coverage,
    val lessons: List<Lesson>,
) {
    init {
        require(sourceId.isNotBlank() && groupId.isNotBlank())
        require(lessons.map { it.id }.distinct().size == lessons.size) { "Duplicate lesson IDs" }
    }
}

sealed interface FetchResult {
    data class Success(val snapshot: ScheduleSnapshot) : FetchResult
    data class Failure(val reason: String) : FetchResult
}

data class LessonChange(val previous: Lesson, val current: Lesson) {
    val disciplineChanged: Boolean get() = previous.disciplineId != current.disciplineId ||
        previous.disciplineName != current.disciplineName
}

sealed interface SyncAssessment {
    data class Failed(val reason: String) : SyncAssessment
    data object Incomplete : SyncAssessment
    data object Initial : SyncAssessment
    data class Compared(
        val added: List<Lesson>,
        val changed: List<LessonChange>,
        val removed: List<Lesson>,
    ) : SyncAssessment {
        val hasChanges: Boolean get() = added.isNotEmpty() || changed.isNotEmpty() || removed.isNotEmpty()
    }
}

fun assessSchedule(previous: ScheduleSnapshot?, result: FetchResult): SyncAssessment {
    if (result is FetchResult.Failure) return SyncAssessment.Failed(result.reason)
    val incoming = (result as FetchResult.Success).snapshot
    if (!incoming.coverage.complete) return SyncAssessment.Incomplete
    if (previous == null) return SyncAssessment.Initial
    require(previous.sourceId == incoming.sourceId && previous.groupId == incoming.groupId)
    val old = previous.lessons.associateBy { it.id }
    val fresh = incoming.lessons.associateBy { it.id }
    // Ordering of teachers in a source response is not a schedule change.
    fun normalized(lesson: Lesson) = lesson.copy(teachers = lesson.teachers.distinct().sorted())
    val added = incoming.lessons.filter { it.id !in old }
    val changed = incoming.lessons.mapNotNull { current ->
        old[current.id]?.takeIf { normalized(it) != normalized(current) }?.let { LessonChange(it, current) }
    }
    // A shorter response only authoritatively removes lessons inside its coverage.
    val removed = previous.lessons.filter {
        it.id !in fresh && incoming.coverage.contains(it.start.toLocalDate())
    }
    return SyncAssessment.Compared(added, changed, removed)
}
