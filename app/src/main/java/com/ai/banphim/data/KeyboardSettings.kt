package com.ai.banphim.data

import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.KeyboardLanguage
import com.ai.banphim.ime.engine.TonePlacementStyle

/**
 * Immutable snapshot of user-configurable keyboard, typing engine, floating layout, and AI preferences.
 */
data class KeyboardSettings(
  val activeLanguage: KeyboardLanguage = KeyboardLanguage.VIETNAMESE,
  val enabledLanguages: List<KeyboardLanguage> =
    listOf(KeyboardLanguage.VIETNAMESE, KeyboardLanguage.ENGLISH),
  val inputMethodMode: InputMethodMode = InputMethodMode.TELEX,
  val tonePlacementStyle: TonePlacementStyle = TonePlacementStyle.MODERN,
  val autoCorrectionEnabled: Boolean = true,
  val showSuggestions: Boolean = true,
  val personalizedSuggestionsEnabled: Boolean = true,
  val showNumberRow: Boolean = false,
  val floatingMode: Boolean = false,
  val floatingXFraction: Float = 0.5f,
  val floatingYFraction: Float = 0.88f,
  val hapticFeedbackEnabled: Boolean = true,
  val soundFeedbackEnabled: Boolean = false,
  val keyboardHeightScale: Float = 1.0f,
  val themePreset: KeyboardThemePreset = KeyboardThemePreset.DYNAMIC,
  val appLanguageCode: String = "",
  val aiPrivacyAccepted: Boolean = false,
  val liveTranslateAsYouType: Boolean = true,
  val translationMode: TranslationMode = TranslationMode.AUTO_HYBRID,
  val defaultSourceLang: String = "auto",
  val defaultTargetLang: String = "en",
  val preferredAiModelId: String = "auto",
  val autoSwitchAiModel: Boolean = true,
  val optionalGeminiApiKey: String = "",
  val recentEmojis: List<String> = DEFAULT_RECENT_EMOJIS,
  val hasCompletedOnboarding: Boolean = false,
  val seenDiscoveryAreas: Set<String> = emptySet(),
  val discoveryTipsDismissed: Boolean = false,
  val activeThemeType: String = "PRESET", // "PRESET", "COLOR", "PHOTO"
  val activeThemeValue: String = "DYNAMIC",
  val customPhotoOpacity: Float = 0.7f,
  val customThemes: List<CustomThemeItem> = emptyList(),
) {
  companion object {
    /** Initial recent emoji set displayed before user customization. */
    val DEFAULT_RECENT_EMOJIS =
      listOf("😀", "😂", "🥰", "👍", "❤️", "🙏", "😭", "🔥", "🎉", "👌", "✨", "🤔")
  }
}

data class CustomThemeItem(
  val id: String,
  val name: String,
  val type: String, // "COLOR" or "PHOTO"
  val value: String, // color int string or photo uri
  val opacity: Float = 0.7f,
)
