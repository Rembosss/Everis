package org.everis.core

enum class HomeworkAttentionReason { LESSON_REMOVED, LESSON_CANCELLED, DISCIPLINE_CHANGED }
data class HomeworkAttention(val homeworkId: String, val reason: HomeworkAttentionReason)
data class HomeworkResolution(val homework: List<Homework>, val attention: List<HomeworkAttention>)

/** Apply only changes that have already been confidently matched to a local lesson ID. */
fun reconcileHomework(homework: List<Homework>, changes: SyncAssessment.Compared): HomeworkResolution {
    val removed = changes.removed.map { it.id }.toSet()
    val modified = changes.changed.associateBy { it.current.id }
    val attention = mutableListOf<HomeworkAttention>()
    val updated = homework.map { task ->
        val id = task.lessonId
        val change = modified[id]
        when {
            task.completed || id == null -> task
            id in removed -> {
                attention += HomeworkAttention(task.id, HomeworkAttentionReason.LESSON_REMOVED)
                task
            }
            change?.current?.cancelled == true -> {
                attention += HomeworkAttention(task.id, HomeworkAttentionReason.LESSON_CANCELLED)
                task
            }
            change != null && (change.disciplineChanged || task.disciplineId != change.previous.disciplineId) -> {
                attention += HomeworkAttention(task.id, HomeworkAttentionReason.DISCIPLINE_CHANGED)
                task
            }
            change != null && task.followLesson && change.previous.start != change.current.start ->
                task.copy(due = change.current.start)
            else -> task
        }
    }
    return HomeworkResolution(updated, attention)
}
