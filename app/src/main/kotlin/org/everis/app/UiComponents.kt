package org.everis.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader

/** Shared policy: every modal leaves the content below it softly blurred. */
@Stable
class ModalHostState {
    private val layers = mutableStateListOf<Any>()
    val count: Int get() = layers.size
    fun enter(token: Any) { if (token !in layers) layers.add(token) }
    fun exit(token: Any) { layers.remove(token) }
    fun covered(token: Any): Boolean = layers.indexOf(token).let { it >= 0 && it < layers.lastIndex }
}
val LocalModalHost = staticCompositionLocalOf { ModalHostState() }
val LocalPerformanceMode = staticCompositionLocalOf { false }

@Composable
fun rememberModalLayer(visible: Boolean = true): Any {
    val host = LocalModalHost.current
    val token = remember { Any() }
    DisposableEffect(host, visible) {
        if (visible) host.enter(token)
        onDispose { if (visible) host.exit(token) }
    }
    return token
}

@Composable
fun EverisAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val token = rememberModalLayer()
    val host = LocalModalHost.current
    val density = LocalDensity.current.density
    val efficient = LocalPerformanceMode.current
    val blur = animateFloatAsState(if (!efficient && host.covered(token)) 7f else 0f, tween(300), label = "nested modal backdrop")
    AlertDialog(onDismissRequest, confirmButton, modifier = modifier.graphicsLayer {
        val radius = blur.value * density
        renderEffect = if (!efficient && radius > .1f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect() else null
    }, dismissButton = dismissButton,
        title = title, text = text, shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

data class MenuChoice<T>(val value: T, val label: String)

/** Use this selector for future setting/calendar/language menus as well. */
@Composable
fun <T> EverisSelect(
    selected: T, choices: List<MenuChoice<T>>, onSelected: (T) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, placeholder: String = "",
) {
    val feedback = LocalEverisHaptics.current
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "menu arrow")
    // A menu belongs to its anchor; opening it must not blur/re-measure the anchor's dialog.
    Box(modifier) {
        EverisOutlinedButton(onClick = { expanded = !expanded }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).onSizeChanged { anchorWidth = it.width }) {
            Text(choices.firstOrNull { it.value == selected }?.label ?: placeholder, Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.padding(start = 12.dp).rotate(arrow))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { anchorWidth.toDp() }).heightIn(max = 360.dp),
            shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp, shadowElevation = if (LocalPerformanceMode.current) 0.dp else 6.dp) {
            choices.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) },
                    leadingIcon = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (item.value == selected) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                    } },
                    onClick = { expanded = false; if (item.value != selected) { feedback?.select(); onSelected(item.value) } })
            }
        }
    }
}

@Composable
internal fun EverisPageHeading(title: String, onBack: (() -> Unit)? = null) {
    val configuration = LocalConfiguration.current
    val style = if (configuration.fontScale > 1.3f) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) EverisIconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.back)) }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = style.copy(
            lineBreak = androidx.compose.ui.text.style.LineBreak.Heading,
            hyphens = androidx.compose.ui.text.style.Hyphens.Auto), fontWeight = FontWeight.Bold)
    }
}

