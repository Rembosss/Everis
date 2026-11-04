package org.everis.app

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource

/** An independent settings transaction. Cancelling never changes onboarding or the current group. */
@Composable
fun GroupChangeDialog(diary: DiaryViewModel) {
    val state = diary.state
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var subgroup by rememberSaveable { mutableStateOf("") }
    val selected = state.groups.firstOrNull { it.id == selectedId && it.name == query }
    LaunchedEffect(selected?.id) { selected?.let(diary::previewGroup) }
    EverisAlertDialog(onDismissRequest = { if (!state.saving) diary.cancelGroupChange() },
        title = { Text(stringResource(R.string.change_group)) },
        text = { GroupSearch(query, selected, state, { query = it; selectedId = null },
            { query = it.name; selectedId = it.id; subgroup = "" }, diary::loadGroups, subgroup, { subgroup = it }) },
        confirmButton = {
            EverisTextButton(onClick = { selected?.let { diary.chooseGroup(it, subgroup) } },
                enabled = selected != null && selected != state.group && !state.saving && !state.groupPreviewLoading &&
                    (state.previewSubgroups.isEmpty() || subgroup in state.previewSubgroups)) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { EverisTextButton(onClick = diary::cancelGroupChange, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } })
}
