package com.lifestream.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LsColors {
    val Night = Color(0xFF0A0E1C)
    val Deep = Color(0xFF111733)
    val Panel = Color(0xFF182046)
    val Crystal = Color(0xFF8AD8FF)
    val Mako = Color(0xFF6EF2B5)
    val Gold = Color(0xFFE8C46A)
    val Mist = Color(0xFFB8C2E0)
    val Faint = Color(0xFF6B7499)
}

private val scheme = darkColorScheme(
    primary = LsColors.Crystal,
    onPrimary = Color(0xFF04223A),
    primaryContainer = Color(0xFF123A5C),
    onPrimaryContainer = Color(0xFFD5EEFF),
    secondary = LsColors.Gold,
    onSecondary = Color(0xFF2E2200),
    tertiary = LsColors.Mako,
    onTertiary = Color(0xFF003824),
    background = LsColors.Night,
    onBackground = Color(0xFFE6EAF7),
    surface = LsColors.Night,
    onSurface = Color(0xFFE6EAF7),
    surfaceVariant = LsColors.Panel,
    onSurfaceVariant = LsColors.Mist,
    surfaceContainer = LsColors.Deep,
    surfaceContainerHigh = LsColors.Panel,
    surfaceContainerHighest = Color(0xFF212A57),
    surfaceContainerLow = Color(0xFF0E1328),
    outline = Color(0xFF3A4472),
    outlineVariant = Color(0xFF262F5A),
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
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
