package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class HaloSemanticColors(
  val stealthAccent: Color,
  val stealthContainer: Color,
  val onStealthContainer: Color,
  val priorityAccent: Color,
  val priorityContainer: Color,
  val heroStart: Color,
  val heroEnd: Color,
  val monoNumberSurface: Color,
)

val LocalHaloColors =
  staticCompositionLocalOf {
    HaloSemanticColors(
      stealthAccent = StealthAmberLight,
      stealthContainer = StealthAmberContainerLight,
      onStealthContainer = OnStealthAmberContainerLight,
      priorityAccent = PriorityRoseLight,
      priorityContainer = PriorityRoseContainerLight,
      heroStart = EmeraldDeep,
      heroEnd = Emerald40,
      monoNumberSurface = SoftSurfaceVariant,
    )
  }

private val DarkColorScheme =
  darkColorScheme(
    primary = Emerald80,
    onPrimary = OnEmeraldContainerLight,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = ObsidianBg,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = TealAccentDark,
    tertiaryContainer = TealContainerDark,
    background = ObsidianBg,
    onBackground = MistText,
    surface = ObsidianSurface,
    onSurface = MistText,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = MutedMistText,
    outline = SubtleOutlineDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Emerald40,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = OnSlateContainerLight,
    tertiary = TealAccentLight,
    tertiaryContainer = TealContainerLight,
    background = AlabasterBg,
    onBackground = InkText,
    surface = CrispSurface,
    onSurface = InkText,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = MutedInkText,
    outline = SubtleOutlineLight,
  )

private val AppShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun HaloAntriTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors =
    if (darkTheme) {
      HaloSemanticColors(
        stealthAccent = StealthAmberDark,
        stealthContainer = StealthAmberContainerDark,
        onStealthContainer = OnStealthAmberContainerDark,
        priorityAccent = PriorityRoseDark,
        priorityContainer = PriorityRoseContainerDark,
        heroStart = EmeraldContainerDark,
        heroEnd = ObsidianSurfaceElevated,
        monoNumberSurface = ObsidianSurfaceElevated,
      )
    } else {
      HaloSemanticColors(
        stealthAccent = StealthAmberLight,
        stealthContainer = StealthAmberContainerLight,
        onStealthContainer = OnStealthAmberContainerLight,
        priorityAccent = PriorityRoseLight,
        priorityContainer = PriorityRoseContainerLight,
        heroStart = EmeraldDeep,
        heroEnd = EmeraldBright,
        monoNumberSurface = SoftSurfaceVariant,
      )
    }

  CompositionLocalProvider(LocalHaloColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = AppShapes,
      content = content,
    )
  }
}
