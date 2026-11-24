package com.ai.banphim.ime.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.ai.banphim.data.KeyboardThemePreset

/**
 * Color token set applied consistently across both the Java Canvas keyboard surface and Compose overlays.
 */
data class KeyboardThemePalette(
  val isDark: Boolean,
  val keyboardBackground: Int,
  val toolbarBackground: Int,
  val keyBackground: Int,
  val keyPressedBackground: Int,
  val specialKeyBackground: Int,
  val enterKeyBackground: Int,
  val enterKeyText: Int,
  val keyText: Int,
  val keyHintText: Int,
  val keyShadow: Int,
  val keyDivider: Int,
  val accent: Int,
  val popupBackground: Int,
  val popupText: Int,
) {
  companion object {
    /** Resolves concrete ARGB colors for [preset] honoring current system dark/light configuration. */
    fun resolve(context: Context, preset: KeyboardThemePreset): KeyboardThemePalette {
      val sysDark =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
          Configuration.UI_MODE_NIGHT_YES

      return when (preset) {
        KeyboardThemePreset.DYNAMIC -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme =
              if (sysDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            KeyboardThemePalette(
              isDark = sysDark,
              keyboardBackground = scheme.surfaceContainerLow.toArgb(),
              toolbarBackground = scheme.surfaceContainer.toArgb(),
              keyBackground =
                if (sysDark) scheme.surfaceContainerHigh.toArgb() else Color.WHITE,
              keyPressedBackground = scheme.primaryContainer.toArgb(),
              specialKeyBackground = scheme.surfaceContainerHighest.toArgb(),
              enterKeyBackground = scheme.primary.toArgb(),
              enterKeyText = scheme.onPrimary.toArgb(),
              keyText = scheme.onSurface.toArgb(),
              keyHintText = scheme.onSurfaceVariant.toArgb(),
              keyShadow = if (sysDark) 0x44000000 else 0x22000000,
              keyDivider = scheme.outlineVariant.toArgb(),
              accent = scheme.primary.toArgb(),
              popupBackground = scheme.surfaceContainerHighest.toArgb(),
              popupText = scheme.onSurface.toArgb(),
            )
          } else {
            if (sysDark) darkPalette() else lightPalette()
          }
        }
        KeyboardThemePreset.LIGHT -> lightPalette()
        KeyboardThemePreset.DARK -> darkPalette()
        KeyboardThemePreset.AMOLED ->
          KeyboardThemePalette(
            isDark = true,
            keyboardBackground = 0xFF000000.toInt(),
            toolbarBackground = 0xFF0A0A0A.toInt(),
            keyBackground = 0xFF1A1A1A.toInt(),
            keyPressedBackground = 0xFF2D3A4D.toInt(),
            specialKeyBackground = 0xFF121212.toInt(),
            enterKeyBackground = 0xFF1A73E8.toInt(),
            enterKeyText = Color.WHITE,
            keyText = 0xFFF2F2F2.toInt(),
            keyHintText = 0xFF8E959E.toInt(),
            keyShadow = 0x00000000,
            keyDivider = 0xFF262626.toInt(),
            accent = 0xFF4C9BFF.toInt(),
            popupBackground = 0xFF242629.toInt(),
            popupText = Color.WHITE,
          )
        KeyboardThemePreset.OCEAN ->
          KeyboardThemePalette(
            isDark = true,
            keyboardBackground = 0xFF0F1E2E.toInt(),
            toolbarBackground = 0xFF13263B.toInt(),
            keyBackground = 0xFF1D354D.toInt(),
            keyPressedBackground = 0xFF2B547E.toInt(),
            specialKeyBackground = 0xFF162B40.toInt(),
            enterKeyBackground = 0xFF00B4D8.toInt(),
            enterKeyText = 0xFF061A26.toInt(),
            keyText = 0xFFE8F4FA.toInt(),
            keyHintText = 0xFF90B8D4.toInt(),
            keyShadow = 0x33000000,
            keyDivider = 0xFF284866.toInt(),
            accent = 0xFF38BDF8.toInt(),
            popupBackground = 0xFF244361.toInt(),
            popupText = Color.WHITE,
          )
      }
    }

    private fun lightPalette() =
      KeyboardThemePalette(
        isDark = false,
        keyboardBackground = 0xFFECEFF4.toInt(),
        toolbarBackground = 0xFFE4E8EF.toInt(),
        keyBackground = 0xFFFFFFFF.toInt(),
        keyPressedBackground = 0xFFD2E3FC.toInt(),
        specialKeyBackground = 0xFFD8DEE9.toInt(),
        enterKeyBackground = 0xFF1A73E8.toInt(),
        enterKeyText = Color.WHITE,
        keyText = 0xFF1F2329.toInt(),
        keyHintText = 0xFF6B7280.toInt(),
        keyShadow = 0x26000000,
        keyDivider = 0xFFCBD5E1.toInt(),
        accent = 0xFF1A73E8.toInt(),
        popupBackground = 0xFFFFFFFF.toInt(),
        popupText = 0xFF1F2329.toInt(),
      )

    private fun darkPalette() =
      KeyboardThemePalette(
        isDark = true,
        keyboardBackground = 0xFF1B1C1E.toInt(),
        toolbarBackground = 0xFF222427.toInt(),
        keyBackground = 0xFF2E3136.toInt(),
        keyPressedBackground = 0xFF3E4C5E.toInt(),
        specialKeyBackground = 0xFF25282C.toInt(),
        enterKeyBackground = 0xFF8AB4F8.toInt(),
        enterKeyText = 0xFF0E1D33.toInt(),
        keyText = 0xFFECEFF1.toInt(),
        keyHintText = 0xFF9AA0A6.toInt(),
        keyShadow = 0x44000000,
        keyDivider = 0xFF3C4043.toInt(),
        accent = 0xFF8AB4F8.toInt(),
        popupBackground = 0xFF3C4043.toInt(),
        popupText = Color.WHITE,
      )

    /** Resolves concrete ARGB colors honoring activeThemeType (PRESET, COLOR, PHOTO). */
    fun resolve(context: Context, settings: com.ai.banphim.data.KeyboardSettings): KeyboardThemePalette {
      return when (settings.activeThemeType) {
        "COLOR" -> {
          val colorInt = settings.activeThemeValue.toIntOrNull() ?: 0xFF1A73E8.toInt()
          resolveCustomColor(colorInt)
        }
        "PHOTO" -> {
          photoPalette(settings.customPhotoOpacity)
        }
        else -> {
          val preset = runCatching { KeyboardThemePreset.valueOf(settings.activeThemeValue) }
            .getOrDefault(settings.themePreset)
          resolve(context, preset)
        }
      }
    }

    private fun resolveCustomColor(color: Int): KeyboardThemePalette {
      val r = Color.red(color) / 255.0
      val g = Color.green(color) / 255.0
      val b = Color.blue(color) / 255.0
      val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
      val isDark = lum < 0.5

      return if (isDark) {
        KeyboardThemePalette(
          isDark = true,
          keyboardBackground = color,
          toolbarBackground = adjustAlpha(color, 0.9f),
          keyBackground = 0x33FFFFFF,
          keyPressedBackground = 0x55FFFFFF,
          specialKeyBackground = 0x22000000,
          enterKeyBackground = 0xFFFFFFFF.toInt(),
          enterKeyText = color,
          keyText = Color.WHITE,
          keyHintText = 0xAAFFFFFF.toInt(),
          keyShadow = 0x44000000,
          keyDivider = 0x33FFFFFF,
          accent = 0xFFFFFFFF.toInt(),
          popupBackground = 0xFF2A2D32.toInt(),
          popupText = Color.WHITE,
        )
      } else {
        KeyboardThemePalette(
          isDark = false,
          keyboardBackground = color,
          toolbarBackground = adjustAlpha(color, 0.9f),
          keyBackground = Color.WHITE,
          keyPressedBackground = 0x33000000,
          specialKeyBackground = 0x1A000000,
          enterKeyBackground = 0xFF1A73E8.toInt(),
          enterKeyText = Color.WHITE,
          keyText = 0xFF1F2329.toInt(),
          keyHintText = 0xFF6B7280.toInt(),
          keyShadow = 0x22000000,
          keyDivider = 0x22000000,
          accent = 0xFF1A73E8.toInt(),
          popupBackground = Color.WHITE,
          popupText = 0xFF1F2329.toInt(),
        )
      }
    }

    private fun photoPalette(opacity: Float): KeyboardThemePalette {
      return KeyboardThemePalette(
        isDark = true,
        keyboardBackground = Color.BLACK,
        toolbarBackground = 0x99000000.toInt(),
        keyBackground = 0x40FFFFFF,
        keyPressedBackground = 0x70FFFFFF,
        specialKeyBackground = 0x2A000000,
        enterKeyBackground = 0xFF1A73E8.toInt(),
        enterKeyText = Color.WHITE,
        keyText = Color.WHITE,
        keyHintText = 0xCCFFFFFF.toInt(),
        keyShadow = 0x44000000,
        keyDivider = 0x33FFFFFF,
        accent = 0xFF8AB4F8.toInt(),
        popupBackground = 0xEE1E1E1E.toInt(),
        popupText = Color.WHITE,
      )
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
      val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
  }
}
