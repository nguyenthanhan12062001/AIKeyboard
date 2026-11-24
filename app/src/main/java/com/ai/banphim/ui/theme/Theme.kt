package com.ai.banphim.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ai.banphim.data.KeyboardThemePreset

/** Supported companion app appearance modes. */
enum class AppAppearanceMode {
  LIGHT,
  DARK,
  AMOLED,
}

/** Extended semantic surface, border, gradient, and status tokens for all 3 appearance modes. */
@Immutable
data class AppExtendedColors(
  val appearanceMode: AppAppearanceMode,
  val canvasBackground: Color,
  val cardSurface: Color,
  val cardSurfaceElevated: Color,
  val cardSurfaceMuted: Color,
  val cardBorder: Color,
  val subtleBorder: Color,
  val electricBlue: Color,
  val heroGradientStart: Color,
  val heroGradientEnd: Color,
  val success: Color,
  val successContainer: Color,
  val onSuccessContainer: Color,
  val warning: Color,
  val warningContainer: Color,
  val onWarningContainer: Color,
  val isAmoled: Boolean,
)

private val LightColorScheme =
  lightColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = ElectricBlue600,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = CyanSecondaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF164E63),
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceMuted,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Indigo300,
    onPrimary = Color(0xFF0A0E26),
    primaryContainer = Color(0xFF232A52),
    onPrimaryContainer = Indigo100,
    secondary = ElectricBlue400,
    onSecondary = Color(0xFF081B38),
    secondaryContainer = Color(0xFF172B4D),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = CyanSecondaryDark,
    onTertiary = Color(0xFF04262E),
    tertiaryContainer = Color(0xFF0F3540),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurfaceMuted,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = ErrorDark,
    onError = Color(0xFF2D0505),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
  )

private val AmoledColorScheme =
  darkColorScheme(
    primary = Indigo300,
    onPrimary = Color(0xFF05081A),
    primaryContainer = Color(0xFF192045),
    onPrimaryContainer = Indigo100,
    secondary = ElectricBlue400,
    onSecondary = Color(0xFF05142C),
    secondaryContainer = Color(0xFF102140),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = CyanSecondaryDark,
    onTertiary = Color(0xFF031F26),
    tertiaryContainer = Color(0xFF0A2933),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = AmoledCanvas,
    onBackground = AmoledTextPrimary,
    surface = AmoledSurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledSurfaceElevated,
    onSurfaceVariant = AmoledTextSecondary,
    surfaceContainerLowest = AmoledCanvas,
    surfaceContainerLow = AmoledSurfaceMuted,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceElevated,
    outline = AmoledBorder,
    outlineVariant = AmoledBorderSubtle,
    error = ErrorDark,
    onError = Color(0xFF280404),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
  )

private val LightExtendedColors =
  AppExtendedColors(
    appearanceMode = AppAppearanceMode.LIGHT,
    canvasBackground = LightCanvas,
    cardSurface = LightSurface,
    cardSurfaceElevated = LightSurfaceElevated,
    cardSurfaceMuted = LightSurfaceMuted,
    cardBorder = LightBorder,
    subtleBorder = LightBorderSubtle,
    electricBlue = ElectricBlue600,
    heroGradientStart = Color(0xFFEEF2FF),
    heroGradientEnd = Color(0xFFE0F2FE),
    success = SuccessLight,
    successContainer = SuccessContainerLight,
    onSuccessContainer = OnSuccessContainerLight,
    warning = WarningLight,
    warningContainer = WarningContainerLight,
    onWarningContainer = OnWarningContainerLight,
    isAmoled = false,
  )

private val DarkExtendedColors =
  AppExtendedColors(
    appearanceMode = AppAppearanceMode.DARK,
    canvasBackground = DarkCanvas,
    cardSurface = DarkSurface,
    cardSurfaceElevated = DarkSurfaceElevated,
    cardSurfaceMuted = DarkSurfaceMuted,
    cardBorder = DarkBorder,
    subtleBorder = DarkBorderSubtle,
    electricBlue = ElectricBlue400,
    heroGradientStart = Color(0xFF1A2038),
    heroGradientEnd = Color(0xFF14243D),
    success = SuccessDark,
    successContainer = SuccessContainerDark,
    onSuccessContainer = OnSuccessContainerDark,
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = OnWarningContainerDark,
    isAmoled = false,
  )

private val AmoledExtendedColors =
  AppExtendedColors(
    appearanceMode = AppAppearanceMode.AMOLED,
    canvasBackground = AmoledCanvas,
    cardSurface = AmoledSurface,
    cardSurfaceElevated = AmoledSurfaceElevated,
    cardSurfaceMuted = AmoledSurfaceMuted,
    cardBorder = AmoledBorder,
    subtleBorder = AmoledBorderSubtle,
    electricBlue = ElectricBlue400,
    heroGradientStart = Color(0xFF11162B),
    heroGradientEnd = Color(0xFF0A172B),
    success = SuccessDark,
    successContainer = Color(0xFF05382B),
    onSuccessContainer = OnSuccessContainerDark,
    warning = WarningDark,
    warningContainer = Color(0xFF331403),
    onWarningContainer = OnWarningContainerDark,
    isAmoled = true,
  )

val LocalAppExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Resolves the [AppAppearanceMode] from the persisted [KeyboardThemePreset] and system dark mode. */
@Composable
fun resolveAppearanceMode(preset: KeyboardThemePreset): AppAppearanceMode {
  val systemDark = isSystemInDarkTheme()
  return when (preset) {
    KeyboardThemePreset.LIGHT -> AppAppearanceMode.LIGHT
    KeyboardThemePreset.DARK -> AppAppearanceMode.DARK
    KeyboardThemePreset.AMOLED -> AppAppearanceMode.AMOLED
    KeyboardThemePreset.DYNAMIC,
    KeyboardThemePreset.OCEAN -> if (systemDark) AppAppearanceMode.DARK else AppAppearanceMode.LIGHT
  }
}

/** Convenience accessor for [AppExtendedColors] inside Composables. */
object AppTheme {
  val colors: AppExtendedColors
    @Composable get() = LocalAppExtendedColors.current
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
  }

/**
 * Root Material 3 theme supporting Light, Dark, and pure AMOLED Black modes
 * with Indigo/Electric Blue brand identity and automatic system bar contrast.
 */
@Composable
fun AIBanPhimTheme(
  themePreset: KeyboardThemePreset = KeyboardThemePreset.DYNAMIC,
  content: @Composable () -> Unit,
) {
  val mode = resolveAppearanceMode(themePreset)
  val (scheme, extended) =
    when (mode) {
      AppAppearanceMode.LIGHT -> LightColorScheme to LightExtendedColors
      AppAppearanceMode.DARK -> DarkColorScheme to DarkExtendedColors
      AppAppearanceMode.AMOLED -> AmoledColorScheme to AmoledExtendedColors
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = view.context.findActivity()?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        val isLight = mode == AppAppearanceMode.LIGHT
        insetsController.isAppearanceLightStatusBars = isLight
        insetsController.isAppearanceLightNavigationBars = isLight
      }
    }
  }

  CompositionLocalProvider(LocalAppExtendedColors provides extended) {
    MaterialTheme(
      colorScheme = scheme,
      typography = AIBanPhimTypography,
      shapes = AIBanPhimShapes,
      content = content,
    )
  }
}
