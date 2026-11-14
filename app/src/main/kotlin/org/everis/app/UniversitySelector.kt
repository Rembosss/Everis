package org.everis.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.everis.app.data.UniversitySources

@Composable
fun universityName(id: String): String = stringResource(if (id == "dstu") R.string.university_dstu else R.string.university_rsue)

@Composable
fun UniversityPicker(selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.university), style = MaterialTheme.typography.labelLarge)
        EverisSelect(selected, UniversitySources.universities.map { MenuChoice(it, universityName(it)) }, onSelected)
    }
}

@Composable
fun UniversitySelector(diary: DiaryViewModel, onChanged: () -> Unit) {
    UniversityPicker(diary.state.catalogUniversity, { id ->
        if (id != diary.state.catalogUniversity) { onChanged(); diary.selectUniversity(id) }
    })
}
