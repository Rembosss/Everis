@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.everis.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.everis.app.data.RsueData
import org.everis.app.data.disciplineKey
import org.everis.app.data.disciplineTitle
import org.everis.app.data.disciplineTargets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun HomeworkEditorDialog(diary: DiaryViewModel, request: HomeworkRequest) {
    val state = diary.state
    val locale = uiLocale(LocalConfiguration.current)
    val courses = remember(state.schedule, state.homework) {
        (state.schedule?.lessons.orEmpty().map { disciplineKey(it.disciplineName) to disciplineTitle(it.disciplineName) } + state.homework.map { disciplineKey(it.subject) to disciplineTitle(it.subject) })
            .distinctBy { it.first }.sortedBy { it.second }
    }
    var disciplineId by rememberSaveable(request) { mutableStateOf(disciplineKey(request.subject).ifEmpty { courses.firstOrNull()?.first.orEmpty() }) }
    val subject = courses.firstOrNull { it.first == disciplineId }?.second ?: request.subject
    val candidates = remember(state.schedule, request, disciplineId) { disciplineTargets(state.schedule?.lessons.orEmpty(), subject, request.after) }
    val initialId = request.initialLessonId ?: if (request.editTaskId?.startsWith("manual:") == true) "" else candidates.firstOrNull()?.id.orEmpty()
    var targetId by rememberSaveable(request) { mutableStateOf(initialId) }
    val originalId = remember(request) { request.editTaskId.orEmpty() }
    val sourceTextAtOpen = remember(request) { state.tasks[originalId].orEmpty() }
    var draft by rememberSaveable(request) { mutableStateOf(state.tasks[originalId].orEmpty()) }
    var dueText by rememberSaveable(request) { mutableStateOf(
        (state.homework.firstOrNull { it.id == request.editTaskId }?.due?.toLocalDate()
            ?: candidates.firstOrNull()?.start?.toLocalDate() ?: LocalDate.now(RsueData.zone).plusDays(1)).toString()) }
    var calendar by remember { mutableStateOf(false) }
    val target = candidates.firstOrNull { it.id == targetId }
    val expectedText = state.tasks[if (targetId.isNotBlank()) targetId else request.editTaskId?.takeIf { it.startsWith("manual:") }.orEmpty()].orEmpty()
    val merge = targetId.isNotBlank() && targetId != request.editTaskId && expectedText.isNotBlank()
    val finalText = if (merge) expectedText + "\n\n" + draft else draft
    val valid = (disciplineId.isNotBlank() && (targetId.isBlank() || target != null) && finalText.length <= 20_000 && draft.isNotBlank())
    EverisAlertDialog(onDismissRequest = diary::closeHomework, title = { Text(stringResource(R.string.task_hint)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (request.disciplineId.isBlank()) {
                EverisSelect(disciplineId, courses.map { MenuChoice(it.first, it.second) }, { id ->
                    disciplineId = id
                    targetId = disciplineTargets(state.schedule?.lessons.orEmpty(), courses.firstOrNull { it.first == id }?.second.orEmpty(), request.after).firstOrNull()?.id.orEmpty()
                })
            } else Text(subject, style = MaterialTheme.typography.titleMedium)
            if (candidates.isEmpty()) Text(stringResource(R.string.no_future_lessons), style = MaterialTheme.typography.bodySmall)
            val displayed = (candidates.take(12) + listOfNotNull(target)).distinctBy { it.id }
            val formatter = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", locale)
            EverisSelect(targetId, displayed.map { MenuChoice(it.id, it.start.format(formatter)) } + MenuChoice("", stringResource(R.string.custom_due_date)), { targetId = it })
            if (targetId.isEmpty()) {
                Text(stringResource(R.string.unbound_homework_info), style = MaterialTheme.typography.bodySmall)
                EverisOutlinedButton(onClick = { calendar = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(LocalDate.parse(dueText).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
                }
            } else if (target == null) Text(stringResource(R.string.editor_lesson_unavailable), color = MaterialTheme.colorScheme.error)
            else Text(stringResource(lessonTypeLabel(target.type)) + " · " + target.start.toLocalTime(), style = MaterialTheme.typography.bodySmall)
            if (merge) {
                Text(stringResource(R.string.append_existing_task), style = MaterialTheme.typography.bodySmall)
                Text(expectedText, style = MaterialTheme.typography.bodySmall, maxLines = 4, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = draft, onValueChange = { draft = it.take(20_000) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.task_hint)) }, minLines = 4, maxLines = 8,
                isError = finalText.length > 20_000)
            if (finalText.length > 20_000) Text(stringResource(R.string.task_too_long), color = MaterialTheme.colorScheme.error)
            if (state.saveFailed) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { EverisTextButton(onClick = {
        val finalRequest = request.copy(disciplineId = disciplineId, subject = subject)
        diary.saveHomework(finalRequest, target, target?.start?.toLocalDate() ?: LocalDate.parse(dueText), finalText, expectedText, sourceTextAtOpen, diary::homeworkSaved)
    }, enabled = valid && !state.saving) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (request.editTaskId != null) EverisTextButton(onClick = {
                    diary.deleteHomework(request.editTaskId, sourceTextAtOpen, diary::homeworkSaved)
                }, enabled = !state.saving) { Text(stringResource(R.string.delete_task), color = MaterialTheme.colorScheme.error) }
                EverisTextButton(onClick = diary::closeHomework, enabled = !state.saving) { Text(stringResource(R.string.cancel)) }
            }
        })
    if (calendar) {
        rememberModalLayer()
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(dueText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..2200)
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            EverisTextButton(onClick = {
                picker.selectedDateMillis?.let { dueText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                calendar = false
            }, enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.ok)) }
        }, dismissButton = { EverisTextButton(onClick = { calendar = false }) { Text(stringResource(R.string.cancel)) } }) {
            DatePicker(picker, showModeToggle = false)
        }
    }
}
