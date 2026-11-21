package org.everis.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.Collator
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.everis.app.data.UniversitySources

@Composable
fun universityName(id: String): String = stringResource(when (id) {
    "dstu" -> R.string.university_dstu
    "sfedu" -> R.string.university_sfedu
    "iubip" -> R.string.university_iubip
    "rgups" -> R.string.university_rgups
    else -> R.string.university_rsue
})

@Composable
fun UniversityPicker(selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.university), style = MaterialTheme.typography.labelLarge)
        val locale = uiLocale(LocalConfiguration.current)
        val collator = remember(locale) { Collator.getInstance(locale) }
        val choices = UniversitySources.universities.map { MenuChoice(it, universityName(it)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        EverisSelect(selected, choices, onSelected)
        if (selected == "rgups") Text(stringResource(R.string.rgups_vpn_notice),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun UniversitySelector(diary: DiaryViewModel, onChanged: () -> Unit) {
    UniversityPicker(diary.state.catalogUniversity, { id ->
        if (id != diary.state.catalogUniversity) { onChanged(); diary.selectUniversity(id) }
    })
}
