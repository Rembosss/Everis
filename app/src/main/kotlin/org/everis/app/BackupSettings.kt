package org.everis.app

import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Lives above screens/lazy items so file-picker callbacks survive navigation and rotation. */
@Composable
fun BackupHost(diary: DiaryViewModel, content: @Composable () -> Unit) {
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleImport by rememberSaveable { mutableStateOf(false) }
    var legacyPassword by remember { mutableStateOf("") }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { diary.runBackup(it, restore = false) }
    }
    val exportSchedule = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { diary.runBackup(it, restore = false, scheduleOnly = true) }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { scheduleImport = false; pendingUri = uri.toString() }
    }
    val importSchedule = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { scheduleImport = true; pendingUri = uri.toString() }
    }
    val actions = BackupActions(
        export = { diary.clearBackupResult(); exportBackup.launch("Everis-backup-${LocalDate.now()}.json") },
        restore = { diary.clearBackupResult(); importBackup.launch(arrayOf("application/json", "application/octet-stream", "application/*")) },
        exportSchedule = { diary.clearBackupResult(); exportSchedule.launch("Everis-schedule-${LocalDate.now()}.json") },
        importSchedule = { diary.clearBackupResult(); importSchedule.launch(arrayOf("application/json", "application/octet-stream", "application/*")) })
    CompositionLocalProvider(LocalTransferActions provides actions) { content() }
    pendingUri?.let { uri ->
        EverisAlertDialog(onDismissRequest = { pendingUri = null },
            title = { Text(stringResource(if (scheduleImport) R.string.import_schedule else R.string.restore_backup)) },
            text = { Text(stringResource(if (scheduleImport) R.string.import_schedule_info else R.string.restore_warning)) },
            confirmButton = { EverisTextButton(onClick = {
                pendingUri = null; diary.runBackup(uri.toUri(), restore = true, scheduleOnly = scheduleImport)
            }, enabled = !diary.backupBusy && !diary.state.saving) { Text(stringResource(R.string.continue_action)) } },
            dismissButton = { EverisTextButton(onClick = { pendingUri = null }) { Text(stringResource(R.string.cancel)) } })
    }
    diary.legacyBackupUri?.takeIf { !diary.backupBusy }?.let { uri ->
        EverisAlertDialog(onDismissRequest = { legacyPassword = ""; diary.dismissLegacyBackup() },
            title = { Text(stringResource(R.string.restore_backup)) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.legacy_backup_info))
                    if (diary.backupResult == false) Text(stringResource(R.string.legacy_backup_error), color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(legacyPassword, { legacyPassword = it.take(1024) }, label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            }, confirmButton = { EverisTextButton(onClick = {
                val password = legacyPassword.toCharArray(); legacyPassword = ""
                diary.runBackup(uri, restore = true, legacyPassword = password)
            }, enabled = legacyPassword.isNotEmpty() && !diary.backupBusy) { Text(stringResource(R.string.restore_backup)) } },
            dismissButton = { EverisTextButton(onClick = { legacyPassword = ""; diary.dismissLegacyBackup() }) { Text(stringResource(R.string.cancel)) } })
    }
}

data class BackupActions(val export: () -> Unit, val restore: () -> Unit, val exportSchedule: () -> Unit, val importSchedule: () -> Unit)
val LocalTransferActions = staticCompositionLocalOf<BackupActions> { error("Transfer host is missing") }

@Composable
fun BackupSettings(diary: DiaryViewModel) {
    val actions = LocalTransferActions.current
    Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.backup_info), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EverisOutlinedButton(onClick = actions.export, enabled = !diary.backupBusy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_backup)) }
        EverisOutlinedButton(onClick = actions.restore, enabled = !diary.backupBusy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.restore_backup)) }
    }
    Text(stringResource(R.string.schedule_exchange), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.schedule_exchange_info), style = MaterialTheme.typography.bodySmall)
    EverisOutlinedButton(onClick = actions.exportSchedule, enabled = !diary.backupBusy && diary.state.schedule != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_schedule)) }
    EverisOutlinedButton(onClick = actions.importSchedule, enabled = !diary.backupBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_schedule)) }
    diary.backupResult?.let { success -> Text(stringResource(if (success) R.string.backup_success else R.string.backup_failed),
        style = MaterialTheme.typography.bodySmall, color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
}
