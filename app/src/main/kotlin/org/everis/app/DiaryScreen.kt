package org.everis.app

import android.Manifest
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.everis.app.notifications.EverisNotifications
import kotlinx.coroutines.delay

@Composable
fun DiaryApp(diary: DiaryViewModel) {
    val modalHost = remember { ModalHostState() }
    val languageChanging = diary.state.languageRequest != null
    val processing = languageChanging || diary.backupBusy
    val context = LocalContext.current
    LaunchedEffect(diary.manualUpdateResult) {
        diary.manualUpdateResult?.let { current ->
            android.widget.Toast.makeText(context, if (current) R.string.app_up_to_date else R.string.update_check_failed, android.widget.Toast.LENGTH_SHORT).show()
            diary.consumeManualUpdateResult()
        }
    }
    val efficient = diary.state.performanceMode
    val processingText = stringResource(if (languageChanging) R.string.changing_language else R.string.backup_processing)
    var frozenProcessingText by remember { mutableStateOf(diary.languageOverlayText ?: processingText) }
    LaunchedEffect(processing) {
        if (processing) {
            if (languageChanging) diary.freezeLanguageMessage(processingText)
            frozenProcessingText = diary.languageOverlayText ?: processingText
        }
    }
    val contentLayer = rememberGraphicsLayer()
    var localeSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val snapshotOpacity = remember { androidx.compose.animation.core.Animatable(1f) }
    var localeApplying by remember { mutableStateOf(false) }
    val blur = animateFloatAsState(if (!efficient && (modalHost.count > 0 || processing)) 9f else 0f,
        tween(320), label = "modal backdrop")
    val density = LocalDensity.current.density
    LaunchedEffect(diary.state.languageRequest) {
        val code = diary.state.languageRequest ?: return@LaunchedEffect
        val animate = ValueAnimator.areAnimatorsEnabled()
        diary.freezeLanguageMessage(frozenProcessingText)
        try {
            if (!efficient && animate) {
                snapshotOpacity.snapTo(1f)
                // Record only the frames used for this transition, not every normal app frame.
                withFrameNanos { }; withFrameNanos { }
                localeSnapshot = try { contentLayer.toImageBitmap() } catch (_: Exception) { null }
            }
            if (animate) delay(if (efficient) 150 else 280)
            localeApplying = true
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
            withFrameNanos { }; withFrameNanos { }
            if (animate) delay(160)
            localeApplying = false
            if (localeSnapshot != null && animate) snapshotOpacity.animateTo(0f, tween(240))
            diary.finishLanguageChange()
        } finally {
            localeSnapshot = null
            localeApplying = false
        }
    }
    CompositionLocalProvider(LocalModalHost provides modalHost) {
      BackupHost(diary) {
       Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val radius = blur.value * density
                renderEffect = if (!efficient && radius > 0.1f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect() else null
            }) {
                Box(Modifier.fillMaxSize().drawWithContent {
                    if (efficient || !languageChanging) drawContent()
                    else { contentLayer.record { this@drawWithContent.drawContent() }; drawLayer(contentLayer) }
                }) { DiaryAppContent(diary) }
                localeSnapshot?.let { frozen -> Image(frozen, null, Modifier.fillMaxSize().graphicsLayer { alpha = snapshotOpacity.value }, contentScale = ContentScale.FillBounds) }
            }
            AnimatedVisibility(processing, enter = fadeIn(tween(180)), exit = fadeOut(tween(240)), modifier = Modifier.fillMaxSize()) {
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (languageChanging && (efficient || localeSnapshot == null) && localeApplying) 1f else .12f))
                    .clickable(interactionSource = interaction, indication = null) { }, contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
                    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Text(frozenProcessingText)
                    }
                }
                }
            }
        }
       }
      }
    }
    // Register after child navigation, so resource/backup transitions cannot be popped mid-flight.
    BackHandler(processing) { }
}

@Composable
private fun DiaryAppContent(diary: DiaryViewModel) {
    val state = diary.state
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The beta welcome dialog is shown before the notification permission request.
    LaunchedEffect(state.completed, state.acknowledged, state.permissionAsked) {
        if (state.completed && state.acknowledged && !state.permissionAsked && Build.VERSION.SDK_INT >= 33) {
            diary.markPermissionAsked()
            if (!EverisNotifications(context).permissionGranted()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    when {
        !state.loaded -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { }
        state.storageFailed -> Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.storage_error))
                EverisButton(onClick = diary::load) { Text(stringResource(R.string.retry)) }
            }
        }
        else -> {
            AnimatedContent(state.completed, modifier = Modifier.fillMaxSize(), label = "setup to diary", transitionSpec = {
                screenTransition(efficient = state.performanceMode)
            }) { complete -> if (complete) MainScreens(diary) else SetupScreens(diary) }
            if (state.completed && !state.acknowledged) EverisAlertDialog(onDismissRequest = {},
                title = { Text(stringResource(R.string.prototype_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.prototype_body), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
                confirmButton = { EverisTextButton(onClick = diary::acknowledge) { Text(stringResource(R.string.ok)) } })
            else if (state.changingGroup) GroupChangeDialog(diary)
            if (state.completed && state.acknowledged && !state.changingGroup && state.homeworkRequest == null && diary.appUpdate != null)
                AppUpdateDialog(diary.appUpdate!!, diary::dismissAppUpdate)
            if (state.homeworkRequest != null) HomeworkEditorDialog(diary, state.homeworkRequest)
        }
    }
}

@Composable
private fun MainScreens(diary: DiaryViewModel) {
    var selection by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<TabSelection, Any>(
        save = { it.index }, restore = { TabSelection(restoreSelectedTab(it)) })) { mutableStateOf(TabSelection(0)) }
    val tab = selection.index
    val feedback = LocalEverisHaptics.current
    val holder = rememberSaveableStateHolder()
    val compactLabels = LocalConfiguration.current.fontScale > 1.4f && with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() < 600.dp }
    LaunchedEffect(diary.state.requestedDay) { if (diary.state.requestedDay != null) selection = selection.select(0) }
    Scaffold(modifier = Modifier.imePadding(), contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                val tabs = listOf(R.string.home to R.drawable.ic_home, R.string.disciplines to R.drawable.ic_disciplines,
                    R.string.timetable to R.drawable.ic_calendar_agenda, R.string.settings to R.drawable.ic_settings)
                tabs.forEachIndexed { index, (label, icon) ->
                    val interaction = remember(index) { MutableInteractionSource() }
                    NavigationBarItem(selected = tab == index, onClick = { if (tab != index) { feedback?.select(); selection = selection.select(index) } },
                        interactionSource = interaction,
                        icon = { Icon(painterResource(icon), stringResource(label), Modifier.pressFeedback(interaction)) },
                        label = if (compactLabels) null else ({ Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }))
                }
            }
        }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxSize()) {
            if (diary.state.saveFailed) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(stringResource(R.string.save_error))
                    EverisTextButton(onClick = diary::clearSaveError) { Text(stringResource(R.string.ok)) }
                }
            }
            AnimatedContent(targetState = selection,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().background(MaterialTheme.colorScheme.background),
                contentKey = { it.index }, label = "main tab",
                transitionSpec = { screenTransition(targetState.direction, diary.state.performanceMode) }) { current ->
                holder.SaveableStateProvider(current.index) {
                  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (current.index) {
                        0 -> DailySchedule(diary)
                        1 -> DisciplinesScreen(diary)
                        2 -> CalendarScreen(diary)
                        else -> SettingsScreen(diary)
                    }
                  }
                }
                }
        }
        }
    }
}
