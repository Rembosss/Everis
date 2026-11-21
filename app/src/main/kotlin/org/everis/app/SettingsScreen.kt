package org.everis.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.everis.app.data.DiaryRepository
import org.everis.app.notifications.EverisNotifications
import java.time.LocalTime
import java.util.Locale

private data class AccentOption(val hex: String, val label: Int)
private val accents = listOf(
    AccentOption("#F44336", R.string.color_red), AccentOption("#FF9800", R.string.color_orange),
    AccentOption("#FFC107", R.string.color_amber), AccentOption("#FFEB3B", R.string.color_yellow),
    AccentOption("#CDDC39", R.string.color_lime), AccentOption("#4CAF50", R.string.color_green),
    AccentOption("#009688", R.string.color_teal), AccentOption("#00BCD4", R.string.color_cyan),
    AccentOption("#2196F3", R.string.color_blue), AccentOption("#3F51B5", R.string.color_indigo),
    AccentOption("#9C27B0", R.string.color_purple), AccentOption("#E91E63", R.string.color_pink),
    AccentOption("#795548", R.string.color_brown), AccentOption("#757575", R.string.color_gray),
    AccentOption("#212121", R.string.color_black), AccentOption("#FAFAFA", R.string.color_white),
)

@Composable
fun SettingsScreen(diary: DiaryViewModel) {
    var page by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<String, String>(
        save = { it }, restore = { restoreSettingsPage(it) })) { mutableStateOf("root") }
    var customColor by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    OnSectionReset { customColor = false; history = false; page = "root" }
    val state = diary.state
    val active = LocalScreenActive.current
    BackHandler(enabled = active && page != "root") { page = "root" }
    AnimatedContent(targetState = page, label = "settings page", contentKey = { it },
        modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background),
        transitionSpec = { screenTransition(if (targetState == "root") -1 else 1, state.performanceMode) }) { current ->
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (current == "about") AboutScreen(diary) { page = "root" }
        else Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
                EverisPageHeading(stringResource(when (current) {
                        "study" -> R.string.study_schedule_settings; "appearance" -> R.string.appearance
                        "notifications" -> R.string.notifications
                        "data" -> R.string.data_transfer; else -> R.string.settings
                    }), if (current != "root") ({ page = "root" }) else null)
            }
          LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = LocalNavigationClearance.current + 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (current) {
                "root" -> {
                    item("study-menu") { SettingsSection(stringResource(R.string.study_settings)) {
                        SettingsLink(stringResource(R.string.study_schedule_settings), state.group?.name.orEmpty(), R.drawable.ic_disciplines) { page = "study" }
                        SettingsLink(stringResource(R.string.notifications), stringResource(R.string.notification_settings_caption), R.drawable.ic_notification) { page = "notifications" }
                    } }
                    item("data-app") { SettingsSection(stringResource(R.string.app_settings)) {
                        SettingsLink(stringResource(R.string.appearance), stringResource(R.string.appearance_caption), R.drawable.ic_eyedropper) { page = "appearance" }
                        SettingsLink(stringResource(R.string.data_transfer), stringResource(R.string.data_transfer_caption), R.drawable.ic_diary) { page = "data" }
                        SettingsLink(stringResource(R.string.about), stringResource(R.string.about_caption), R.drawable.ic_info) { page = "about" }
                    } }
                    item("privacy") { Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                "study" -> item("study") { SettingsSection(null) {
                Text(state.group?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.university_source, universityName(state.group?.university ?: "rsue")), style = MaterialTheme.typography.bodySmall)
                EverisOutlinedButton(onClick = diary::changeGroup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_group)) }
                if (state.subgroups.isNotEmpty()) {
                    Text(stringResource(R.string.subgroup), style = MaterialTheme.typography.titleSmall)
                    EverisSelect(state.subgroup, state.subgroups.map { MenuChoice(it, it) }, diary::setSubgroup, placeholder = stringResource(R.string.select_subgroup))
                    SettingSwitch(stringResource(R.string.only_my_subgroup), state.onlyMySubgroup, state.subgroup.isNotBlank()) { diary.setOnlyMySubgroup(it) }
                }
                EverisTextButton(onClick = { history = true }) { Text(stringResource(R.string.change_history)) }
                if (state.tasksForReview.isNotEmpty()) {
                    Text(stringResource(R.string.tasks_need_review), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.tasks_need_review_info), style = MaterialTheme.typography.bodySmall)
                    state.tasksForReview.forEach { task ->
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(task.subject + "\n" + task.text, style = MaterialTheme.typography.bodySmall)
                        }
                        EverisTextButton(onClick = { diary.reviewHomework(task) }) { Text(stringResource(R.string.move_homework)) }
                    }
                }
                } }
                "appearance" -> item("appearance") { SettingsSection(null) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                LanguageButton(Modifier.fillMaxWidth(), diary::setLanguage)
                ThemePreviews(diary)
                SettingSwitch(stringResource(R.string.new_dock), state.newDock) { diary.setNewDock(it) }
                Text(stringResource(R.string.new_dock_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val dark = when (state.themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
                SettingSwitch(stringResource(R.string.black_theme), state.blackTheme && dark, dark) { diary.setBlackTheme(it) }
                Text(stringResource(R.string.palette), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentCircle(stringResource(R.string.dynamic_color), state.palette == "dynamic", { diary.setPalette("dynamic") },
                        brush = Brush.sweepGradient(listOf(Color(0xFFEF9A9A), Color(0xFFFFE082), Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFCE93D8), Color(0xFFEF9A9A))))
                    accents.forEach { option ->
                        AccentCircle(stringResource(option.label), state.palette.equals(option.hex, true), { diary.setPalette(option.hex) }, Color(option.hex.toColorInt()))
                    }
                    AccentCircle(stringResource(R.string.custom_color), state.palette != "dynamic" && accents.none { state.palette.equals(it.hex, true) },
                        { customColor = true }, MaterialTheme.colorScheme.primaryContainer, eyedropper = true)
                }
                SettingSwitch(stringResource(R.string.vibration), state.hapticsEnabled) { diary.setHaptics(it) }
                SettingSwitch(stringResource(R.string.performance_mode), state.performanceMode) { diary.setPerformanceMode(it) }
                Text(stringResource(R.string.performance_mode_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                "notifications" -> item("notifications") { SettingsSection(null) { NotificationSettings(diary) } }
                "data" -> item("data") { SettingsSection(null) { BackupSettings(diary) } }
            }
          }
        }
      }
    }
    if (customColor) ColorPickerDialog(state.palette, onDismiss = { customColor = false }, onColor = { diary.setPalette(it); customColor = false })
    if (history) HistoryDialog(state.history) { history = false }
}

@Composable
private fun SettingsLink(title: String, caption: String, icon: Int, onClick: () -> Unit) {
    val feedback = LocalEverisHaptics.current
    Surface(onClick = { feedback?.select(); onClick() }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null)
        }
    }
}

@Composable
internal fun SettingsSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ThemePreviews(diary: DiaryViewModel) {
    val systemDark = isSystemInDarkTheme()
    val mode = diary.state.themeMode
    val effectiveDark = when (mode) { "light" -> false; "dark" -> true; else -> systemDark }
    val accent = MaterialTheme.colorScheme.primary
    val feedback = LocalEverisHaptics.current
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false, true).forEach { dark ->
            val label = stringResource(if (dark) R.string.theme_dark else R.string.theme_light)
            val selected = dark == effectiveDark
            Column(Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                onClick = { feedback?.select(); diary.setTheme(if (dark == systemDark) "system" else if (dark) "dark" else "light") }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large, color = if (dark) Color(0xFF151318) else Color(0xFFF9F6FC),
                    border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(132.dp).semantics { this.selected = selected; contentDescription = label }) {
                    Canvas(Modifier.padding(16.dp)) {
                        val w = size.width; val h = size.height
                        val fg = if (dark) Color.White else Color.Black
                        drawRoundRect(accent, Offset(0f, 0f), Size(w * .58f, h * .10f), CornerRadius(5.dp.toPx()))
                        repeat(2) { row ->
                            val y = h * (.25f + row * .29f)
                            drawRoundRect(accent.copy(alpha = if (dark) .17f else .12f), Offset(0f, y), Size(w, h * .23f), CornerRadius(8.dp.toPx()))
                            drawRoundRect(fg.copy(alpha = .55f), Offset(w * .10f, y + h * .07f), Size(w * .5f, h * .035f), CornerRadius(2.dp.toPx()))
                        }
                        drawRoundRect(accent.copy(alpha = .18f), Offset(0f, h * .88f), Size(w, h * .12f), CornerRadius(8.dp.toPx()))
                        drawCircle(accent, h * .025f, Offset(w * .25f, h * .94f))
                        drawCircle(fg.copy(alpha = .25f), h * .025f, Offset(w * .75f, h * .94f))
                    }
                }
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(if (dark == systemDark) stringResource(R.string.theme_system) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccentCircle(label: String, selected: Boolean, onClick: () -> Unit, color: Color = Color.Transparent, brush: Brush? = null, eyedropper: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = LocalEverisHaptics.current
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = { feedback?.select(); onClick() }, modifier = Modifier.size(52.dp).pressFeedback(interaction).semantics { contentDescription = label; this.selected = selected },
            shape = CircleShape, interactionSource = interaction, color = Color.Transparent,
            border = BorderStroke(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)) {
            Box(Modifier.padding(if (selected) 6.dp else 4.dp).clip(CircleShape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                if (eyedropper) Icon(painterResource(R.drawable.ic_eyedropper), contentDescription = null)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, minLines = 2)
    }
}

@Composable
private fun NotificationSettings(diary: DiaryViewModel) {
    val context = LocalContext.current
    val preferences = diary.state.notifications
    val publisher = remember(context) { EverisNotifications(context) }
    var granted by remember { mutableStateOf(publisher.permissionGranted()) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = publisher.permissionGranted() }
    val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = publisher.permissionGranted() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var editingTime by remember { mutableStateOf<String?>(null) }
    if (diary.state.backgroundFailed) Text(stringResource(R.string.background_setup_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (!granted) {
        Text(stringResource(R.string.notifications_permission_info), style = MaterialTheme.typography.bodySmall)
        EverisButton(onClick = {
            diary.markPermissionAsked()
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text(stringResource(R.string.allow_notifications)) }
    }
    Text(stringResource(R.string.schedule_updates), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingSwitch(stringResource(R.string.periodic_check), preferences.periodicEnabled) { enabled -> diary.updateNotifications { it.copy(periodicEnabled = enabled) } }
    Text(stringResource(R.string.check_interval), style = MaterialTheme.typography.bodyMedium)
    EverisSelect(preferences.intervalMinutes, listOf(2, 4, 6, 8, 10, 12).map { MenuChoice(it * 60L, pluralStringResource(R.plurals.hours_value, it, it)) },
        { value -> diary.updateNotifications { it.copy(intervalMinutes = value) } }, enabled = preferences.periodicEnabled)
    SettingSwitch(stringResource(R.string.notify_changes), preferences.changesEnabled) { enabled -> diary.updateNotifications { it.copy(changesEnabled = enabled) } }
    SettingSwitch(stringResource(R.string.bedtime_check), preferences.bedtimeEnabled) { enabled -> diary.updateNotifications { it.copy(bedtimeEnabled = enabled) } }
    SettingValue(stringResource(R.string.bedtime_time), preferences.bedtime.toString(), preferences.bedtimeEnabled) { editingTime = "bedtime" }
    Text(stringResource(R.string.background_timing_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.homework_notifications), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingSwitch(stringResource(R.string.homework_notifications), preferences.homeworkEnabled) { enabled -> diary.updateNotifications { it.copy(homeworkEnabled = enabled) } }
    SettingValue(stringResource(R.string.homework_time), preferences.homeworkTime.toString(), preferences.homeworkEnabled) { editingTime = "homework" }
    Text(stringResource(R.string.days_ahead), style = MaterialTheme.typography.bodyMedium)
    EverisSelect(preferences.daysAhead, listOf(1, 2, 3, 5, 7, 12, 14).map { MenuChoice(it, pluralStringResource(R.plurals.days_value, it, it)) },
        { value -> diary.updateNotifications { it.copy(daysAhead = value) } }, enabled = preferences.homeworkEnabled)
    SettingSwitch(stringResource(R.string.daily_homework), preferences.dailyHomework, preferences.homeworkEnabled) { enabled -> diary.updateNotifications { it.copy(dailyHomework = enabled) } }
    Text(stringResource(R.string.homework_repeat_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingSwitch(stringResource(R.string.homework_due_day), preferences.onDueDay, preferences.homeworkEnabled) { enabled -> diary.updateNotifications { it.copy(onDueDay = enabled) } }
    EverisOutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.system_notification_settings)) }
    EverisTextButton(onClick = {
        granted = publisher.permissionGranted()
        scope.launch { testResult = runCatching { publisher.test(DiaryRepository.get(context).read()) }.getOrDefault(false) }
    }, enabled = granted) { Text(stringResource(R.string.test_notification)) }
    testResult?.let { Text(stringResource(if (it) R.string.test_notification_sent else R.string.notifications_blocked), style = MaterialTheme.typography.bodySmall) }
    if (editingTime != null) {
        val bedtime = editingTime == "bedtime"
        TimeSettingDialog(if (bedtime) preferences.bedtime else preferences.homeworkTime,
            stringResource(if (bedtime) R.string.bedtime_time else R.string.homework_time), onDismiss = { editingTime = null }) { time ->
            diary.updateNotifications { if (bedtime) it.copy(bedtime = time) else it.copy(homeworkTime = time) }
            editingTime = null
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val feedback = LocalEverisHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.large
    val colors = SwitchDefaults.colors()
    // Only the checked-state fraction animates here. Theme roles already share one global
    // transition; starting another color animation would make switches trail behind the UI.
    val fraction by animateFloatAsState(if (checked) 1f else 0f,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "switch selection")
    val track = lerp(colors.uncheckedTrackColor, colors.checkedTrackColor, fraction)
    val thumb = lerp(colors.uncheckedThumbColor, colors.checkedThumbColor, fraction)
    val border = lerp(colors.uncheckedBorderColor, colors.checkedBorderColor, fraction)
    val icon = lerp(colors.uncheckedIconColor, colors.checkedIconColor, fraction)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch,
            interactionSource = interaction, indication = ripple(),
            onValueChange = { feedback?.toggle(it); onChange(it) }).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, interactionSource = interaction,
            colors = colors.copy(checkedTrackColor = track, uncheckedTrackColor = track,
                checkedThumbColor = thumb, uncheckedThumbColor = thumb,
                checkedBorderColor = border, uncheckedBorderColor = border,
                checkedIconColor = icon, uncheckedIconColor = icon),
            thumbContent = { Icon(painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_close), null, Modifier.size(SwitchDefaults.IconSize)) })
    }
}

@Composable
private fun SettingValue(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.large)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
private fun TimeSettingDialog(initial: LocalTime, title: String, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) {
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 480.dp } || LocalConfiguration.current.fontScale > 1.4f
    val rows = if (compact) 3 else 5
    val wheelTextStyle = if (LocalConfiguration.current.fontScale > 1.4f) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium
    val wheelText = rememberTextMeasurer(cacheSize = 2).measure("00", style = wheelTextStyle)
    val rowHeight = with(LocalDensity.current) { wheelText.size.height.toDp() + 8.dp }.coerceAtLeast(48.dp)
    EverisAlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = rowHeight * rows)) {
            val fittingRows = (maxHeight / rowHeight).toInt().coerceIn(1, rows).let { if (it % 2 == 0) it - 1 else it }
            Surface(Modifier.fillMaxWidth().height(rowHeight).align(Alignment.Center), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) { }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TimeWheel(24, initial.hour, fittingRows, rowHeight, stringResource(R.string.hours_label), Modifier.weight(1f)) { hour = it }
                Text(":", style = MaterialTheme.typography.headlineMedium)
                TimeWheel(60, initial.minute, fittingRows, rowHeight, stringResource(R.string.minutes_label), Modifier.weight(1f)) { minute = it }
            }
        }
    }, confirmButton = { EverisTextButton(onClick = { onSave(LocalTime.of(hour, minute)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { EverisTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun TimeWheel(count: Int, initial: Int, rows: Int, rowHeight: androidx.compose.ui.unit.Dp, label: String, modifier: Modifier, onSelected: (Int) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = 500 * count + initial)
    val scope = rememberCoroutineScope()
    fun center(): Int = list.layoutInfo.let { info ->
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index ?: initial
    }
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = .7f) }
    val snapping = remember(list) { SnapLayoutInfoProvider(list) }
    val fling = remember(snapping, decay) { snapFlingBehavior(snapping, decay, spring(dampingRatio = .93f, stiffness = 350f)) }
    val selected by remember { derivedStateOf { center() % count } }
    val callback by rememberUpdatedState(onSelected)
    val feedback = LocalEverisHaptics.current
    LaunchedEffect(list) {
        var first = true
        snapshotFlow { center() % count }.distinctUntilChanged().collect {
            callback(it)
            if (!first && list.isScrollInProgress) feedback?.tick()
            first = false
        }
    }
    val largeFont = LocalConfiguration.current.fontScale > 1.4f
    LazyColumn(state = list, flingBehavior = fling, contentPadding = PaddingValues(vertical = rowHeight * ((rows - 1) / 2)),
        modifier = modifier.height(rowHeight * rows).semantics { contentDescription = label; stateDescription = selected.toString() }) {
        items(count * 1000) { index ->
            val value = index % count
            Box(Modifier.fillMaxWidth().height(rowHeight).clickable { scope.launch { list.animateScrollToItem(index) } }, contentAlignment = Alignment.Center) {
                Text(String.format(Locale.ROOT, "%02d", value), style = if (largeFont) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = if (selected == value) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected == value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
            }
        }
    }
}
