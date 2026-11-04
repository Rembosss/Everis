package org.everis.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.everis.app.data.ScheduleHistoryEntry
import org.everis.app.data.RsueData
import org.everis.core.Lesson
import java.time.format.DateTimeFormatter

@Composable
fun HistoryDialog(history: List<ScheduleHistoryEntry>, onDismiss: () -> Unit) {
    val locale = uiLocale(LocalConfiguration.current)
    EverisAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.change_history)) }, text = {
        if (history.isEmpty()) Text(stringResource(R.string.no_changes_history))
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            history.forEachIndexed { index, entry ->
                item("time-$index") { Text(entry.at.atZone(RsueData.zone).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", locale)), style = MaterialTheme.typography.titleSmall) }
                items(entry.changes) { change ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            change.before?.let { Text(stringResource(R.string.was_label), style = MaterialTheme.typography.labelSmall); ChangeLesson(it) }
                            change.after?.let { Text(stringResource(R.string.now_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); ChangeLesson(it) }
                            Text(stringResource(if (change.before == null) R.string.lesson_added else if (change.after == null) R.string.lesson_removed else R.string.lesson_changed), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }, confirmButton = { EverisTextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } })
}

@Composable
private fun ChangeLesson(lesson: Lesson) {
    val locale = uiLocale(LocalConfiguration.current)
    Text(lesson.disciplineName, style = MaterialTheme.typography.titleSmall)
    Text(lesson.start.format(DateTimeFormatter.ofPattern("d MMM HH:mm", locale)) + "–" + lesson.end.toLocalTime() + " · " + stringResource(lessonTypeLabel(lesson.type)), style = MaterialTheme.typography.bodySmall)
    Text((lesson.teachers + listOfNotNull(lesson.building, lesson.room, lesson.subgroup)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}
