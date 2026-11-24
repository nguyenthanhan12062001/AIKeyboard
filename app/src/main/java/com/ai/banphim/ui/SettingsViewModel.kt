package com.ai.banphim.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.banphim.R
import com.ai.banphim.ai.AiProviderManager
import com.ai.banphim.ai.CatalogSnapshot
import com.ai.banphim.ai.ModelCatalogRepository
import com.ai.banphim.ai.SupportedLanguage
import com.ai.banphim.ai.TranslationResult
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.data.ClipboardClipEntity
import com.ai.banphim.data.ClipboardDatabase
import com.ai.banphim.data.ClipboardRepository
import com.ai.banphim.data.CustomThemeItem
import com.ai.banphim.data.KeyboardPreferencesRepository
import com.ai.banphim.data.KeyboardSettings
import com.ai.banphim.data.KeyboardThemePreset
import com.ai.banphim.data.PersonalDictionaryDatabase
import com.ai.banphim.data.PersonalDictionaryRepository
import com.ai.banphim.data.PersonalLexiconStats
import com.ai.banphim.data.TranslationMode
import com.ai.banphim.ime.BanPhimImeService
import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.KeyboardLanguage
import com.ai.banphim.ime.engine.TonePlacementStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Snapshot of whether the keyboard is enabled in Android system settings and currently active.
 */
data class ImeSetupStatus(
  val isEnabled: Boolean = false,
  val isSelectedDefault: Boolean = false,
)

/**
 * UI state for the AI translation diagnostics and catalog panel in Settings.
 */
data class AiStatusUiState(
  val catalog: CatalogSnapshot,
  val isRefreshingCatalog: Boolean = false,
  val refreshError: String? = null,
  val isTestingTranslation: Boolean = false,
  val testResult: TranslationResult? = null,
  val cooldownSecondsByModel: Map<String, Long> = emptyMap(),
  val downloadedOfflineLangs: Set<String> = emptySet(),
  val downloadingOfflineLangCode: String? = null,
  val offlinePackError: String? = null,
)

/**
 * ViewModel backing the keyboard companion Settings, Clipboard manager, AI diagnostics, and About screen.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
  private val prefsRepo = KeyboardPreferencesRepository(application)
  private val clipboardRepo =
    ClipboardRepository(ClipboardDatabase.getInstance(application).clipboardDao())
  private val personalDictRepo =
    PersonalDictionaryRepository(PersonalDictionaryDatabase.getInstance(application).personalDictionaryDao())
  private val aiManager = AiProviderManager(application)

  private val _settingsLoaded = MutableStateFlow(false)
  /** Emits `true` once the first real DataStore snapshot has been loaded from disk. */
  val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

  /** Live stream of user keyboard preferences persisted in DataStore. */
  val settings: StateFlow<KeyboardSettings> =
    prefsRepo.settingsFlow
      .onEach { _settingsLoaded.value = true }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        KeyboardSettings(),
      )

  /** Live stream of locally saved and pinned clipboard clips. */
  val clips: StateFlow<List<ClipboardClipEntity>> =
    clipboardRepo.clipsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  /** Live stream of encrypted personal lexicon word & bigram counts. */
  val personalLexiconStats: StateFlow<PersonalLexiconStats> =
    personalDictRepo.statsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      PersonalLexiconStats(),
    )

  private val _imeStatus = MutableStateFlow(ImeSetupStatus())
  /** Observable IME activation status in Android system settings. */
  val imeStatus: StateFlow<ImeSetupStatus> = _imeStatus.asStateFlow()

  private val _aiStatus =
    MutableStateFlow(
      AiStatusUiState(
        catalog = CatalogSnapshot(ModelCatalogRepository.DEFAULT_FALLBACK_MODELS, 0L, fromNetwork = false)
      )
    )
  /** Observable state of the free Kilo AI catalog, offline ML Kit packs, and quick translation tester. */
  val aiStatus: StateFlow<AiStatusUiState> = _aiStatus.asStateFlow()

  init {
    viewModelScope.launch { prefsRepo.ensureFirstLaunchDefaultsSaved() }
    viewModelScope.launch {
      val cached = withContext(Dispatchers.IO) { aiManager.catalogRepository.getCachedOrFallbackCatalog() }
      _aiStatus.update { it.copy(catalog = cached) }
      if (cached.updatedAtMs == 0L) {
        refreshCatalog()
      }
    }
    refreshImeStatus()
    refreshOfflinePacks()
  }

  /** Re-queries Android [InputMethodManager] and [Settings.Secure] off Main Thread to update setup checklist state. */
  fun refreshImeStatus() {
    viewModelScope.launch {
      val status =
        withContext(Dispatchers.IO) {
          runCatching {
            val ctx = getApplication<Application>()
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val expectedId = ComponentName(ctx, BanPhimImeService::class.java).flattenToShortString()
            val enabled =
              imm?.enabledInputMethodList?.any {
                it.packageName == ctx.packageName || it.id == expectedId
              } == true
            val defaultIme =
              Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                .orEmpty()
            val selected =
              defaultIme == expectedId || defaultIme.startsWith("${ctx.packageName}/")
            ImeSetupStatus(isEnabled = enabled, isSelectedDefault = selected)
          }.getOrDefault(ImeSetupStatus())
        }
      _imeStatus.value = status
    }
  }

  /** Sets the active keyboard input language and enables it if needed. */
  fun updateActiveLanguage(language: KeyboardLanguage) =
    viewModelScope.launch { prefsRepo.setActiveLanguage(language) }

  /** Toggles whether [language] appears in the quick-switch language rotation. */
  fun toggleLanguageEnabled(language: KeyboardLanguage) =
    viewModelScope.launch { prefsRepo.toggleLanguageEnabled(language) }

  /** Updates the Vietnamese input engine mode (Telex, VNI, or Off). */
  fun updateInputMethodMode(mode: InputMethodMode) =
    viewModelScope.launch { prefsRepo.setInputMethodMode(mode) }

  /** Updates the Vietnamese tone placement rule (Modern or Classic). */
  fun updateTonePlacementStyle(style: TonePlacementStyle) =
    viewModelScope.launch { prefsRepo.setTonePlacementStyle(style) }

  /** Enables or disables automatic typo correction on Space. */
  fun updateAutoCorrect(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setAutoCorrectionEnabled(enabled) }

  /** Shows or hides the offline suggestion strip above the keyboard. */
  fun updateShowSuggestions(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setShowSuggestions(enabled) }

  /** Enables or disables privacy-safe on-device typing-habit learning. */
  fun updatePersonalizedSuggestions(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setPersonalizedSuggestionsEnabled(enabled) }

  /** Permanently deletes all learned words and word pairs from the encrypted personal lexicon. */
  fun clearPersonalLexicon() =
    viewModelScope.launch { personalDictRepo.clearAll() }

  /** Shows or hides the fixed 1–0 numeric row on alphabetic layouts. */
  fun updateShowNumberRow(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setShowNumberRow(enabled) }

  /** Toggles Gboard-style draggable floating keyboard mode. */
  fun updateFloatingMode(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setFloatingMode(enabled) }

  /** Enables or disables keypress haptic vibration. */
  fun updateHaptic(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setHapticEnabled(enabled) }

  /** Enables or disables system click sounds on keypress. */
  fun updateSound(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setSoundEnabled(enabled) }

  /** Adjusts keyboard vertical height multiplier within supported bounds. */
  fun updateHeightScale(scale: Float) =
    viewModelScope.launch { prefsRepo.setHeightScale(scale) }

  /** Selects the active visual [KeyboardThemePreset]. */
  fun updateThemePreset(preset: KeyboardThemePreset) =
    viewModelScope.launch { prefsRepo.setThemePreset(preset) }

  /** Applies a custom color or personal photo theme with optional opacity. */
  fun applyCustomTheme(type: String, value: String, opacity: Float) =
    viewModelScope.launch { prefsRepo.applyTheme(type, value, opacity) }

  /** Saves a custom theme item and optionally applies it immediately. */
  fun saveCustomTheme(item: CustomThemeItem, applyNow: Boolean = true) =
    viewModelScope.launch { prefsRepo.saveCustomTheme(item, applyNow) }

  /** Updates the per-app UI language tag (empty string for system default). */
  fun updateAppLanguage(code: String) =
    viewModelScope.launch { prefsRepo.setAppLanguageCode(code) }

  /** Updates default source and target languages for the AI translation bar. */
  fun updateLanguages(src: String, tgt: String) =
    viewModelScope.launch { prefsRepo.setLanguages(src, tgt) }

  /** Enables or disables Gboard-style live translation as you type. */
  fun updateLiveTranslate(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setLiveTranslateAsYouType(enabled) }

  /** Updates the translation engine routing mode (Auto Hybrid, Offline ML Kit, or Online AI). */
  fun updateTranslationMode(mode: TranslationMode) =
    viewModelScope.launch { prefsRepo.setTranslationMode(mode) }

  /** Sets the user's preferred free AI model ID (`"auto"` for Smart Auto). */
  fun updatePreferredAiModel(modelId: String) =
    viewModelScope.launch { prefsRepo.setPreferredAiModelId(modelId) }

  /** Enables or disables automatic model failover when hitting a free limit. */
  fun updateAutoSwitchAiModel(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setAutoSwitchAiModel(enabled) }

  /** Stores or clears the optional personal Gemini BYOK API key. */
  fun updateOptionalGeminiKey(key: String) =
    viewModelScope.launch { prefsRepo.setOptionalGeminiApiKey(key) }

  /** Toggles the pinned status of the clip with [id]. */
  fun togglePinClip(id: Long) =
    viewModelScope.launch { clipboardRepo.togglePin(id) }

  /** Saves and immediately pins [rawText] to the local clipboard store. */
  fun addPinnedClip(rawText: String) =
    viewModelScope.launch { clipboardRepo.pinOrInsert(rawText) }

  /** Deletes all unpinned clipboard items while preserving pinned entries. */
  fun clearUnpinnedClips() =
    viewModelScope.launch { clipboardRepo.clearUnpinned() }

  /** Deletes a single clipboard clip by [id]. */
  fun deleteClip(id: Long) =
    viewModelScope.launch { clipboardRepo.deleteById(id) }

  /** Fetches the latest list of free models from Kilo AI Gateway. */
  fun refreshCatalog() {
    viewModelScope.launch {
      _aiStatus.update { it.copy(isRefreshingCatalog = true, refreshError = null) }
      val fresh = aiManager.refreshFreeModels()
      if (fresh != null) {
        _aiStatus.update {
          it.copy(
            catalog = fresh,
            isRefreshingCatalog = false,
            refreshError = null,
            cooldownSecondsByModel = aiManager.getActiveCooldownsSeconds(),
          )
        }
      } else {
        _aiStatus.update {
          it.copy(
            isRefreshingCatalog = false,
            refreshError =
              AppLocaleManager.getString(getApplication(), R.string.ai_refresh_error),
            cooldownSecondsByModel = aiManager.getActiveCooldownsSeconds(),
          )
        }
      }
    }
  }

  /** Refreshes the list of currently downloaded Google ML Kit offline language packs. */
  fun refreshOfflinePacks() {
    viewModelScope.launch {
      val downloaded = aiManager.offlineManager.getDownloadedLanguages()
      _aiStatus.update { it.copy(downloadedOfflineLangs = downloaded) }
    }
  }

  /** Downloads a Google ML Kit offline language pack (~30 MB). */
  fun downloadOfflinePack(langCode: String) {
    viewModelScope.launch {
      _aiStatus.update { it.copy(downloadingOfflineLangCode = langCode, offlinePackError = null) }
      val result = aiManager.offlineManager.downloadLanguage(langCode)
      val downloaded = aiManager.offlineManager.getDownloadedLanguages()
      _aiStatus.update {
        it.copy(
          downloadedOfflineLangs = downloaded,
          downloadingOfflineLangCode = null,
          offlinePackError =
            if (result.isFailure) {
              AppLocaleManager.getString(getApplication(), R.string.offline_pack_error_msg)
            } else {
              null
            },
        )
      }
    }
  }

  /** Deletes a previously downloaded ML Kit offline language pack to free space. */
  fun deleteOfflinePack(langCode: String) {
    viewModelScope.launch {
      aiManager.offlineManager.deleteLanguage(langCode)
      val downloaded = aiManager.offlineManager.getDownloadedLanguages()
      _aiStatus.update { it.copy(downloadedOfflineLangs = downloaded, offlinePackError = null) }
    }
  }

  /** Runs an end-to-end sample translation against the active translation mode chain. */
  fun runQuickTranslationTest(sampleText: String) {
    viewModelScope.launch {
      _aiStatus.update { it.copy(isTestingTranslation = true, testResult = null) }
      val s = settings.value
      val res =
        aiManager.translate(
          rawText = sampleText,
          sourceLang = SupportedLanguage.findByCode(s.defaultSourceLang),
          targetLang = SupportedLanguage.findByCode(s.defaultTargetLang),
          preferredModelId = s.preferredAiModelId,
          autoSwitchOnLimit = s.autoSwitchAiModel,
          optionalGeminiKey = s.optionalGeminiApiKey,
          translationMode = s.translationMode,
        )
      _aiStatus.update {
        it.copy(
          isTestingTranslation = false,
          testResult = res,
          cooldownSecondsByModel = aiManager.getActiveCooldownsSeconds(),
        )
      }
    }
  }

  /** Marks the initial onboarding overview as completed (or reset for replay). */
  fun setOnboardingCompleted(completed: Boolean) =
    viewModelScope.launch { prefsRepo.setOnboardingCompleted(completed) }

  /** Records that the user explored or completed the feature tour for [areaId]. */
  fun markDiscoveryAreaSeen(areaId: String) =
    viewModelScope.launch { prefsRepo.markDiscoveryAreaSeen(areaId) }

  /** Records all feature discovery areas as explored. */
  fun markAllDiscoveryAreasSeen(areaIds: Collection<String>) =
    viewModelScope.launch { prefsRepo.markAllDiscoveryAreasSeen(areaIds) }

  /** Suppresses or restores inline per-tab discovery tip banners. */
  fun setDiscoveryTipsDismissed(dismissed: Boolean) =
    viewModelScope.launch { prefsRepo.setDiscoveryTipsDismissed(dismissed) }

  /** Resets discovery progress so tours can be replayed from scratch. */
  fun resetFeatureDiscovery() =
    viewModelScope.launch { prefsRepo.resetFeatureDiscovery() }
}
