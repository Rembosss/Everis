package org.everis.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.everis.app.data.PublicProfile
import org.everis.app.data.PublicProfiles
import org.everis.app.updates.AppUpdate
import org.everis.app.updates.trustedDownload

internal fun openProjectLink(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
    catch (_: SecurityException) { Toast.makeText(context, R.string.no_link_handler, Toast.LENGTH_SHORT).show() }
}

@Composable
fun AboutScreen(diary: DiaryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val profiles = remember { PublicProfiles(context.applicationContext) }
    val rembos by produceState(PublicProfile("Rembos1k"), profiles) {
        withContext(Dispatchers.IO) { value = profiles.cached("Rembos1k"); value = profiles.refresh("Rembos1k") }
    }
    val donor by produceState(PublicProfile("Gods_Mask"), profiles) {
        withContext(Dispatchers.IO) { value = profiles.cached("Gods_Mask"); value = profiles.refresh("Gods_Mask") }
    }
    var person by rememberSaveable { mutableStateOf<String?>(null) }
    var checkingGitHub by remember { mutableStateOf(false) }
    var githubUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(checkingGitHub, person) {
        if (!checkingGitHub || person != "rembos") return@LaunchedEffect
        try {
            val missing = withContext(Dispatchers.IO) {
                val result = org.everis.sources.PublicHttpClient(setOf("api.github.com"), maxBytes = 64 * 1024,
                    connectTimeoutMillis = 5000, readTimeoutMillis = 5000)
                    .get(java.net.URI("https://api.github.com/users/Rembos1k"))
                result is org.everis.sources.HttpResult.Failure && result.statusCode == 404
            }
            if (missing) githubUnavailable = true else openProjectLink(context, "https://github.com/Rembos1k")
        } finally { checkingGitHub = false }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("heading") {
            EverisPageHeading(stringResource(R.string.about), onBack)
        }
        item("app") { SettingsSection("Everis") {
            Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodySmall)
            EverisButton(onClick = { diary.checkAppUpdate(manual = true) }, enabled = !diary.checkingUpdate, modifier = Modifier.fillMaxWidth()) {
                if (diary.checkingUpdate) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                Text(stringResource(R.string.check_app_updates))
            }
            ContactRow(stringResource(R.string.project_channel), "@EverisProject", R.drawable.ic_telegram) { openProjectLink(context, "https://t.me/EverisProject") }
            ContactRow(stringResource(R.string.project_repository), BuildConfig.UPDATE_REPOSITORY, R.drawable.ic_github) {
                openProjectLink(context, "https://github.com/${BuildConfig.UPDATE_REPOSITORY}")
            }
        } }
        item("developers") { SettingsSection(stringResource(R.string.developers)) {
            PersonRow("Rembos1k", stringResource(R.string.project_author), rembos, false) { person = "rembos" }
            PersonRow("Codex", "OpenAI", null, true) { person = "codex" }
        } }
        item("thanks") { SettingsSection(stringResource(R.string.thanks)) {
            Text(stringResource(R.string.special_thanks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PersonRow(donor.name, stringResource(R.string.codex_subscription_credit), donor, false) { person = "donor" }
        } }
    }
    if (person != null) {
        val profile = when (person) { "rembos" -> rembos; "donor" -> donor; else -> null }
        val codex = person == "codex"
        EverisAlertDialog(onDismissRequest = { person = null }, title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PersonAvatar(profile, codex, Modifier.size(80.dp))
                Text(if (codex) "Codex" else if (person == "rembos") "Rembos1k" else donor.name, style = MaterialTheme.typography.titleLarge)
            }
        }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (codex) ContactRow(stringResource(R.string.codex_homepage), "openai.com/codex", R.drawable.ic_codex) { openProjectLink(context, "https://openai.com/codex/") }
                else {
                    val handle = profile!!.handle
                    ContactRow("Telegram", "@$handle", R.drawable.ic_telegram) { openProjectLink(context, "https://t.me/$handle") }
                    if (person == "rembos") ContactRow("GitHub", "Rembos1k", R.drawable.ic_github, busy = checkingGitHub) { checkingGitHub = true }
                }
            }
        }, confirmButton = { EverisTextButton(onClick = { person = null }) { Text(stringResource(R.string.ok)) } })
    }
    if (githubUnavailable) EverisAlertDialog(onDismissRequest = { githubUnavailable = false },
        title = { Text(stringResource(R.string.github_profile_unavailable)) },
        text = { Text(stringResource(R.string.github_profile_unavailable_body)) },
        confirmButton = { EverisTextButton(onClick = { githubUnavailable = false; openProjectLink(context, "https://github.com/Rembos1k") }) { Text("GitHub") } },
        dismissButton = { EverisTextButton(onClick = { githubUnavailable = false; openProjectLink(context, "https://t.me/Rembos1k") }) { Text("Telegram") } })
}

@Composable
private fun ContactRow(title: String, subtitle: String, icon: Int, busy: Boolean = false, onClick: () -> Unit) {
    val feedback = LocalEverisHaptics.current
    Surface(onClick = { feedback?.select(); onClick() }, enabled = !busy, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.ic_chevron_right), null)
        }
    }
}

@Composable
private fun PersonRow(name: String, caption: String, profile: PublicProfile?, codex: Boolean, onClick: () -> Unit) {
    val feedback = LocalEverisHaptics.current
    Surface(onClick = { feedback?.select(); onClick() }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(profile, codex, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium); Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(painterResource(if (codex) R.drawable.ic_codex else R.drawable.ic_telegram), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PersonAvatar(profile: PublicProfile?, codex: Boolean, modifier: Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            if (codex) Icon(painterResource(R.drawable.ic_codex), null, Modifier.fillMaxSize().padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            else if (profile?.avatar != null) Image(profile.avatar.asImageBitmap(), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            else Text(profile?.handle?.take(1).orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun AppUpdateDialog(update: AppUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    EverisAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.update_available), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_version, update.version), style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(update.notes.ifBlank { stringResource(R.string.no_release_notes) }, style = MaterialTheme.typography.bodyMedium) }
            }
        }, confirmButton = { EverisTextButton(onClick = {
            if (trustedDownload(update.downloadUrl, BuildConfig.UPDATE_REPOSITORY)) { openProjectLink(context, update.downloadUrl); onDismiss() }
        }) { Text(stringResource(R.string.update_action)) } },
        dismissButton = { EverisTextButton(onClick = onDismiss) { Text(stringResource(R.string.remind_later)) } })
}
