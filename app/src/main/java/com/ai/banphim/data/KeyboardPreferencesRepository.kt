package com.ai.banphim.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.KeyboardLanguage
import com.ai.banphim.ime.engine.LocaleDefaultResolver
import com.ai.banphim.ime.engine.TonePlacementStyle
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.keyboardDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "banphim_settings")

/**
 * DataStore-backed repository persisting keyboard language, input method, visual theme,
 * floating coordinates, and AI translation preferences with locale-aware first-launch defaults.
 */
class KeyboardPreferencesRepository(private val context: Context) {

  private object Keys {
    val ACTIVE_LANGUAGE = stringPreferencesKey("active_language")
    val ENABLED_LANGUAGES = stringPreferencesKey("enabled_languages")
    val INPUT_MODE = stringPreferencesKey("input_mode")
    val TONE_STYLE = stringPreferencesKey("tone_style")
    val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
    val SHOW_SUGGESTIONS = booleanPreferencesKey("show_suggestions")
    val PERSONALIZED_SUGGESTIONS = booleanPreferencesKey("personalized_suggestions")
    val SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
    val FLOATING_MODE = booleanPreferencesKey("floating_mode")
    val FLOATING_X_FRACTION = floatPreferencesKey("floating_x_fraction")
    val FLOATING_Y_FRACTION = floatPreferencesKey("floating_y_fraction")
    val HAPTIC = booleanPreferencesKey("haptic_feedback")
    val SOUND = booleanPreferencesKey("sound_feedback")
    val HEIGHT_SCALE = floatPreferencesKey("height_scale")
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val APP_LANGUAGE = stringPreferencesKey("app_language_code")
    val AI_PRIVACY_ACCEPTED = booleanPreferencesKey("ai_privacy_accepted")
    val LIVE_TRANSLATE = booleanPreferencesKey("live_translate_as_you_type")
    val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
    val SOURCE_LANG = stringPreferencesKey("default_source_lang")
    val TARGET_LANG = stringPreferencesKey("default_target_lang")
    val PREFERRED_AI_MODEL = stringPreferencesKey("preferred_ai_model_id")
    val AUTO_SWITCH_AI_MODEL = booleanPreferencesKey("auto_switch_ai_model")
    val OPTIONAL_GEMINI_KEY = stringPreferencesKey("optional_gemini_key")
    val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val SEEN_DISCOVERY_AREAS = stringPreferencesKey("seen_discovery_areas")
    val DISCOVERY_TIPS_DISMISSED = booleanPreferencesKey("discovery_tips_dismissed")
    val ACTIVE_THEME_TYPE = stringPreferencesKey("active_theme_type")
    val ACTIVE_THEME_VALUE = stringPreferencesKey("active_theme_value")
    val CUSTOM_PHOTO_OPACITY = floatPreferencesKey("custom_photo_opacity")
    val CUSTOM_THEMES_SERIALIZED = stringPreferencesKey("custom_themes_serialized")
  }

  @Volatile private var cachedLocaleKey: String? = null
  @Volatile private var cachedDefaults: com.ai.banphim.ime.engine.LocaleDefaultResolver.InitialDefaults? = null

  private fun currentSystemLocale(): Locale = AppLocaleManager.systemLocale()

  private fun resolveSystemDefaults(): com.ai.banphim.ime.engine.LocaleDefaultResolver.InitialDefaults {
    val locale = currentSystemLocale()
    val key = locale.toLanguageTag()
    val cached = cachedDefaults
    if (cached != null && cachedLocaleKey == key) {
      return cached
    }
    return LocaleDefaultResolver.resolve(locale).also {
      cachedLocaleKey = key
      cachedDefaults = it
    }
  }

  /** Reactive stream of resolved [KeyboardSettings] combining stored values with locale defaults. */
  val settingsFlow: Flow<KeyboardSettings> =
    context.keyboardDataStore.data.map { prefs ->
      val systemDefaults = resolveSystemDefaults()

      val enabledLangs =
        prefs[Keys.ENABLED_LANGUAGES]
          ?.split("|")
          ?.filter { it.isNotBlank() }
          ?.map { KeyboardLanguage.fromCode(it) }
          ?.distinct()
          ?.takeIf { it.isNotEmpty() }
          ?: systemDefaults.enabledLanguages

      val activeLang =
        prefs[Keys.ACTIVE_LANGUAGE]?.let { KeyboardLanguage.fromCode(it) }
          ?: systemDefaults.activeLanguage

      val mode =
        prefs[Keys.INPUT_MODE]?.let { runCatching { InputMethodMode.valueOf(it) }.getOrNull() }
          ?: systemDefaults.inputMethodMode
      val toneStyle =
        prefs[Keys.TONE_STYLE]?.let { runCatching { TonePlacementStyle.valueOf(it) }.getOrNull() }
          ?: TonePlacementStyle.MODERN
      val theme =
        prefs[Keys.THEME_PRESET]?.let {
          runCatching { KeyboardThemePreset.valueOf(it) }.getOrNull()
        } ?: KeyboardThemePreset.DYNAMIC
      val recentsRaw = prefs[Keys.RECENT_EMOJIS]
      val recents =
        if (recentsRaw.isNullOrBlank()) {
          KeyboardSettings.DEFAULT_RECENT_EMOJIS
        } else {
          recentsRaw.split("|").filter { it.isNotBlank() }
        }

      val translationMode =
        prefs[Keys.TRANSLATION_MODE]?.let {
          runCatching { TranslationMode.valueOf(it) }.getOrNull()
        } ?: TranslationMode.AUTO_HYBRID

      KeyboardSettings(
        activeLanguage = activeLang,
        enabledLanguages =
          if (enabledLangs.contains(activeLang)) enabledLangs else listOf(activeLang) + enabledLangs,
        inputMethodMode = mode,
        tonePlacementStyle = toneStyle,
        autoCorrectionEnabled = prefs[Keys.AUTO_CORRECT] ?: true,
        showSuggestions = prefs[Keys.SHOW_SUGGESTIONS] ?: true,
        personalizedSuggestionsEnabled = prefs[Keys.PERSONALIZED_SUGGESTIONS] ?: true,
        showNumberRow = prefs[Keys.SHOW_NUMBER_ROW] ?: false,
        floatingMode = prefs[Keys.FLOATING_MODE] ?: false,
        floatingXFraction = (prefs[Keys.FLOATING_X_FRACTION] ?: 0.5f).coerceIn(0f, 1f),
        floatingYFraction = (prefs[Keys.FLOATING_Y_FRACTION] ?: 0.88f).coerceIn(0f, 1f),
        hapticFeedbackEnabled = prefs[Keys.HAPTIC] ?: true,
        soundFeedbackEnabled = prefs[Keys.SOUND] ?: false,
        keyboardHeightScale = (prefs[Keys.HEIGHT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.2f),
        themePreset = theme,
        appLanguageCode = AppLanguageOption.fromTag(prefs[Keys.APP_LANGUAGE]).tag,
        aiPrivacyAccepted = prefs[Keys.AI_PRIVACY_ACCEPTED] ?: false,
        liveTranslateAsYouType = prefs[Keys.LIVE_TRANSLATE] ?: true,
        translationMode = translationMode,
        defaultSourceLang = prefs[Keys.SOURCE_LANG] ?: "auto",
        defaultTargetLang = prefs[Keys.TARGET_LANG] ?: systemDefaults.defaultTranslateTargetCode,
        preferredAiModelId = prefs[Keys.PREFERRED_AI_MODEL]?.takeIf { it.isNotBlank() } ?: "auto",
        autoSwitchAiModel = prefs[Keys.AUTO_SWITCH_AI_MODEL] ?: true,
        optionalGeminiApiKey = prefs[Keys.OPTIONAL_GEMINI_KEY] ?: "",
        recentEmojis = recents,
        hasCompletedOnboarding = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
        seenDiscoveryAreas =
          prefs[Keys.SEEN_DISCOVERY_AREAS]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet(),
        discoveryTipsDismissed = prefs[Keys.DISCOVERY_TIPS_DISMISSED] ?: false,
        activeThemeType = prefs[Keys.ACTIVE_THEME_TYPE] ?: "PRESET",
        activeThemeValue = prefs[Keys.ACTIVE_THEME_VALUE] ?: theme.name,
        customPhotoOpacity = prefs[Keys.CUSTOM_PHOTO_OPACITY] ?: 0.7f,
        customThemes =
          prefs[Keys.CUSTOM_THEMES_SERIALIZED]
            ?.split("\n")
            ?.mapNotNull { line ->
              val parts = line.split(";;;")
              if (parts.size >= 4) {
                CustomThemeItem(
                  id = parts[0],
                  name = parts[1],
                  type = parts[2],
                  value = parts[3],
                  opacity = parts.getOrNull(4)?.toFloatOrNull() ?: 0.7f,
                )
              } else null
            } ?: emptyList(),
      )
    }

  /**
   * Persists system-locale defaults on first launch if no language preference has been saved yet,
   * so subsequent locale changes don't unexpectedly override the user's keyboard setup.
   */
  suspend fun ensureFirstLaunchDefaultsSaved() {
    val systemDefaults = LocaleDefaultResolver.resolve(currentSystemLocale())
    context.keyboardDataStore.edit { prefs ->
      if (prefs[Keys.ACTIVE_LANGUAGE] == null) {
        prefs[Keys.ACTIVE_LANGUAGE] = systemDefaults.activeLanguage.code
      }
      if (prefs[Keys.ENABLED_LANGUAGES] == null) {
        prefs[Keys.ENABLED_LANGUAGES] = systemDefaults.enabledLanguages.joinToString("|") { it.code }
      }
      if (prefs[Keys.INPUT_MODE] == null) {
        prefs[Keys.INPUT_MODE] = systemDefaults.inputMethodMode.name
      }
      if (prefs[Keys.TARGET_LANG] == null) {
        prefs[Keys.TARGET_LANG] = systemDefaults.defaultTranslateTargetCode
      }
    }
  }

  /** Switches the active typing [language] and ensures it is included in enabled languages. */
  suspend fun setActiveLanguage(language: KeyboardLanguage) {
    val systemDefaults = LocaleDefaultResolver.resolve(currentSystemLocale())
    context.keyboardDataStore.edit { prefs ->
      prefs[Keys.ACTIVE_LANGUAGE] = language.code
      val currentEnabled =
        prefs[Keys.ENABLED_LANGUAGES]
          ?.split("|")
          ?.filter { it.isNotBlank() }
          ?.map { KeyboardLanguage.fromCode(it) }
          ?.distinct()
          ?: systemDefaults.enabledLanguages
      if (!currentEnabled.contains(language)) {
        prefs[Keys.ENABLED_LANGUAGES] = (currentEnabled + language).joinToString("|") { it.code }
      }
    }
  }

  /** Toggles [language] in the Spacebar quick-switch list while keeping at least one language active. */
  suspend fun toggleLanguageEnabled(language: KeyboardLanguage) {
    val systemDefaults = LocaleDefaultResolver.resolve(currentSystemLocale())
    context.keyboardDataStore.edit { prefs ->
      val currentEnabled =
        prefs[Keys.ENABLED_LANGUAGES]
          ?.split("|")
          ?.filter { it.isNotBlank() }
          ?.map { KeyboardLanguage.fromCode(it) }
          ?.distinct()
          ?.toMutableList()
          ?: systemDefaults.enabledLanguages.toMutableList()

      if (currentEnabled.contains(language)) {
        if (currentEnabled.size <= 1) return@edit
        currentEnabled.remove(language)
        val curActive = prefs[Keys.ACTIVE_LANGUAGE]?.let { KeyboardLanguage.fromCode(it) }
        if (curActive == language) {
          prefs[Keys.ACTIVE_LANGUAGE] = currentEnabled.first().code
        }
      } else {
        currentEnabled.add(language)
      }
      prefs[Keys.ENABLED_LANGUAGES] = currentEnabled.joinToString("|") { it.code }
    }
  }

  /** Persists the Vietnamese input method mode (Telex, VNI, or Off). */
  suspend fun setInputMethodMode(mode: InputMethodMode) {
    context.keyboardDataStore.edit { it[Keys.INPUT_MODE] = mode.name }
  }

  /** Persists the Vietnamese tone placement rule (Modern or Classic). */
  suspend fun setTonePlacementStyle(style: TonePlacementStyle) {
    context.keyboardDataStore.edit { it[Keys.TONE_STYLE] = style.name }
  }

  /** Persists whether Space automatically applies common typo corrections. */
  suspend fun setAutoCorrectionEnabled(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.AUTO_CORRECT] = enabled }
  }

  /** Persists whether the offline word suggestion strip is displayed. */
  suspend fun setShowSuggestions(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.SHOW_SUGGESTIONS] = enabled }
  }

  /** Persists whether personalized typing-habit learning is enabled on-device. */
  suspend fun setPersonalizedSuggestionsEnabled(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.PERSONALIZED_SUGGESTIONS] = enabled }
  }

  /** Persists whether the fixed 1–0 number row appears above QWERTY letters. */
  suspend fun setShowNumberRow(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.SHOW_NUMBER_ROW] = enabled }
  }

  /** Persists whether the keyboard renders as a draggable floating window. */
  suspend fun setFloatingMode(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.FLOATING_MODE] = enabled }
  }

  /** Persists normalized drag coordinates ([0f..1f]) for floating keyboard mode. */
  suspend fun setFloatingPosition(xFraction: Float, yFraction: Float) {
    context.keyboardDataStore.edit {
      it[Keys.FLOATING_X_FRACTION] = xFraction.coerceIn(0f, 1f)
      it[Keys.FLOATING_Y_FRACTION] = yFraction.coerceIn(0f, 1f)
    }
  }

  /** Persists whether haptic vibration triggers on key taps. */
  suspend fun setHapticEnabled(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.HAPTIC] = enabled }
  }

  /** Persists whether system click audio plays on key taps. */
  suspend fun setSoundEnabled(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.SOUND] = enabled }
  }

  /** Persists vertical keyboard height scale clamped to [0.85f..1.20f]. */
  suspend fun setHeightScale(scale: Float) {
    context.keyboardDataStore.edit { it[Keys.HEIGHT_SCALE] = scale.coerceIn(0.85f, 1.2f) }
  }

  /** Persists the selected [KeyboardThemePreset]. */
  suspend fun setThemePreset(preset: KeyboardThemePreset) {
    context.keyboardDataStore.edit {
      it[Keys.THEME_PRESET] = preset.name
      it[Keys.ACTIVE_THEME_TYPE] = "PRESET"
      it[Keys.ACTIVE_THEME_VALUE] = preset.name
    }
  }

  /** Applies a preset theme or custom color / photo theme. */
  suspend fun applyTheme(type: String, value: String, opacity: Float = 0.7f) {
    context.keyboardDataStore.edit { prefs ->
      prefs[Keys.ACTIVE_THEME_TYPE] = type
      prefs[Keys.ACTIVE_THEME_VALUE] = value
      prefs[Keys.CUSTOM_PHOTO_OPACITY] = opacity
      if (type == "PRESET") {
        runCatching { KeyboardThemePreset.valueOf(value) }.getOrNull()?.let {
          prefs[Keys.THEME_PRESET] = it.name
        }
      }
    }
  }

  /** Adds or updates a custom theme in the user's saved themes list. */
  suspend fun saveCustomTheme(item: CustomThemeItem, applyNow: Boolean = true) {
    context.keyboardDataStore.edit { prefs ->
      val currentRaw = prefs[Keys.CUSTOM_THEMES_SERIALIZED] ?: ""
      val currentList = currentRaw.split("\n").filter { it.isNotBlank() }.toMutableList()
      currentList.removeAll { it.startsWith("${item.id};;;") }
      val serialized = "${item.id};;;${item.name};;;${item.type};;;${item.value};;;${item.opacity}"
      currentList.add(0, serialized)
      prefs[Keys.CUSTOM_THEMES_SERIALIZED] = currentList.joinToString("\n")

      if (applyNow) {
        prefs[Keys.ACTIVE_THEME_TYPE] = item.type
        prefs[Keys.ACTIVE_THEME_VALUE] = item.value
        prefs[Keys.CUSTOM_PHOTO_OPACITY] = item.opacity
      }
    }
  }

  /** Deletes a saved custom theme by ID. */
  suspend fun deleteCustomTheme(id: String) {
    context.keyboardDataStore.edit { prefs ->
      val currentRaw = prefs[Keys.CUSTOM_THEMES_SERIALIZED] ?: ""
      val currentList = currentRaw.split("\n").filter { it.isNotBlank() }.filterNot { it.startsWith("$id;;;") }
      prefs[Keys.CUSTOM_THEMES_SERIALIZED] = currentList.joinToString("\n")
      if (prefs[Keys.ACTIVE_THEME_VALUE] == id) {
        prefs[Keys.ACTIVE_THEME_TYPE] = "PRESET"
        prefs[Keys.ACTIVE_THEME_VALUE] = KeyboardThemePreset.DYNAMIC.name
        prefs[Keys.THEME_PRESET] = KeyboardThemePreset.DYNAMIC.name
      }
    }
  }

  /** Persists the chosen per-app UI language tag and synchronizes platform locale state. */
  suspend fun setAppLanguageCode(code: String) {
    val normalized = AppLanguageOption.fromTag(code).tag
    AppLocaleManager.saveSyncTag(context, normalized)
    context.keyboardDataStore.edit { it[Keys.APP_LANGUAGE] = normalized }
    AppLocaleManager.applyPlatformLocaleIfSupported(context, normalized)
  }

  /** Persists whether the user acknowledged the AI translation privacy notice. */
  suspend fun setAiPrivacyAccepted(accepted: Boolean) {
    context.keyboardDataStore.edit { it[Keys.AI_PRIVACY_ACCEPTED] = accepted }
  }

  /** Persists whether Gboard-style live translation while typing is enabled. */
  suspend fun setLiveTranslateAsYouType(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.LIVE_TRANSLATE] = enabled }
  }

  /** Persists the active translation engine routing mode (Auto Hybrid, Offline ML Kit, or Online AI). */
  suspend fun setTranslationMode(mode: TranslationMode) {
    context.keyboardDataStore.edit { it[Keys.TRANSLATION_MODE] = mode.name }
  }

  /** Persists default source and target language codes for AI translation. */
  suspend fun setLanguages(source: String, target: String) {
    context.keyboardDataStore.edit {
      it[Keys.SOURCE_LANG] = source
      it[Keys.TARGET_LANG] = target
    }
  }

  /** Persists the user's preferred free AI model ID (`"auto"` for Smart Auto). */
  suspend fun setPreferredAiModelId(modelId: String) {
    val clean = modelId.trim().ifBlank { "auto" }
    context.keyboardDataStore.edit { it[Keys.PREFERRED_AI_MODEL] = clean }
  }

  /** Persists whether the translation router automatically switches models when hitting free limits. */
  suspend fun setAutoSwitchAiModel(enabled: Boolean) {
    context.keyboardDataStore.edit { it[Keys.AUTO_SWITCH_AI_MODEL] = enabled }
  }

  /** Persists or clears the optional personal Gemini BYOK API key. */
  suspend fun setOptionalGeminiApiKey(key: String) {
    context.keyboardDataStore.edit { it[Keys.OPTIONAL_GEMINI_KEY] = key.trim() }
  }

  /** Moves [emoji] to the front of the 24-item recent emoji list. */
  suspend fun pushRecentEmoji(emoji: String) {
    context.keyboardDataStore.edit { prefs ->
      val current =
        prefs[Keys.RECENT_EMOJIS]?.split("|")?.filter { it.isNotBlank() }
          ?: KeyboardSettings.DEFAULT_RECENT_EMOJIS
      val updated = (listOf(emoji) + current.filter { it != emoji }).take(24)
      prefs[Keys.RECENT_EMOJIS] = updated.joinToString("|")
    }
  }

  /** Persists whether the user has finished or skipped the initial onboarding overview. */
  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.keyboardDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  /** Marks a feature discovery area (`home`, `settings`, `typing`, `theme`) as explored. */
  suspend fun markDiscoveryAreaSeen(areaId: String) {
    val clean = areaId.trim().lowercase()
    if (clean.isEmpty()) return
    context.keyboardDataStore.edit { prefs ->
      val current =
        prefs[Keys.SEEN_DISCOVERY_AREAS]
          ?.split("|")
          ?.filter { it.isNotBlank() }
          ?.toMutableSet()
          ?: mutableSetOf()
      current.add(clean)
      prefs[Keys.SEEN_DISCOVERY_AREAS] = current.joinToString("|")
    }
  }

  /** Marks all discovery areas as seen at once (e.g., when completing the full tour). */
  suspend fun markAllDiscoveryAreasSeen(areaIds: Collection<String>) {
    context.keyboardDataStore.edit { prefs ->
      val current =
        prefs[Keys.SEEN_DISCOVERY_AREAS]
          ?.split("|")
          ?.filter { it.isNotBlank() }
          ?.toMutableSet()
          ?: mutableSetOf()
      current.addAll(areaIds.map { it.trim().lowercase() }.filter { it.isNotEmpty() })
      prefs[Keys.SEEN_DISCOVERY_AREAS] = current.joinToString("|")
    }
  }

  /** Persists whether inline tab discovery banners are dismissed by the user. */
  suspend fun setDiscoveryTipsDismissed(dismissed: Boolean) {
    context.keyboardDataStore.edit { it[Keys.DISCOVERY_TIPS_DISMISSED] = dismissed }
  }

  /** Clears feature discovery progress so the user can replay contextual tours. */
  suspend fun resetFeatureDiscovery() {
    context.keyboardDataStore.edit {
      it.remove(Keys.SEEN_DISCOVERY_AREAS)
      it[Keys.DISCOVERY_TIPS_DISMISSED] = false
    }
  }
}
