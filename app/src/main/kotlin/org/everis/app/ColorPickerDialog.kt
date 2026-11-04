package org.everis.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ColorPickerDialog(initial: String, onDismiss: () -> Unit, onColor: (String) -> Unit) {
    val currentAccent = MaterialTheme.colorScheme.primary.toArgb()
    val initialHsv = remember(initial, currentAccent) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(runCatching { initial.toColorInt() }.getOrDefault(currentAccent), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    fun hex(h: Float, s: Float, v: Float) = String.format(Locale.ROOT, "#%06X", android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xFFFFFF)
    var text by remember { mutableStateOf(hex(hue, saturation, brightness)) }
    val valid = Regex("#[0-9A-Fa-f]{6}").matches(text)
    val preview = Color.hsv(hue, saturation, brightness)
    val updatePalette by rememberUpdatedState<(Float, Float) -> Unit>({ s, v ->
        saturation = s; brightness = v; text = hex(hue, s, v)
    })
    EverisAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.custom_color)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = preview, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(text, color = if (preview.luminance() > 0.179f) Color.Black else Color.White) }
                }
                Canvas(Modifier.fillMaxWidth().height(140.dp).pointerInput(Unit) {
                    fun pick(position: Offset) {
                        if (size.width > 0 && size.height > 0) updatePalette((position.x / size.width).coerceIn(0f, 1f), (1f - position.y / size.height).coerceIn(0f, 1f))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pick(down.position); down.consume()
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            pick(change.position); change.consume()
                        } while (change.pressed)
                    }
                }) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val point = Offset(saturation * size.width, (1f - brightness) * size.height)
                    drawCircle(Color.Black, 7.dp.toPx(), point, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 7.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
                }
                val hueLabel = stringResource(R.string.color_hue)
                val saturationLabel = stringResource(R.string.color_saturation)
                val brightnessLabel = stringResource(R.string.color_brightness)
                Text(hueLabel, style = MaterialTheme.typography.labelMedium)
                ColorValueSlider(hue, { hue = it; text = hex(it, saturation, brightness) }, hueLabel, 0f..360f)
                // Accessible alternatives to the two-dimensional touch palette.
                Text(saturationLabel, style = MaterialTheme.typography.labelMedium)
                ColorValueSlider(saturation, { saturation = it; text = hex(hue, it, brightness) }, saturationLabel)
                Text(brightnessLabel, style = MaterialTheme.typography.labelMedium)
                ColorValueSlider(brightness, { brightness = it; text = hex(hue, saturation, it) }, brightnessLabel)
                OutlinedTextField(value = text, onValueChange = { entered ->
                    text = "#" + entered.removePrefix("#").filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase(Locale.ROOT)
                    if (Regex("#[0-9A-F]{6}").matches(text)) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(text.toColorInt(), hsv)
                        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                    }
                }, label = { Text("HEX") }, placeholder = { Text("#RRGGBB") }, singleLine = true, isError = !valid)
            }
        },
        confirmButton = { EverisTextButton(onClick = { if (valid) onColor(text) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { EverisTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorValueSlider(value: Float, onChange: (Float) -> Unit, label: String, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    val slider = remember(range) { SliderState(value, 0, range) }
    slider.value = value
    Slider(state = slider, onValueChange = onChange, modifier = Modifier.semantics { contentDescription = label })
}
