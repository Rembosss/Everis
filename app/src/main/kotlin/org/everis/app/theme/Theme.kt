package org.everis.app.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.lerp
import org.everis.app.LocalPerformanceMode
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.google.android.material.color.MaterialColors
import androidx.core.graphics.ColorUtils

/** Same expressive spring curves, at 40% speed: stiffness is divided by 2.5 squared. */
class WelcomeMotionScheme(private val base: MotionScheme) : MotionScheme {
    private fun <T> slower(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = when (spec) {
        is SpringSpec<T> -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness / 6.25f, visibilityThreshold = spec.visibilityThreshold)
        else -> tween(durationMillis = 900)
    }
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = slower(base.defaultSpatialSpec())
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = slower(base.fastSpatialSpec())
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = slower(base.slowSpatialSpec())
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = slower(base.defaultEffectsSpec())
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = slower(base.fastEffectsSpec())
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = slower(base.slowEffectsSpec())
}

/** Public Material color-role API; no dependency on restricted internal HCT classes. */
private fun seededScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed.toArgb(), it) }
    fun roles(offset: Float, saturation: Float) = MaterialColors.getColorRoles(
        ColorUtils.HSLToColor(floatArrayOf((hsl[0] + offset) % 360f, saturation, 0.5f)), !dark)
    val primary = MaterialColors.getColorRoles(seed.toArgb(), !dark)
    val secondary = roles(0f, minOf(hsl[1], 0.28f))
    val tertiary = roles(60f, minOf(hsl[1], 0.45f))
    val inversePrimary = Color(MaterialColors.getColorRoles(seed.toArgb(), dark).accent)
    fun neutral(tone: Int) = Color(ColorUtils.blendARGB(
        ColorUtils.HSLToColor(floatArrayOf(0f, 0f, tone / 100f)), seed.toArgb(), 0.035f))
    val common = if (dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = Color(primary.accent), onPrimary = Color(primary.onAccent),
        primaryContainer = Color(primary.accentContainer), onPrimaryContainer = Color(primary.onAccentContainer),
        secondary = Color(secondary.accent), onSecondary = Color(secondary.onAccent),
        secondaryContainer = Color(secondary.accentContainer), onSecondaryContainer = Color(secondary.onAccentContainer),
        tertiary = Color(tertiary.accent), onTertiary = Color(tertiary.onAccent),
        tertiaryContainer = Color(tertiary.accentContainer), onTertiaryContainer = Color(tertiary.onAccentContainer),
        background = neutral(if (dark) 6 else 98), onBackground = neutral(if (dark) 90 else 10),
        surface = neutral(if (dark) 6 else 98), onSurface = neutral(if (dark) 90 else 10),
        surfaceVariant = neutral(if (dark) 30 else 90), onSurfaceVariant = neutral(if (dark) 80 else 30),
        outline = neutral(if (dark) 60 else 50), outlineVariant = neutral(if (dark) 30 else 80),
        inverseSurface = neutral(if (dark) 90 else 20), inverseOnSurface = neutral(if (dark) 20 else 95),
        inversePrimary = inversePrimary, surfaceTint = Color(primary.accent),
        surfaceContainerLowest = neutral(if (dark) 4 else 100), surfaceContainerLow = neutral(if (dark) 10 else 96),
        surfaceContainer = neutral(if (dark) 12 else 94), surfaceContainerHigh = neutral(if (dark) 17 else 92),
        surfaceContainerHighest = neutral(if (dark) 22 else 90),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EverisTheme(palette: String = "dynamic", themeMode: String = "system", blackTheme: Boolean = false, performanceMode: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = when (themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val baseColors = if (palette == "dynamic") {
        remember(context, configuration, dark) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
    } else remember(palette, dark) {
        val seed = when (palette) {
            "blue" -> Color(0xFF4056A4) // Preserve 0.2.0 prototype preferences.
            "green" -> Color(0xFF326B4B)
            else -> runCatching { Color(palette.toColorInt()) }.getOrDefault(Color(0xFF236B62))
        }
        seededScheme(seed, dark)
    }
    val colors = remember(baseColors, dark, blackTheme) {
        if (dark && blackTheme) baseColors.copy(background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF101010), surfaceContainerHigh = Color(0xFF181818),
            surfaceContainerHighest = Color(0xFF222222)) else baseColors
    }
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(colors) }
    var target by remember { mutableStateOf(colors) }
    val displayed = if (progress.value >= 1f) target else interpolateScheme(from, target, progress.value)
    val signature = listOf(colors.primary, colors.onPrimary, colors.secondary, colors.tertiary, colors.background,
        colors.onBackground, colors.surfaceContainerLow, colors.surfaceContainerHigh, colors.outline)
    LaunchedEffect(signature, performanceMode) {
        from = displayed
        progress.snapTo(0f)
        target = colors
        if (performanceMode) progress.snapTo(1f) else progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val motion = remember(performanceMode) { EverisMotionScheme(performanceMode) }
    CompositionLocalProvider(LocalPerformanceMode provides performanceMode) {
        MaterialExpressiveTheme(colorScheme = displayed, motionScheme = motion, content = content)
    }
}

private class EverisMotionScheme(private val efficient: Boolean) : MotionScheme {
    private fun <T> spec(ms: Int): FiniteAnimationSpec<T> = tween(if (efficient) minOf(ms, 160) else ms, easing = FastOutSlowInEasing)
    override fun <T> defaultSpatialSpec() = spec<T>(420)
    override fun <T> fastSpatialSpec() = spec<T>(260)
    override fun <T> slowSpatialSpec() = spec<T>(520)
    override fun <T> defaultEffectsSpec() = spec<T>(320)
    override fun <T> fastEffectsSpec() = spec<T>(220)
    override fun <T> slowEffectsSpec() = spec<T>(440)
}

private fun interpolateScheme(a: ColorScheme, b: ColorScheme, f: Float): ColorScheme = b.copy(
    primary = lerp(a.primary, b.primary, f), onPrimary = lerp(a.onPrimary, b.onPrimary, f),
    primaryContainer = lerp(a.primaryContainer, b.primaryContainer, f), onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, f),
    secondary = lerp(a.secondary, b.secondary, f), onSecondary = lerp(a.onSecondary, b.onSecondary, f),
    secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, f), onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, f),
    tertiary = lerp(a.tertiary, b.tertiary, f), onTertiary = lerp(a.onTertiary, b.onTertiary, f),
    tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, f), onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, f),
    background = lerp(a.background, b.background, f), onBackground = lerp(a.onBackground, b.onBackground, f),
    surface = lerp(a.surface, b.surface, f), onSurface = lerp(a.onSurface, b.onSurface, f),
    surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, f), onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, f),
    surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, f), surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, f),
    surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, f), surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, f),
    surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, f),
    surfaceBright = lerp(a.surfaceBright, b.surfaceBright, f), surfaceDim = lerp(a.surfaceDim, b.surfaceDim, f),
    outline = lerp(a.outline, b.outline, f), outlineVariant = lerp(a.outlineVariant, b.outlineVariant, f),
    inverseSurface = lerp(a.inverseSurface, b.inverseSurface, f), inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, f),
    inversePrimary = lerp(a.inversePrimary, b.inversePrimary, f), surfaceTint = lerp(a.surfaceTint, b.surfaceTint, f),
    error = lerp(a.error, b.error, f), onError = lerp(a.onError, b.onError, f),
    errorContainer = lerp(a.errorContainer, b.errorContainer, f), onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, f),
)
