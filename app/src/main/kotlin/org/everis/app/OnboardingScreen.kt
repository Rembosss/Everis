@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.everis.app

import androidx.activity.compose.BackHandler

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.everis.app.data.StudentGroup
import org.everis.app.theme.WelcomeMotionScheme

data class AppLanguage(val code: String, val name: String, val flag: String)
val appLanguages = listOf(
    AppLanguage("ru", "Русский", "🇷🇺"), AppLanguage("uk", "Українська", "🇺🇦"),
    AppLanguage("be", "Беларуская", "🇧🇾"), AppLanguage("kk", "Қазақша", "🇰🇿"),
    AppLanguage("en", "English", "🇬🇧"),
)

@Composable
fun LanguageButton(modifier: Modifier = Modifier, onSelected: (String) -> Unit = {}) {
    val languageCode = uiLocale(LocalConfiguration.current).language
    val language = appLanguages.firstOrNull { it.code == languageCode } ?: appLanguages.first()
    EverisSelect(language.code, appLanguages.map { MenuChoice(it.code, "${it.flag}  ${it.name}") }, onSelected, modifier)
}

@Composable
fun SetupScreens(diary: DiaryViewModel) {
    val state = diary.state
    var page by rememberSaveable { mutableIntStateOf(if (state.group != null) 1 else 0) }
    var query by rememberSaveable { mutableStateOf(state.group?.name.orEmpty()) }
    var selectedId by rememberSaveable { mutableStateOf(state.group?.id) }
    var subgroup by rememberSaveable { mutableStateOf(state.subgroup) }
    val selected = state.groups.firstOrNull { it.id == selectedId && it.name == query }
    BackHandler(enabled = page > 0) { if (!state.saving) page-- }
    LaunchedEffect(selected?.id) { selected?.let(diary::previewGroup) }
    LaunchedEffect(diary.importedSetup) {
        diary.importedSetup?.let { imported ->
            query = imported.group.name; selectedId = imported.group.id; subgroup = imported.subgroup; page = 2
            diary.consumeSetupImport()
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(page, modifier = Modifier.fillMaxSize(), label = "onboarding step", transitionSpec = {
        val direction = if (targetState > initialState) 1 else -1
        screenTransition(direction, state.performanceMode)
    }) { step ->
        SetupPage(
            title = stringResource(when (step) { 0 -> R.string.welcome_title; 1 -> R.string.title_group; else -> R.string.title_ready }),
            action = stringResource(if (step == 2) R.string.forward else R.string.continue_action),
            enabled = !state.saving && step == page && (step != 1 || (selected != null && !state.groupPreviewLoading &&
                (state.previewSubgroups.isEmpty() || subgroup in state.previewSubgroups))),
            initialDelayMillis = if (step == 0) (500L - (android.os.SystemClock.elapsedRealtime() - diary.launchTimeMillis)).coerceAtLeast(0) else 80L,
            groupPage = step == 1,
            onContinue = {
                when (page) {
                    0 -> { page = 1 }
                    1 -> selected?.let { diary.chooseGroup(it, subgroup) { page = 2 } }
                    2 -> diary.finishSetup()
                }
            },
            onBack = if (step > 0) ({ if (!state.saving && page == step) page-- }) else null,
            controls = {
                when (step) {
                    0 -> LanguageButton(Modifier.fillMaxWidth(), diary::setLanguage)
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UniversitySelector(diary) { query = ""; selectedId = null; subgroup = "" }
                        GroupSearch(query, selected, state,
                            onQuery = { query = it; selectedId = null; subgroup = "" },
                            onSelect = { query = it.name; selectedId = it.id; subgroup = "" }, onRetry = diary::loadGroups,
                            subgroup = subgroup, onSubgroup = { subgroup = it })
                        val transfer = LocalTransferActions.current
                        EverisTextButton(onClick = transfer.importSchedule, enabled = !diary.backupBusy) { Text(stringResource(R.string.import_schedule)) }
                        diary.backupResult?.let { success -> Text(stringResource(if (success) R.string.backup_success else R.string.backup_failed), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
        )
    }
}

/** A single layout and motion contract for all three steps. */
@Composable
private fun SetupPage(
    title: String, action: String, enabled: Boolean, initialDelayMillis: Long, onContinue: () -> Unit,
    groupPage: Boolean, onBack: (() -> Unit)?, controls: @Composable () -> Unit,
) {
    // Subsequent steps already have a screen transition. Do not hide them behind another stagger.
    var titleVisible by remember { mutableStateOf(initialDelayMillis < 100) }
    var controlsVisible by remember { mutableStateOf(initialDelayMillis < 100) }
    val baseMotion = MaterialTheme.motionScheme
    val motion = remember(baseMotion) { WelcomeMotionScheme(baseMotion) }
    val typing = groupPage && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val fontScale = LocalConfiguration.current.fontScale
    val heroHeight by animateDpAsState(if (typing) 0.dp else (96 * fontScale.coerceAtMost(2f)).dp,
        baseMotion.defaultSpatialSpec(), label = "group search space")
    val heroAlpha = animateFloatAsState(if (typing) 0f else 1f, baseMotion.defaultEffectsSpec(), label = "group heading")
    LaunchedEffect(Unit) {
        if (ValueAnimator.areAnimatorsEnabled()) delay(initialDelayMillis)
        titleVisible = true
        if (ValueAnimator.areAnimatorsEnabled()) delay(700)
        controlsVisible = true
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp)) {
            val top = if (groupPage) 48.dp else (maxHeight * 0.22f).coerceAtMost(180.dp)
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(top))
                    AnimatedVisibility(titleVisible,
                        enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 8 },
                        exit = fadeOut(motion.fastEffectsSpec())) {
                        Box(Modifier.fillMaxWidth().then(if (groupPage) Modifier.height(heroHeight).clipToBounds().graphicsLayer { alpha = heroAlpha.value }
                            else Modifier.heightIn(min = 144.dp)), contentAlignment = Alignment.Center) {
                            Text(title, Modifier.widthIn(max = 440.dp).semantics { heading() },
                                style = if (groupPage && fontScale > 1.4f) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                    if (!titleVisible) Spacer(Modifier.height(if (groupPage) 104.dp else 144.dp))
                    Spacer(Modifier.height(if (groupPage) 8.dp else 24.dp))
                    AnimatedVisibility(controlsVisible,
                        enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 8 },
                        exit = fadeOut(motion.fastEffectsSpec())) {
                        Box(Modifier.widthIn(max = 440.dp).fillMaxWidth()) { controls() }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                AnimatedVisibility(controlsVisible,
                    enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 8 },
                    exit = fadeOut(motion.fastEffectsSpec()), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    EverisButton(onClick = onContinue, enabled = enabled,
                        modifier = Modifier.widthIn(min = if (fontScale > 1.3f) 216.dp else 156.dp, max = if (fontScale > 1.3f) 360.dp else 216.dp)
                            .fillMaxWidth(if (fontScale > 1.3f) 1f else 0.58f).padding(bottom = 24.dp).heightIn(min = 48.dp)) {
                        Text(action, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                    }
                }
            }
            if (onBack != null) EverisTextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Text("‹  " + stringResource(R.string.back))
            }
        }
    }
}

@Composable
internal fun GroupSearch(
    query: String, selected: StudentGroup?, state: DiaryState,
    onQuery: (String) -> Unit, onSelect: (StudentGroup) -> Unit, onRetry: () -> Unit,
    subgroup: String = "", onSubgroup: (String) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(query.isNotBlank() && selected == null) }
    val focus = LocalFocusManager.current
    val search by produceState("" to emptyList<StudentGroup>(), query, state.groups, state.catalogUniversity) {
        if (query.isBlank()) { value = query to emptyList(); return@produceState }
        delay(100)
        value = query to withContext(Dispatchers.Default) {
            state.groups.filter { it.university == state.catalogUniversity && it.name.contains(query.trim(), ignoreCase = true) }
                .sortedBy { !it.name.startsWith(query.trim(), ignoreCase = true) }.take(15)
        }
    }
    // Retain matching rows while the next search is being prepared. Never collapse the menu
    // between keystrokes: it would resize/recenter the containing dialog on every character.
    val suggestions = search.second.filter { it.university == state.catalogUniversity && it.name.contains(query.trim(), ignoreCase = true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = query, onValueChange = { expanded = it.isNotBlank(); onQuery(it.take(80)) }, singleLine = true,
                placeholder = { Text(stringResource(R.string.group_number)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        AnimatedVisibility(expanded && query.isNotBlank() && selected == null,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                LazyColumn(Modifier.fillMaxWidth().height(210.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    if (suggestions.isEmpty()) item("search-state") {
                        Box(Modifier.fillMaxWidth().height(190.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                            if (search.first != query || state.groupsLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Text(stringResource(if (state.groupsFailed) R.string.groups_error else R.string.no_groups), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(suggestions, key = { it.id }) { group ->
                        DropdownMenuItem(text = { Text(group.name) }, onClick = { expanded = false; onSelect(group); focus.clearFocus() })
                    }
                }
            }
        }
        Text(stringResource(R.string.university_source, universityName(state.catalogUniversity)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedVisibility(selected != null && state.previewGroupId == selected.id && state.previewSubgroups.isNotEmpty(),
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subgroup), style = MaterialTheme.typography.titleSmall)
                EverisSelect(subgroup, state.previewSubgroups.map { MenuChoice(it, it) }, onSubgroup, placeholder = stringResource(R.string.select_subgroup))
            }
        }
        if (selected != null && state.previewGroupId == selected.id && state.groupPreviewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            state.groupsLoading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.loading_groups), style = MaterialTheme.typography.bodySmall)
            }
            state.groupsFailed -> {
                Text(stringResource(R.string.groups_error), style = MaterialTheme.typography.bodySmall)
                EverisTextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            selected == null -> Text(stringResource(R.string.choose_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
