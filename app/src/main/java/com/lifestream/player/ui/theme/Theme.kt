package com.lifestream.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's colours, always from the theme chosen in Settings. Reading them from Compose
 * recolours the UI as soon as the theme changes.
 */
object LsColors {
    private val p get() = ThemeState.current
    val Night: Color get() = p.night
    val Deep: Color get() = p.deep
    val Panel: Color get() = p.panel
    val Crystal: Color get() = p.primary
    val Mako: Color get() = p.accent
    val Gold: Color get() = p.gold
    val Mist: Color get() = p.mist
    val Faint: Color get() = lerp(p.panel, p.mist, 0.45f)
    /** Content drawn on top of the primary colour (e.g. the play button icon). */
    val OnPrimary: Color get() = p.night
}

/** The theme currently applied (set from [com.lifestream.player.data.AppSettings]). */
object ThemeState {
    var current by mutableStateOf(Palettes.default)
}

private fun schemeFor(p: Palette) = darkColorScheme(
    primary = p.primary,
    onPrimary = p.night,
    primaryContainer = lerp(p.panel, p.primary, 0.3f),
    onPrimaryContainer = p.text,
    secondary = p.gold,
    onSecondary = p.night,
    tertiary = p.accent,
    onTertiary = p.night,
    background = p.night,
    onBackground = p.text,
    surface = p.night,
    onSurface = p.text,
    surfaceVariant = p.panel,
    onSurfaceVariant = p.mist,
    surfaceContainer = p.deep,
    surfaceContainerHigh = p.panel,
    surfaceContainerHighest = lerp(p.panel, p.mist, 0.1f),
    surfaceContainerLow = lerp(p.night, p.deep, 0.5f),
    outline = lerp(p.panel, p.mist, 0.35f),
    outlineVariant = lerp(p.deep, p.mist, 0.15f),
)

private val base = Typography()

/** Serif display type gives headings an old-tome, storybook feel; body text stays clean. */
private val typography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LifestreamTheme(content: @Composable () -> Unit) {
    val palette = ThemeState.current
    val scheme = remember(palette) { schemeFor(palette) }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
