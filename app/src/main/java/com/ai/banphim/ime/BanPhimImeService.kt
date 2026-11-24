package com.ai.banphim.ime

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.banphim.MainActivity
import com.ai.banphim.R
import com.ai.banphim.ai.AiProviderManager
import com.ai.banphim.ai.SupportedLanguage
import com.ai.banphim.ai.TranslationResult
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.data.ClipboardDatabase
import com.ai.banphim.data.ClipboardRepository
import com.ai.banphim.data.KeyboardPreferencesRepository
import com.ai.banphim.data.KeyboardSettings
import com.ai.banphim.data.PersonalDictionaryDatabase
import com.ai.banphim.data.PersonalDictionaryRepository
import com.ai.banphim.data.TranslationMode
import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.JapaneseRomajiEngine
import com.ai.banphim.ime.engine.KeyboardLanguage
import com.ai.banphim.ime.engine.KoreanHangulEngine
import com.ai.banphim.ime.engine.PinyinEngine
import com.ai.banphim.ime.engine.VietnameseImeEngine
import com.ai.banphim.ime.suggestion.WordSuggestionEngine
import com.ai.banphim.ime.ui.ImeBodyPanelHost
import com.ai.banphim.ime.ui.ImeHeaderHost
import com.ai.banphim.ime.ui.ImePanelMode
import com.ai.banphim.ime.ui.ImeUiState
import com.ai.banphim.ime.view.KeyboardCanvasView
import com.ai.banphim.ime.view.KeyboardLayoutSpec.KeyAction
import com.ai.banphim.ime.view.KeyboardLayoutSpec.KeyboardPage
import com.ai.banphim.ime.view.KeyboardLayoutSpec.ShiftState
import com.ai.banphim.ime.view.KeyboardThemePalette
import com.ai.banphim.ui.ProvideAppLocale
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hybrid InputMethodService (Method B for Lifecycle/SavedState + Compose Panels,
 * Option A for high-speed Java Custom View/Canvas keyboard surface).
 * Supports Gboard-style floating draggable keyboard, responsive landscape sizing,
 * touch-outside-to-hide, panel reset on reopen, and live AI translation.
 */
class BanPhimImeService :
  InputMethodService(),
  LifecycleOwner,
  ViewModelStoreOwner,
  SavedStateRegistryOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val store = ViewModelStore()
  private val savedStateController = SavedStateRegistryController.create(this)
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val viewModelStore: ViewModelStore
    get() = store

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateController.savedStateRegistry

  private lateinit var prefsRepo: KeyboardPreferencesRepository
  private lateinit var clipboardRepo: ClipboardRepository
  private lateinit var personalDictRepo: PersonalDictionaryRepository
  private lateinit var aiManager: AiProviderManager
  private val suggestionEngine = WordSuggestionEngine()

  private var settings = KeyboardSettings()
  private val uiState = MutableStateFlow(ImeUiState())

  private var rootFrame: FrameLayout? = null
  private var keyboardCardContainer: FrameLayout? = null
  private var resizeHandleView: ResizeHandleView? = null
  private var floatingHandleBar: LinearLayout? = null
  private var floatingTitleView: TextView? = null
  private var floatingDragHandleChip: View? = null
  private var floatingDragIcon: HandleGlyphView? = null
  private var floatingDockIcon: HandleGlyphView? = null
  private var floatingHideIcon: HandleGlyphView? = null

  private var keyboardCanvasView: KeyboardCanvasView? = null
  private var headerComposeView: ComposeView? = null
  private var bodyComposeView: ComposeView? = null

  private var currentPage = KeyboardPage.QWERTY
  private var shiftState = ShiftState.OFF
  private var currentImeOptions = EditorInfo.IME_ACTION_NONE
  private var currentInputType = InputType.TYPE_CLASS_TEXT

  // Active composing state
  private var activeWord = ""
  private var activeRawHistory = ""
  private var pinyinRawBuffer = ""
  private var romajiBuffer = ""
  private var translateHangulComposing = ""
  private var previousCommittedWord = ""
  private var hasTypedWordInCurrentField = false
  private var lastAutoCorrectedFrom: String? = null
  private var lastAutoCorrectedTo: String? = null

  // Translation buffer & job cancellation
  private var translateRawHistory = ""
  private var debounceTranslationJob: Job? = null
  private var activeTranslationJob: Job? = null
  private var hasLiveComposingInHost = false

  // Outside-touch hide & floating drag state
  private var pendingOutsideHideJob: Job? = null
  private var openDropdownCount = 0
  private var suppressOutsideHideUntilMs = 0L
  private var isDraggingFloating = false
  private var lastAppliedFloatingMode: Boolean? = null

  // Cached drawables for floating container styling to avoid allocations on every keypress
  private val floatingCardDrawable by lazy {
    GradientDrawable().apply { cornerRadius = dp(18f) }
  }
  private val floatingGripDrawable by lazy {
    GradientDrawable().apply { cornerRadius = dp(14f) }
  }
  private var lastFloatingPaletteKey: Int = 0

  private var clipboardManager: ClipboardManager? = null
  private var audioManager: AudioManager? = null
  private var cachedVibrator: Vibrator? = null
  private var cachedSpaceOffLangCode: String? = null
  private var cachedSpaceOffLabel: String = ""

  private val clipListener =
    ClipboardManager.OnPrimaryClipChangedListener {
      syncCurrentSystemClipboard()
    }

  private fun moveLifecycleTo(targetState: Lifecycle.State) {
    val current = lifecycleRegistry.currentState
    if (current == Lifecycle.State.DESTROYED || current == targetState) return
    runCatching {
      lifecycleRegistry.currentState = targetState
    }
  }

  override fun onCreate() {
    super.onCreate()
    savedStateController.performRestore(null)
    moveLifecycleTo(Lifecycle.State.CREATED)

    prefsRepo = KeyboardPreferencesRepository(this)
    clipboardRepo = ClipboardRepository(ClipboardDatabase.getInstance(this).clipboardDao())
    personalDictRepo =
      PersonalDictionaryRepository(PersonalDictionaryDatabase.getInstance(this).personalDictionaryDao())
    aiManager = AiProviderManager(this)

    clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboardManager?.addPrimaryClipChangedListener(clipListener)
    audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    cachedVibrator =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }

    updateOrientationInState()

    serviceScope.launch {
      suggestionEngine.loadBundledAssetsIfNeeded(this@BanPhimImeService)
    }
    serviceScope.launch {
      val snapshot = personalDictRepo.loadSnapshot()
      suggestionEngine.hydrateUserLexicon(snapshot.words, snapshot.bigrams)
    }
    serviceScope.launch {
      personalDictRepo.statsFlow.collect { stats ->
        if (stats.wordCount == 0 && stats.bigramCount == 0) {
          suggestionEngine.clearUserLexicon()
        }
      }
    }

    serviceScope.launch {
      prefsRepo.ensureFirstLaunchDefaultsSaved()
      prefsRepo.settingsFlow.collect { newSettings ->
        val privacyJustAccepted = !settings.aiPrivacyAccepted && newSettings.aiPrivacyAccepted
        val switchedToOffline =
          settings.translationMode != TranslationMode.OFFLINE_MLKIT &&
            newSettings.translationMode == TranslationMode.OFFLINE_MLKIT
        val floatingChanged = settings.floatingMode != newSettings.floatingMode
        settings = newSettings
        uiState.update {
          it.copy(
            activeLanguage = newSettings.activeLanguage,
            enabledLanguages = newSettings.enabledLanguages,
            inputMode = newSettings.inputMethodMode,
            themePreset = newSettings.themePreset,
            appLanguageCode = newSettings.appLanguageCode,
            floatingMode = newSettings.floatingMode,
            aiPrivacyAccepted = newSettings.aiPrivacyAccepted,
            liveTranslateEnabled = newSettings.liveTranslateAsYouType,
            translationMode = newSettings.translationMode,
            sourceLang = SupportedLanguage.findByCode(newSettings.defaultSourceLang),
            targetLang = SupportedLanguage.findByCode(newSettings.defaultTargetLang),
            recentEmojis = newSettings.recentEmojis,
          )
        }
        if ((privacyJustAccepted || switchedToOffline) &&
          uiState.value.panelMode == ImePanelMode.TRANSLATE &&
          newSettings.liveTranslateAsYouType &&
          uiState.value.translateSourceText.trim().length >= 2
        ) {
          scheduleLiveTranslationIfEnabled(afterDelimiter = true)
        }
        if (floatingChanged || lastAppliedFloatingMode == null) {
          applyContainerLayoutMode()
        }
        syncKeyboardCanvasConfig()
      }
    }

    serviceScope.launch {
      clipboardRepo.clipsFlow.collect { clips -> uiState.update { it.copy(clips = clips) } }
    }
  }

  override fun onEvaluateFullscreenMode(): Boolean = false

  override fun onCreateInputView(): View {
    attachViewTreeOwners()
    installOutsideTouchWatcher()
    updateOrientationInState()

    val root =
      FloatingRootFrameLayout(this).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
      }

    val card =
      FrameLayout(this).apply {
        layoutParams =
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
          )
        setOnTouchListener(FloatingKeyboardTouchListener())
      }

    val cardContent =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams =
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
          )
      }

    val resizeHandle = createResizeHandle()
    resizeHandleView = resizeHandle

    val header =
      ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val state by uiState.collectAsState()
          val palette = KeyboardThemePalette.resolve(this@BanPhimImeService, state.themePreset)
          ProvideAppLocale(localeTag = state.appLanguageCode) {
            MaterialTheme(
              colorScheme =
                if (palette.isDark) darkColorScheme(primary = Color(palette.accent))
                else lightColorScheme(primary = Color(palette.accent))
            ) {
              ImeHeaderHost(
              state = state,
              palette = palette,
              onTogglePanel = ::togglePanel,
              onCycleInputMode = ::cycleInputMode,
              onCycleLanguage = { cycleEnabledLanguage(1) },
              onToggleFloatingMode = ::toggleFloatingMode,
              onHideKeyboard = { requestHideSelf(0) },
              onOpenSettings = ::openSettingsActivity,
              onToggleToolbarMode = { showSuggestions ->
                uiState.update { it.copy(showSuggestionsStrip = showSuggestions) }
              },
              onSuggestionClick = ::applySuggestion,
              onForgetSuggestion = ::forgetSuggestion,
              onPinyinCandidateClick = ::applyPinyinCandidate,
              onSourceLangSelected = { lang ->
                uiState.update { it.copy(sourceLang = lang, translationResult = null) }
                serviceScope.launch { prefsRepo.setLanguages(lang.code, uiState.value.targetLang.code) }
                scheduleLiveTranslationIfEnabled(afterDelimiter = true)
              },
              onTargetLangSelected = { lang ->
                uiState.update { it.copy(targetLang = lang, translationResult = null) }
                serviceScope.launch { prefsRepo.setLanguages(uiState.value.sourceLang.code, lang.code) }
                scheduleLiveTranslationIfEnabled(afterDelimiter = true)
              },
              onSwapLanguages = ::swapTranslationLanguages,
              onTranslateNow = ::triggerTranslation,
              onClearTranslateSource = {
                cancelActiveTranslation()
                clearLiveHostComposing()
                translateRawHistory = ""
                translateHangulComposing = ""
                pinyinRawBuffer = ""
                romajiBuffer = ""
                uiState.update {
                  it.copy(
                    translateSourceText = "",
                    translateCursorIndex = 0,
                    translationResult = null,
                    isLiveSyncedToHost = false,
                    pinyinCandidates = emptyList(),
                    candidateComposingDisplay = "",
                  )
                }
              },
              onPullFromEditor = ::pullTextFromHostEditor,
              onPasteToTranslate = ::pasteClipboardToTranslate,
              onInsertTranslation = ::insertTranslatedTextToHost,
              onCopyTranslation = ::copyToClipboard,
              onAcceptAiPrivacy = {
                serviceScope.launch { prefsRepo.setAiPrivacyAccepted(true) }
              },
              onToggleLiveTranslate = ::toggleLiveTranslate,
              onCycleTranslationMode = ::cycleTranslationMode,
              onTranslateCursorChanged = ::updateTranslateCursorIndex,
              onDropdownOpenChanged = { isOpen ->
                if (isOpen) {
                  openDropdownCount++
                  suppressOutsideHideUntilMs = System.currentTimeMillis() + 8000L
                } else {
                  openDropdownCount = (openDropdownCount - 1).coerceAtLeast(0)
                  suppressOutsideHideUntilMs = System.currentTimeMillis() + 1500L
                }
              },
            )
            }
          }
        }
      }

    val canvas =
      KeyboardCanvasView(this).apply {
        setOnKeyboardActionListener(keyboardListener)
      }

    val bodyOverlay =
      ComposeView(this).apply {
        visibility = View.GONE
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          val state by uiState.collectAsState()
          val palette = KeyboardThemePalette.resolve(this@BanPhimImeService, state.themePreset)
          val panelHeightDp = computeResponsivePanelHeightDp(state.isLandscape, state.floatingMode)
          ProvideAppLocale(localeTag = state.appLanguageCode) {
            MaterialTheme(
              colorScheme =
                if (palette.isDark) darkColorScheme(primary = Color(palette.accent))
                else lightColorScheme(primary = Color(palette.accent))
            ) {
              ImeBodyPanelHost(
              state = state,
              palette = palette,
              panelHeightDp = panelHeightDp,
              onClosePanel = { togglePanel(ImePanelMode.NONE) },
              onEmojiPicked = ::commitEmoji,
              onBackspaceClick = { handleBackspace(wordDelete = false) },
              onInsertClip = ::insertClipText,
              onTogglePinClip = { id ->
                if (settings.hapticFeedbackEnabled) performHapticFeedback(KeyAction.SPACE)
                serviceScope.launch { clipboardRepo.togglePin(id) }
              },
              onDeleteClip = { id -> serviceScope.launch { clipboardRepo.deleteById(id) } },
              onClearUnpinnedClips = { serviceScope.launch { clipboardRepo.clearUnpinned() } },
              onSelectThemePreset = { preset ->
                serviceScope.launch { prefsRepo.setThemePreset(preset) }
              },
              onSelectKeyboardLanguage = { lang ->
                switchToLanguage(lang)
                togglePanel(ImePanelMode.NONE)
              },
              onToggleLanguageEnabled = { lang ->
                serviceScope.launch { prefsRepo.toggleLanguageEnabled(lang) }
              },
              onShowSystemImePicker = {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
              },
            )
            }
          }
        }
      }

    val handleBar = buildFloatingHandleBar()

    rootFrame = root
    keyboardCardContainer = card
    headerComposeView = header
    keyboardCanvasView = canvas
    bodyComposeView = bodyOverlay
    floatingHandleBar = handleBar

    cardContent.addView(handleBar)
    cardContent.addView(header)
    cardContent.addView(canvas)
    cardContent.addView(bodyOverlay)

    card.addView(cardContent)
    card.addView(resizeHandle)
    root.addView(card)

    applyContainerLayoutMode()
    syncKeyboardCanvasConfig()
    return root
  }

  private fun computeResponsivePanelHeightDp(isLandscape: Boolean, floating: Boolean): Int {
    val base =
      when {
        isLandscape && floating -> 142
        isLandscape -> 152
        floating -> 182
        else -> 228
      }
    return (base * settings.keyboardHeightScale).toInt().coerceIn(132, 275)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun buildFloatingHandleBar(): LinearLayout {
    val bar =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpInt(38f),
          )
        setPadding(dpInt(8f), 0, dpInt(8f), 0)
      }

    val localizedCtx = AppLocaleManager.createLocalizedContext(this, settings.appLanguageCode)
    val dockBtn =
      HandleGlyphView(this, HandleGlyphView.GlyphType.DOCK).apply {
        contentDescription = localizedCtx.getString(R.string.ime_floating_dock_cd)
        layoutParams = LinearLayout.LayoutParams(dpInt(38f), dpInt(32f))
        setOnClickListener {
          serviceScope.launch { prefsRepo.setFloatingMode(false) }
        }
      }

    val titleView =
      TextView(this).apply {
        text = localizedCtx.getString(R.string.app_name)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams =
          LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dpInt(6f)
          }
      }

    val dragHandleChip =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        contentDescription = localizedCtx.getString(R.string.ime_floating_drag_hint)
        layoutParams =
          LinearLayout.LayoutParams(dpInt(58f), dpInt(30f)).apply {
            marginEnd = dpInt(6f)
          }
      }

    val dragGlyph =
      HandleGlyphView(this, HandleGlyphView.GlyphType.DRAG).apply {
        layoutParams = LinearLayout.LayoutParams(dpInt(36f), dpInt(24f))
      }
    dragHandleChip.addView(dragGlyph)

    val dragTouchListener = FloatingKeyboardTouchListener()

    dragHandleChip.setOnTouchListener(dragTouchListener)
    dragGlyph.setOnTouchListener(dragTouchListener)
    titleView.setOnTouchListener(dragTouchListener)
    bar.setOnTouchListener(dragTouchListener)

    val hideBtn =
      HandleGlyphView(this, HandleGlyphView.GlyphType.HIDE).apply {
        contentDescription = localizedCtx.getString(R.string.ime_floating_hide_cd)
        layoutParams = LinearLayout.LayoutParams(dpInt(38f), dpInt(32f))
        setOnClickListener { requestHideSelf(0) }
      }

    floatingDockIcon = dockBtn
    floatingTitleView = titleView
    floatingDragHandleChip = dragHandleChip
    floatingDragIcon = dragGlyph
    floatingHideIcon = hideBtn

    bar.addView(dockBtn)
    bar.addView(titleView)
    bar.addView(dragHandleChip)
    bar.addView(hideBtn)
    return bar
  }

  private data class TravelBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

  private fun computeFloatingTravelBounds(root: View, card: View): TravelBounds {
    val dm = resources.displayMetrics
    val containerW = if (root.width > 0) root.width else dm.widthPixels
    val containerH = if (root.height > 0) root.height else dm.heightPixels
    val cardW = if (card.width > 0) card.width else dpInt(320f)
    val cardH = if (card.height > 0) card.height else dpInt(240f)

    val marginX = dp(4f)
    val minX = marginX
    val maxX = (containerW - cardW - marginX).coerceAtLeast(minX)

    val minY = dp(4f)
    val bottomSafe = dp(4f)
    val maxY = (containerH - cardH - bottomSafe).coerceAtLeast(minY)
    return TravelBounds(minX, maxX, minY, maxY)
  }

  private fun applyFloatingCardPosition() {
    val root = rootFrame ?: return
    val card = keyboardCardContainer ?: return
    if (!settings.floatingMode) {
      return
    }
    val bounds = computeFloatingTravelBounds(root, card)
    val targetX = bounds.minX + (bounds.maxX - bounds.minX) * settings.floatingXFraction
    val targetY = bounds.minY + (bounds.maxY - bounds.minY) * settings.floatingYFraction
    card.translationX = targetX.coerceIn(bounds.minX, bounds.maxX)
    card.translationY = targetY.coerceIn(bounds.minY, bounds.maxY)
    root.requestLayout()
  }

  private fun applyContainerLayoutMode() {
    val root = rootFrame ?: return
    val card = keyboardCardContainer ?: return
    val handle = floatingHandleBar ?: return
    val floating = settings.floatingMode
    lastAppliedFloatingMode = floating

    val palette = KeyboardThemePalette.resolve(this, settings)
    val dm = resources.displayMetrics
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (floating) {
      window?.window?.let { win ->
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        win.setGravity(Gravity.TOP or Gravity.START)
        val params = win.attributes
        params.x = 0
        params.y = 0
        win.attributes = params
      }
      (root.parent as? ViewGroup)?.let { inputFrame ->
        inputFrame.layoutParams =
          (inputFrame.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
          }
      }
      root.layoutParams =
        (root.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)).apply {
          width = ViewGroup.LayoutParams.MATCH_PARENT
          height = ViewGroup.LayoutParams.MATCH_PARENT
        }

      val floatingWidthPx =
        if (isLandscape) {
          min(dpInt(376f), (dm.widthPixels * 0.56f).toInt()).coerceAtLeast(dpInt(276f))
        } else {
          min(dpInt(346f), (dm.widthPixels * 0.88f).toInt()).coerceAtLeast(dpInt(260f))
        }
      card.layoutParams =
        FrameLayout.LayoutParams(floatingWidthPx, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
          gravity = Gravity.TOP or Gravity.START
        }

      resizeHandleView?.visibility = View.VISIBLE
      updateFloatingCardChrome(palette, force = true)
      root.post { applyFloatingCardPosition() }
    } else {
      window?.window?.let { win ->
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        win.setGravity(Gravity.BOTTOM)
        val params = win.attributes
        params.x = 0
        params.y = 0
        win.attributes = params
      }
      (root.parent as? ViewGroup)?.let { inputFrame ->
        inputFrame.layoutParams =
          (inputFrame.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
          }
      }
      root.layoutParams =
        (root.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
          width = ViewGroup.LayoutParams.MATCH_PARENT
          height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
      card.layoutParams =
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
          gravity = Gravity.BOTTOM
        }
      card.translationX = 0f
      card.translationY = 0f
      card.elevation = 0f
      card.clipToOutline = false
      card.background = null
      handle.visibility = View.GONE
      resizeHandleView?.visibility = View.GONE
      lastFloatingPaletteKey = 0
      root.requestLayout()
    }
  }

  /** Updates cached floating container drawables only when the resolved palette changes. */
  private fun updateFloatingCardChrome(palette: KeyboardThemePalette, force: Boolean = false) {
    val key = palette.keyboardBackground * 31 + palette.keyDivider * 17 + palette.keyHintText
    if (!force && lastFloatingPaletteKey == key) return
    lastFloatingPaletteKey = key

    val card = keyboardCardContainer ?: return
    val handle = floatingHandleBar ?: return

    floatingCardDrawable.setColor(palette.keyboardBackground)
    floatingCardDrawable.setStroke(dpInt(1f), palette.keyDivider)
    card.background = floatingCardDrawable
    card.elevation = dp(10f)
    card.clipToOutline = true

    handle.visibility = View.VISIBLE
    handle.setBackgroundColor(palette.toolbarBackground)

    floatingGripDrawable.setColor(palette.keyBackground)
    floatingGripDrawable.setStroke(dpInt(1f), palette.keyDivider)
    floatingDragHandleChip?.background = floatingGripDrawable
    floatingTitleView?.setTextColor(palette.keyHintText)
    floatingDragIcon?.setTint(palette.accent)
    floatingDockIcon?.setTint(palette.keyHintText)
    floatingHideIcon?.setTint(palette.keyHintText)
    resizeHandleView?.setTint(palette.keyHintText)
  }

  override fun onComputeInsets(outInsets: Insets) {
    val root = rootFrame
    val card = keyboardCardContainer
    val dm = resources.displayMetrics
    val containerH = if (root != null && root.height > 0) root.height else dm.heightPixels
    val containerW = if (root != null && root.width > 0) root.width else dm.widthPixels

    if (settings.floatingMode && card != null && card.width > 0 && card.height > 0) {
      outInsets.contentTopInsets = containerH
      outInsets.visibleTopInsets = containerH
      outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
      val left = card.translationX.toInt().coerceAtLeast(0)
      val top = card.translationY.toInt().coerceAtLeast(0)
      val right = (left + card.width).coerceAtMost(containerW)
      val bottom = (top + card.height).coerceAtMost(containerH)
      outInsets.touchableRegion.set(left, top, right, bottom)
      return
    }
    super.onComputeInsets(outInsets)
  }

  private fun attachViewTreeOwners() {
    window?.window?.decorView?.let { decor ->
      decor.setViewTreeLifecycleOwner(this)
      decor.setViewTreeViewModelStoreOwner(this)
      decor.setViewTreeSavedStateRegistryOwner(this)
    }
  }

  private fun installOutsideTouchWatcher() {
    val win = window?.window ?: return
    // Temporarily disabled outside-touch auto-hide per user request
    win.clearFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
  }

  private fun handleOutsideTouch() {
    // Temporarily disabled per user request
    return
  }

  private fun cancelPendingOutsideHide() {
    pendingOutsideHideJob?.cancel()
    pendingOutsideHideJob = null
  }

  private inner class OutsideTouchWindowCallback(
    private val delegate: Window.Callback,
  ) : Window.Callback {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = delegate.dispatchKeyEvent(event)
    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean = delegate.dispatchKeyShortcutEvent(event)
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
      // Temporarily disabled outside-touch auto-hide per user request
      return delegate.dispatchTouchEvent(event)
    }
    override fun dispatchTrackballEvent(event: MotionEvent): Boolean = delegate.dispatchTrackballEvent(event)
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = delegate.dispatchGenericMotionEvent(event)
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean = delegate.dispatchPopulateAccessibilityEvent(event)
    override fun onCreatePanelView(featureId: Int): View? = delegate.onCreatePanelView(featureId)
    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean = delegate.onCreatePanelMenu(featureId, menu)
    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean = delegate.onPreparePanel(featureId, view, menu)
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean = delegate.onMenuOpened(featureId, menu)
    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = delegate.onMenuItemSelected(featureId, item)
    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams) = delegate.onWindowAttributesChanged(attrs)
    override fun onContentChanged() = delegate.onContentChanged()
    override fun onWindowFocusChanged(hasFocus: Boolean) = delegate.onWindowFocusChanged(hasFocus)
    override fun onAttachedToWindow() = delegate.onAttachedToWindow()
    override fun onDetachedFromWindow() = delegate.onDetachedFromWindow()
    override fun onPanelClosed(featureId: Int, menu: Menu) = delegate.onPanelClosed(featureId, menu)
    override fun onSearchRequested(): Boolean = delegate.onSearchRequested()
    override fun onSearchRequested(searchEvent: SearchEvent): Boolean = delegate.onSearchRequested(searchEvent)
    override fun onWindowStartingActionMode(callback: ActionMode.Callback): ActionMode? = delegate.onWindowStartingActionMode(callback)
    override fun onWindowStartingActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = delegate.onWindowStartingActionMode(callback, type)
    override fun onActionModeStarted(mode: ActionMode) = delegate.onActionModeStarted(mode)
    override fun onActionModeFinished(mode: ActionMode) = delegate.onActionModeFinished(mode)
  }

  override fun onWindowShown() {
    super.onWindowShown()
    installOutsideTouchWatcher()
    updateOrientationInState()
    if (settings.floatingMode) {
      rootFrame?.post { applyFloatingCardPosition() }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateOrientationInState()
    applyContainerLayoutMode()
    syncKeyboardCanvasConfig()
    if (settings.floatingMode) {
      rootFrame?.post { applyFloatingCardPosition() }
    }
  }

  private fun updateOrientationInState() {
    val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (uiState.value.isLandscape != landscape) {
      uiState.update { it.copy(isLandscape = landscape) }
    }
  }

  override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    super.onStartInput(attribute, restarting)
    cancelPendingOutsideHide()
  }

  override fun onViewClicked(focusChanged: Boolean) {
    super.onViewClicked(focusChanged)
    cancelPendingOutsideHide()
  }

  override fun onUpdateSelection(
    oldSelStart: Int,
    oldSelEnd: Int,
    newSelStart: Int,
    newSelEnd: Int,
    candidatesStart: Int,
    candidatesEnd: Int,
  ) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    cancelPendingOutsideHide()
  }

  override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    cancelPendingOutsideHide()
    attachViewTreeOwners()
    installOutsideTouchWatcher()
    updateOrientationInState()
    moveLifecycleTo(Lifecycle.State.RESUMED)

    if (!restarting) {
      resetPanelToKeyboard()
      previousCommittedWord = ""
      hasTypedWordInCurrentField = false
    }

    currentImeOptions = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE
    currentInputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT

    val inputClass = currentInputType and InputType.TYPE_MASK_CLASS
    currentPage =
      if (inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE) {
        KeyboardPage.NUMERIC
      } else {
        KeyboardPage.QWERTY
      }

    hasLiveComposingInHost = false
    uiState.update { it.copy(isLiveSyncedToHost = false) }
    syncCurrentSystemClipboard()
    resetActiveWordBuffer()
    updateAutoCapFromCursor()
    applyContainerLayoutMode()
    syncKeyboardCanvasConfig()
  }

  override fun onFinishInputView(finishingInput: Boolean) {
    super.onFinishInputView(finishingInput)
    cancelPendingOutsideHide()
    if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      moveLifecycleTo(Lifecycle.State.STARTED)
    }
    commitLiveHostComposingIfNeeded()
    cancelActiveTranslation()
    resetPanelToKeyboard()
    resetActiveWordBuffer()
  }

  override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
    super.onCurrentInputMethodSubtypeChanged(newSubtype)
    if (isInputViewShown && newSubtype != null) {
      val localeStr = newSubtype.locale
      if (!localeStr.isNullOrBlank()) {
        val mapped = KeyboardLanguage.fromSubtypeLocale(localeStr)
        if (mapped != settings.activeLanguage) {
          switchToLanguage(mapped)
        }
      }
    }
  }

  override fun onWindowHidden() {
    super.onWindowHidden()
    cancelPendingOutsideHide()
    resetPanelToKeyboard()
    if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      moveLifecycleTo(Lifecycle.State.CREATED)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    cancelPendingOutsideHide()
    clipboardManager?.removePrimaryClipChangedListener(clipListener)
    cancelActiveTranslation()
    serviceScope.cancel()
    moveLifecycleTo(Lifecycle.State.DESTROYED)
    store.clear()
  }

  private fun resetPanelToKeyboard() {
    openDropdownCount = 0
    if (uiState.value.panelMode != ImePanelMode.NONE) {
      commitLiveHostComposingIfNeeded()
      cancelActiveTranslation()
      uiState.update { it.copy(panelMode = ImePanelMode.NONE) }
    }
    keyboardCanvasView?.visibility = View.VISIBLE
    bodyComposeView?.visibility = View.GONE
    if (settings.floatingMode) {
      rootFrame?.post { applyFloatingCardPosition() }
    }
  }

  private fun toggleFloatingMode() {
    serviceScope.launch {
      prefsRepo.setFloatingMode(!settings.floatingMode)
    }
  }

  private val keyboardListener =
    object : KeyboardCanvasView.OnKeyboardActionListener {
      override fun onCharacter(text: String) {
        handleCharacterInput(text)
      }

      override fun onBackspace(wordDelete: Boolean) {
        handleBackspace(wordDelete)
      }

      override fun onEnter() {
        handleEnter()
      }

      override fun onSpace() {
        handleSpace()
      }

      override fun onSpaceLongPress() {
        togglePanel(ImePanelMode.LANGUAGE)
      }

      override fun onSwipeSpaceLanguage(direction: Int) {
        cycleEnabledLanguage(direction)
      }

      override fun onShiftToggle(doubleTap: Boolean) {
        shiftState =
          when {
            doubleTap -> ShiftState.CAPS_LOCK
            shiftState == ShiftState.OFF -> ShiftState.SINGLE
            else -> ShiftState.OFF
          }
        syncKeyboardCanvasConfig()
      }

      override fun onOpenEmoji() {
        togglePanel(ImePanelMode.EMOJI)
      }

      override fun onPageChanged(newPage: KeyboardPage) {
        currentPage = newPage
        syncKeyboardCanvasConfig()
      }

      override fun onKeyFeedback(action: KeyAction) {
        if (settings.hapticFeedbackEnabled) {
          performHapticFeedback(action)
        }
        if (settings.soundFeedbackEnabled) {
          audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.35f)
        }
      }
    }

  private fun handleCharacterInput(text: String) {
    if (text.isEmpty()) return
    val lang = settings.activeLanguage
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    val password = isPasswordField()

    // 1. Chinese Pinyin mode
    if (lang == KeyboardLanguage.CHINESE_PINYIN && !password) {
      val ch = text[0]
      if ((ch in 'a'..'z' || ch == '\'') && text.length == 1) {
        if (inTranslate) cancelActiveTranslation()
        pinyinRawBuffer += ch
        updatePinyinCandidates(commitToHostComposing = !inTranslate)
        resetSingleShift()
        return
      } else if (pinyinRawBuffer.isNotEmpty()) {
        val top = uiState.value.pinyinCandidates.firstOrNull()?.text ?: pinyinRawBuffer
        commitResolvedPinyinText(top, clearAll = true, inTranslate = inTranslate)
      }
    }

    // 2. Japanese Romaji mode
    if (lang == KeyboardLanguage.JAPANESE_ROMAJI && !password) {
      val ch = text[0]
      if ((ch in 'a'..'z' || ch == '-' || ch == '\'') && text.length == 1) {
        if (inTranslate) cancelActiveTranslation()
        romajiBuffer += ch
        val hira = JapaneseRomajiEngine.toHiragana(romajiBuffer)
        val cands = JapaneseRomajiEngine.getCandidates(romajiBuffer)
        if (!inTranslate) {
          activeWord = hira
          currentInputConnection?.setComposingText(hira, 1)
        }
        uiState.update {
          it.copy(
            showSuggestionsStrip = true,
            suggestions = cands.take(3),
            candidateComposingDisplay = romajiBuffer,
            autoCorrectPreview = null,
          )
        }
        resetSingleShift()
        return
      } else if (romajiBuffer.isNotEmpty()) {
        val hira = JapaneseRomajiEngine.toHiragana(romajiBuffer)
        commitResolvedJapaneseText(hira, inTranslate = inTranslate)
      }
    }

    // 3. Korean 2-Beolsik Hangul mode
    if (lang == KeyboardLanguage.KOREAN_HANGUL && !password && KoreanHangulEngine.isHangulJamo(text)) {
      if (inTranslate) {
        cancelActiveTranslation()
        val currentSource = uiState.value.translateSourceText
        val safeCursor = TranslateTextBuffer.clampCursor(currentSource, uiState.value.translateCursorIndex)
        val beforeCursor = currentSource.substring(0, safeCursor)
        val afterCursor = currentSource.substring(safeCursor)
        val prefix =
          if (translateHangulComposing.isNotEmpty() && beforeCursor.endsWith(translateHangulComposing)) {
            beforeCursor.dropLast(translateHangulComposing.length)
          } else {
            beforeCursor
          }
        val step = KoreanHangulEngine.inputJamo(translateHangulComposing, text)
        translateHangulComposing = step.composing
        val newBefore = prefix + step.committed + step.composing
        uiState.update {
          it.copy(
            translateSourceText = newBefore + afterCursor,
            translateCursorIndex = newBefore.length,
            translationResult = null,
          )
        }
        resetSingleShift()
        scheduleLiveTranslationIfEnabled(afterDelimiter = false)
        return
      } else {
        val ic = currentInputConnection ?: return
        val step = KoreanHangulEngine.inputJamo(activeWord, text)
        if (step.committed.isNotEmpty()) {
          ic.commitText(step.committed, 1)
        }
        activeWord = step.composing
        ic.setComposingText(activeWord, 1)
        resetSingleShift()
        return
      }
    }

    // 4. Direct typing inside AI Translation Bar
    if (inTranslate) {
      cancelActiveTranslation()
      val ch = text[0]
      val currentSource = uiState.value.translateSourceText
      val safeCursor = TranslateTextBuffer.clampCursor(currentSource, uiState.value.translateCursorIndex)
      translateHangulComposing = ""
      val useVietEngine = lang == KeyboardLanguage.VIETNAMESE && !password
      if (!useVietEngine || !VietnameseImeEngine.isWordChar(ch)) {
        translateRawHistory = ""
        val edited = TranslateTextBuffer.insertAtCursor(currentSource, safeCursor, text)
        uiState.update {
          it.copy(
            translateSourceText = edited.text,
            translateCursorIndex = edited.cursorIndex,
            translationResult = null,
          )
        }
      } else {
        val beforeCursor = currentSource.substring(0, safeCursor)
        val afterCursor = currentSource.substring(safeCursor)
        val lastTokenStart = findLastTokenStart(beforeCursor)
        val prefix = beforeCursor.substring(0, lastTokenStart)
        val token = beforeCursor.substring(lastTokenStart)
        if (token.isEmpty()) {
          translateRawHistory = ""
        } else if (translateRawHistory.isEmpty()) {
          translateRawHistory = token
        }
        val res =
          VietnameseImeEngine.processKey(
            token,
            translateRawHistory,
            ch,
            settings.inputMethodMode,
            settings.tonePlacementStyle,
          )
        translateRawHistory = res.rawText
        val newBefore = prefix + res.text
        uiState.update {
          it.copy(
            translateSourceText = newBefore + afterCursor,
            translateCursorIndex = newBefore.length,
            translationResult = null,
          )
        }
      }
      resetSingleShift()
      val isDelimiter =
        ch == '.' || ch == ',' || ch == '!' || ch == '?' || ch == ';' || ch == ':' || ch == '。' || ch == '，'
      scheduleLiveTranslationIfEnabled(afterDelimiter = isDelimiter)
      return
    }

    // 5. Standard host InputConnection typing
    val ic = currentInputConnection ?: return
    lastAutoCorrectedFrom = null
    lastAutoCorrectedTo = null

    val ch = text[0]
    val isWordLetter =
      if (lang == KeyboardLanguage.VIETNAMESE) {
        VietnameseImeEngine.isWordChar(ch)
      } else {
        ch.isLetter()
      }

    if (!isWordLetter) {
      finishComposingAndCommit(text)
      if (text == "." || text == "!" || text == "?") {
        updateAutoCapFromCursor()
      }
      return
    }

    if (lang == KeyboardLanguage.VIETNAMESE && !password) {
      if (activeWord.isEmpty()) {
        val before = ic.getTextBeforeCursor(24, 0)?.toString().orEmpty()
        val tokenStart = findLastTokenStart(before)
        val existingToken = before.substring(tokenStart)
        if (existingToken.isNotEmpty() && existingToken.all { VietnameseImeEngine.isWordChar(it) }) {
          activeWord = existingToken
          activeRawHistory = existingToken
          ic.deleteSurroundingText(existingToken.length, 0)
          ic.setComposingText(activeWord, 1)
        }
      }
      val result =
        VietnameseImeEngine.processKey(
          activeWord,
          activeRawHistory,
          ch,
          settings.inputMethodMode,
          settings.tonePlacementStyle,
        )
      activeWord = result.text
      activeRawHistory = result.rawText
      ic.setComposingText(activeWord, 1)
    } else {
      activeWord += text
      activeRawHistory += text
      ic.setComposingText(activeWord, 1)
    }

    resetSingleShift()
    refreshSuggestions()
  }

  private fun handleSpace() {
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    val lang = settings.activeLanguage

    if (lang == KeyboardLanguage.CHINESE_PINYIN && pinyinRawBuffer.isNotEmpty()) {
      val top = uiState.value.pinyinCandidates.firstOrNull()
      if (top != null) {
        applyPinyinCandidate(top)
      } else {
        commitResolvedPinyinText(pinyinRawBuffer, clearAll = true, inTranslate = inTranslate)
      }
      return
    }

    if (lang == KeyboardLanguage.JAPANESE_ROMAJI && romajiBuffer.isNotEmpty()) {
      val top = uiState.value.suggestions.firstOrNull() ?: JapaneseRomajiEngine.toHiragana(romajiBuffer)
      commitResolvedJapaneseText(top, inTranslate = inTranslate)
      return
    }

    if (inTranslate) {
      cancelActiveTranslation()
      translateRawHistory = ""
      translateHangulComposing = ""
      uiState.update { state ->
        val edited = TranslateTextBuffer.insertAtCursor(state.translateSourceText, state.translateCursorIndex, " ")
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
      return
    }

    val ic = currentInputConnection ?: return
    if (lang == KeyboardLanguage.KOREAN_HANGUL && activeWord.isNotEmpty()) {
      ic.commitText("$activeWord ", 1)
      resetActiveWordBuffer()
      updateAutoCapFromCursor()
      syncKeyboardCanvasConfig()
      return
    }

    val current = activeWord
    if (current.isNotEmpty()) {
      val sug =
        suggestionEngine.suggest(
          currentWord = current,
          previousWord = previousCommittedWord,
          vietnameseEnabled = lang == KeyboardLanguage.VIETNAMESE && settings.inputMethodMode != InputMethodMode.OFF,
          autoCorrectEnabled = settings.autoCorrectionEnabled && !isPasswordField(),
          languageCode = lang.code,
        )
      val autoTarget = sug.autoCorrectTarget
      val committed =
        if (autoTarget != null && autoTarget != current) {
          lastAutoCorrectedFrom = current
          lastAutoCorrectedTo = autoTarget
          autoTarget
        } else {
          current
        }
      ic.commitText("$committed ", 1)
      recordLearnedToken(committed, previousCommittedWord)
      previousCommittedWord = if (WordSuggestionEngine.isSafeToLearn(committed)) committed else ""
      hasTypedWordInCurrentField = true
      activeWord = ""
      activeRawHistory = ""
    } else {
      ic.commitText(" ", 1)
    }

    if (currentPage != KeyboardPage.QWERTY) {
      currentPage = KeyboardPage.QWERTY
    }
    updateAutoCapFromCursor()
    refreshSuggestions()
    syncKeyboardCanvasConfig()
  }

  private fun handleBackspace(wordDelete: Boolean) {
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    val lang = settings.activeLanguage

    if (lang == KeyboardLanguage.CHINESE_PINYIN && pinyinRawBuffer.isNotEmpty()) {
      pinyinRawBuffer = if (wordDelete) "" else pinyinRawBuffer.dropLast(1)
      if (pinyinRawBuffer.isEmpty()) {
        if (!inTranslate) currentInputConnection?.commitText("", 1)
        uiState.update { it.copy(pinyinCandidates = emptyList(), candidateComposingDisplay = "") }
      } else {
        updatePinyinCandidates(commitToHostComposing = !inTranslate)
      }
      return
    }

    if (lang == KeyboardLanguage.JAPANESE_ROMAJI && romajiBuffer.isNotEmpty()) {
      romajiBuffer = if (wordDelete) "" else romajiBuffer.dropLast(1)
      if (romajiBuffer.isEmpty()) {
        activeWord = ""
        if (!inTranslate) currentInputConnection?.commitText("", 1)
        uiState.update {
          it.copy(showSuggestionsStrip = false, suggestions = emptyList(), candidateComposingDisplay = "")
        }
      } else {
        val hira = JapaneseRomajiEngine.toHiragana(romajiBuffer)
        val cands = JapaneseRomajiEngine.getCandidates(romajiBuffer)
        if (!inTranslate) {
          activeWord = hira
          currentInputConnection?.setComposingText(hira, 1)
        }
        uiState.update {
          it.copy(
            showSuggestionsStrip = true,
            suggestions = cands.take(3),
            candidateComposingDisplay = romajiBuffer,
          )
        }
      }
      return
    }

    if (inTranslate) {
      cancelActiveTranslation()
      val cur = uiState.value.translateSourceText
      val safeCursor = TranslateTextBuffer.clampCursor(cur, uiState.value.translateCursorIndex)
      if (cur.isNotEmpty() && safeCursor > 0) {
        val before = cur.substring(0, safeCursor)
        val after = cur.substring(safeCursor)
        if (!wordDelete && lang == KeyboardLanguage.KOREAN_HANGUL && translateHangulComposing.isNotEmpty() && before.endsWith(translateHangulComposing)) {
          val prefix = before.dropLast(translateHangulComposing.length)
          val decomposed = KoreanHangulEngine.backspace(translateHangulComposing)
          translateHangulComposing = decomposed
          val newBefore = prefix + decomposed
          val next = newBefore + after
          uiState.update {
            it.copy(
              translateSourceText = next,
              translateCursorIndex = newBefore.length,
              translationResult = null,
            )
          }
          if (next.trim().isEmpty()) clearLiveHostComposing() else scheduleLiveTranslationIfEnabled(afterDelimiter = false)
          return
        }

        val edited = TranslateTextBuffer.backspaceAtCursor(cur, safeCursor, wordDelete)
        translateRawHistory = ""
        translateHangulComposing = ""
        uiState.update {
          it.copy(
            translateSourceText = edited.text,
            translateCursorIndex = edited.cursorIndex,
            translationResult = null,
          )
        }
        if (edited.text.trim().isEmpty()) {
          clearLiveHostComposing()
        } else {
          scheduleLiveTranslationIfEnabled(afterDelimiter = false)
        }
      }
      return
    }

    val ic = currentInputConnection ?: return

    if (!wordDelete && lang == KeyboardLanguage.KOREAN_HANGUL && activeWord.isNotEmpty()) {
      val decomposed = KoreanHangulEngine.backspace(activeWord)
      activeWord = decomposed
      if (decomposed.isEmpty()) {
        ic.commitText("", 1)
      } else {
        ic.setComposingText(decomposed, 1)
      }
      return
    }

    // Undo recent auto-correction on single Backspace press
    if (!wordDelete && activeWord.isEmpty() && lastAutoCorrectedFrom != null && lastAutoCorrectedTo != null) {
      val expectedSuffix = "${lastAutoCorrectedTo} "
      val before = ic.getTextBeforeCursor(expectedSuffix.length, 0)?.toString()
      if (before == expectedSuffix) {
        ic.deleteSurroundingText(expectedSuffix.length, 0)
        activeWord = lastAutoCorrectedFrom!!
        activeRawHistory = activeWord
        ic.setComposingText(activeWord, 1)
        lastAutoCorrectedFrom = null
        lastAutoCorrectedTo = null
        refreshSuggestions()
        return
      }
    }
    lastAutoCorrectedFrom = null
    lastAutoCorrectedTo = null

    if (wordDelete) {
      if (activeWord.isNotEmpty()) {
        ic.setComposingText("", 1)
        resetActiveWordBuffer()
      } else {
        val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        if (before.isNotEmpty()) {
          var deleteCount = 0
          var i = before.length - 1
          while (i >= 0 && before[i].isWhitespace()) {
            deleteCount++
            i--
          }
          while (i >= 0 && !before[i].isWhitespace()) {
            deleteCount++
            i--
          }
          ic.deleteSurroundingText(deleteCount.coerceAtLeast(1), 0)
        }
      }
      refreshSuggestions()
      return
    }

    if (activeWord.isNotEmpty()) {
      activeWord = activeWord.dropLast(1)
      activeRawHistory = if (activeRawHistory.isNotEmpty()) activeRawHistory.dropLast(1) else ""
      if (activeWord.isEmpty()) {
        ic.commitText("", 1)
      } else {
        ic.setComposingText(activeWord, 1)
      }
      refreshSuggestions()
    } else {
      val selected = ic.getSelectedText(0)
      if (!selected.isNullOrEmpty()) {
        ic.commitText("", 1)
      } else {
        ic.deleteSurroundingText(1, 0)
      }
      updateAutoCapFromCursor()
    }
  }

  private fun handleEnter() {
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE

    if (pinyinRawBuffer.isNotEmpty()) {
      commitResolvedPinyinText(pinyinRawBuffer, clearAll = true, inTranslate = inTranslate)
      return
    }
    if (romajiBuffer.isNotEmpty()) {
      val hira = JapaneseRomajiEngine.toHiragana(romajiBuffer)
      commitResolvedJapaneseText(hira, inTranslate = inTranslate)
      return
    }

    if (inTranslate) {
      cancelActiveTranslation()
      translateRawHistory = ""
      translateHangulComposing = ""
      uiState.update { state ->
        val edited = TranslateTextBuffer.insertAtCursor(state.translateSourceText, state.translateCursorIndex, "\n")
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
      return
    }

    val ic = currentInputConnection ?: return
    if (activeWord.isNotEmpty()) {
      ic.commitText(activeWord, 1)
      recordLearnedToken(activeWord, previousCommittedWord)
      previousCommittedWord = ""
      hasTypedWordInCurrentField = false
      resetActiveWordBuffer()
    }

    val action = currentImeOptions and EditorInfo.IME_MASK_ACTION
    val handled =
      if (action != EditorInfo.IME_ACTION_NONE &&
        action != EditorInfo.IME_ACTION_UNSPECIFIED &&
        (currentInputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0
      ) {
        ic.performEditorAction(action)
      } else {
        false
      }
    if (!handled) {
      ic.commitText("\n", 1)
    }
    updateAutoCapFromCursor()
    refreshSuggestions()
  }

  private fun finishComposingAndCommit(trailingText: String) {
    val ic = currentInputConnection ?: return
    if (activeWord.isNotEmpty()) {
      ic.commitText(activeWord + trailingText, 1)
      recordLearnedToken(activeWord, previousCommittedWord)
      val breaksSentence = trailingText == "." || trailingText == "!" || trailingText == "?" || trailingText == "\n"
      if (breaksSentence) {
        previousCommittedWord = ""
        hasTypedWordInCurrentField = false
      } else {
        previousCommittedWord = if (WordSuggestionEngine.isSafeToLearn(activeWord)) activeWord else ""
        hasTypedWordInCurrentField = true
      }
      resetActiveWordBuffer()
    } else {
      ic.commitText(trailingText, 1)
      if (trailingText == "." || trailingText == "!" || trailingText == "?") {
        previousCommittedWord = ""
        hasTypedWordInCurrentField = false
      }
    }
    refreshSuggestions()
  }

  private fun applySuggestion(word: String) {
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    if (settings.activeLanguage == KeyboardLanguage.JAPANESE_ROMAJI && romajiBuffer.isNotEmpty()) {
      commitResolvedJapaneseText(word, inTranslate = inTranslate)
      return
    }

    val ic = currentInputConnection ?: return
    ic.commitText("$word ", 1)
    recordLearnedToken(word, previousCommittedWord)
    previousCommittedWord = if (WordSuggestionEngine.isSafeToLearn(word)) word else ""
    hasTypedWordInCurrentField = true
    resetActiveWordBuffer()
    updateAutoCapFromCursor()
    refreshSuggestions()
  }

  private fun applyPinyinCandidate(candidate: PinyinEngine.Candidate) {
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    val consumed = candidate.consumedRawLength.coerceIn(0, pinyinRawBuffer.length)
    val remaining = if (consumed < pinyinRawBuffer.length) pinyinRawBuffer.substring(consumed) else ""

    if (inTranslate) {
      cancelActiveTranslation()
      uiState.update { state ->
        val edited =
          TranslateTextBuffer.insertAtCursor(
            state.translateSourceText,
            state.translateCursorIndex,
            candidate.text,
          )
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = remaining.isEmpty())
    } else {
      currentInputConnection?.commitText(candidate.text, 1)
    }

    pinyinRawBuffer = remaining
    if (pinyinRawBuffer.isEmpty()) {
      uiState.update { it.copy(pinyinCandidates = emptyList(), candidateComposingDisplay = "") }
    } else {
      updatePinyinCandidates(commitToHostComposing = !inTranslate)
    }
  }

  private fun updatePinyinCandidates(commitToHostComposing: Boolean) {
    val lookup = PinyinEngine.lookup(pinyinRawBuffer)
    if (commitToHostComposing) {
      val display = lookup.segmentedDisplay.ifEmpty { pinyinRawBuffer }
      currentInputConnection?.setComposingText(display, 1)
    }
    uiState.update {
      it.copy(
        pinyinCandidates = lookup.candidates,
        candidateComposingDisplay = lookup.segmentedDisplay.ifEmpty { pinyinRawBuffer },
      )
    }
  }

  private fun commitResolvedPinyinText(text: String, clearAll: Boolean, inTranslate: Boolean) {
    if (inTranslate) {
      cancelActiveTranslation()
      uiState.update { state ->
        val edited = TranslateTextBuffer.insertAtCursor(state.translateSourceText, state.translateCursorIndex, text)
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
    } else {
      currentInputConnection?.commitText(text, 1)
    }
    if (clearAll) {
      pinyinRawBuffer = ""
      uiState.update { it.copy(pinyinCandidates = emptyList(), candidateComposingDisplay = "") }
    }
  }

  private fun commitResolvedJapaneseText(text: String, inTranslate: Boolean) {
    if (inTranslate) {
      cancelActiveTranslation()
      uiState.update { state ->
        val edited = TranslateTextBuffer.insertAtCursor(state.translateSourceText, state.translateCursorIndex, text)
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
    } else {
      currentInputConnection?.commitText(text, 1)
    }
    romajiBuffer = ""
    activeWord = ""
    uiState.update {
      it.copy(
        showSuggestionsStrip = false,
        suggestions = emptyList(),
        candidateComposingDisplay = "",
      )
    }
  }

  private fun resetSingleShift() {
    if (shiftState == ShiftState.SINGLE) {
      shiftState = ShiftState.OFF
      syncKeyboardCanvasConfig()
    }
  }

  private fun refreshSuggestions() {
    if (!settings.showSuggestions || isPasswordField()) {
      uiState.update {
        it.copy(showSuggestionsStrip = false, suggestions = emptyList(), autoCorrectPreview = null)
      }
      return
    }

    val lang = settings.activeLanguage
    val vietEnabled = lang == KeyboardLanguage.VIETNAMESE && settings.inputMethodMode != InputMethodMode.OFF

    if (activeWord.isEmpty()) {
      if (hasTypedWordInCurrentField &&
        previousCommittedWord.isNotEmpty() &&
        !isSensitiveOrIncognitoField() &&
        currentPage == KeyboardPage.QWERTY
      ) {
        val nextWordSug =
          suggestionEngine.suggest(
            currentWord = "",
            previousWord = previousCommittedWord,
            vietnameseEnabled = vietEnabled,
            autoCorrectEnabled = false,
            languageCode = lang.code,
          )
        uiState.update {
          it.copy(
            showSuggestionsStrip = nextWordSug.candidates.isNotEmpty(),
            suggestions = nextWordSug.candidates,
            autoCorrectPreview = null,
          )
        }
      } else {
        uiState.update {
          it.copy(showSuggestionsStrip = false, suggestions = emptyList(), autoCorrectPreview = null)
        }
      }
      return
    }

    val sug =
      suggestionEngine.suggest(
        currentWord = activeWord,
        previousWord = previousCommittedWord,
        vietnameseEnabled = vietEnabled,
        autoCorrectEnabled = settings.autoCorrectionEnabled,
        languageCode = lang.code,
      )
    uiState.update {
      it.copy(
        showSuggestionsStrip = true,
        suggestions = sug.candidates,
        autoCorrectPreview = sug.autoCorrectTarget,
      )
    }
  }

  private fun recordLearnedToken(word: String, prevWord: String) {
    if (!shouldLearnFromInput()) return
    val clean = word.trim()
    if (!WordSuggestionEngine.isSafeToLearn(clean)) return
    val langCode = settings.activeLanguage.code
    val cleanPrev = if (WordSuggestionEngine.isSafeToLearn(prevWord)) prevWord.trim() else ""
    suggestionEngine.learnWord(clean, previousWord = cleanPrev, languageCode = langCode)
    serviceScope.launch {
      personalDictRepo.recordWord(clean, langCode)
      if (cleanPrev.isNotEmpty()) {
        personalDictRepo.recordBigram(cleanPrev, clean, langCode)
      }
    }
  }

  private fun forgetSuggestion(word: String) {
    val langCode = settings.activeLanguage.code
    suggestionEngine.forgetWord(word, langCode)
    serviceScope.launch {
      personalDictRepo.forgetWord(word, langCode)
    }
    refreshSuggestions()
  }

  private fun shouldLearnFromInput(): Boolean {
    if (!settings.personalizedSuggestionsEnabled) return false
    if (uiState.value.panelMode == ImePanelMode.TRANSLATE) return false
    if (isSensitiveOrIncognitoField()) return false
    return true
  }

  private fun isSensitiveOrIncognitoField(): Boolean {
    if (isPasswordField()) return true
    if ((currentImeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true
    val cls = currentInputType and InputType.TYPE_MASK_CLASS
    val variation = currentInputType and InputType.TYPE_MASK_VARIATION
    if (cls == InputType.TYPE_CLASS_NUMBER ||
      cls == InputType.TYPE_CLASS_PHONE ||
      cls == InputType.TYPE_CLASS_DATETIME
    ) {
      return true
    }
    if (cls == InputType.TYPE_CLASS_TEXT &&
      (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
        variation == InputType.TYPE_TEXT_VARIATION_URI)
    ) {
      return true
    }
    return false
  }

  /** Toggles between the standard keyboard view and an overlay panel such as Translate or Clipboard. */
  private fun togglePanel(target: ImePanelMode) {
    val next = if (uiState.value.panelMode == target) ImePanelMode.NONE else target
    if (next != ImePanelMode.TRANSLATE) {
      commitLiveHostComposingIfNeeded()
      cancelActiveTranslation()
    }
    if (next == ImePanelMode.CLIPBOARD) {
      syncCurrentSystemClipboard()
    }
    uiState.update { it.copy(panelMode = next) }

    val showBodyOverlay =
      next == ImePanelMode.EMOJI ||
        next == ImePanelMode.CLIPBOARD ||
        next == ImePanelMode.THEME ||
        next == ImePanelMode.LANGUAGE
    keyboardCanvasView?.visibility = if (showBodyOverlay) View.GONE else View.VISIBLE
    bodyComposeView?.visibility = if (showBodyOverlay) View.VISIBLE else View.GONE

    if (next == ImePanelMode.TRANSLATE && uiState.value.translateSourceText.isEmpty() && activeWord.isNotEmpty()) {
      val ic = currentInputConnection
      if (ic != null) {
        ic.commitText(activeWord, 1)
        val pulled = activeWord
        resetActiveWordBuffer()
        uiState.update {
          it.copy(translateSourceText = pulled, translateCursorIndex = pulled.length)
        }
        scheduleLiveTranslationIfEnabled(afterDelimiter = false)
      }
    }

    syncKeyboardCanvasConfig()

    if (settings.floatingMode) {
      rootFrame?.post { applyFloatingCardPosition() }
    }
  }

  /** Reads the current system clipboard safely (ignoring sensitive clips) and persists it locally. */
  private fun syncCurrentSystemClipboard() {
    val cm = clipboardManager ?: return
    val clip = cm.primaryClip ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val extras = clip.description.extras
      if (extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) {
        return
      }
    }
    if (clip.itemCount > 0) {
      val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
      if (text.isNotBlank()) {
        serviceScope.launch { clipboardRepo.recordClip(text) }
      }
    }
  }

  private fun switchToLanguage(lang: KeyboardLanguage) {
    commitPendingBuffersBeforeSwitch()
    currentPage = KeyboardPage.QWERTY
    shiftState = ShiftState.OFF
    serviceScope.launch { prefsRepo.setActiveLanguage(lang) }
  }

  private fun cycleEnabledLanguage(direction: Int) {
    val list = settings.enabledLanguages
    if (list.size <= 1) {
      togglePanel(ImePanelMode.LANGUAGE)
      return
    }
    val curIdx = list.indexOf(settings.activeLanguage).coerceAtLeast(0)
    val nextIdx = ((curIdx + direction) % list.size + list.size) % list.size
    switchToLanguage(list[nextIdx])
  }

  private fun commitPendingBuffersBeforeSwitch() {
    val inTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    if (pinyinRawBuffer.isNotEmpty()) {
      val top = uiState.value.pinyinCandidates.firstOrNull()?.text ?: pinyinRawBuffer
      commitResolvedPinyinText(top, clearAll = true, inTranslate = inTranslate)
    }
    if (romajiBuffer.isNotEmpty()) {
      commitResolvedJapaneseText(JapaneseRomajiEngine.toHiragana(romajiBuffer), inTranslate = inTranslate)
    }
    if (activeWord.isNotEmpty()) {
      currentInputConnection?.commitText(activeWord, 1)
      resetActiveWordBuffer()
    }
    translateHangulComposing = ""
    translateRawHistory = ""
    uiState.update {
      it.copy(
        showSuggestionsStrip = false,
        suggestions = emptyList(),
        pinyinCandidates = emptyList(),
        candidateComposingDisplay = "",
      )
    }
  }

  private fun cycleInputMode() {
    val nextMode =
      when (settings.inputMethodMode) {
        InputMethodMode.TELEX -> InputMethodMode.VNI
        InputMethodMode.VNI -> InputMethodMode.OFF
        InputMethodMode.OFF -> InputMethodMode.TELEX
      }
    serviceScope.launch { prefsRepo.setInputMethodMode(nextMode) }
  }

  private fun openSettingsActivity() {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    startActivity(intent)
  }

  private fun commitEmoji(emoji: String) {
    if (uiState.value.panelMode == ImePanelMode.TRANSLATE) {
      translateRawHistory = ""
      translateHangulComposing = ""
      uiState.update { state ->
        val edited = TranslateTextBuffer.insertAtCursor(state.translateSourceText, state.translateCursorIndex, emoji)
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
    } else {
      currentInputConnection?.commitText(emoji, 1)
    }
    serviceScope.launch { prefsRepo.pushRecentEmoji(emoji) }
  }

  private fun insertClipText(text: String) {
    val wasInTranslate = uiState.value.panelMode == ImePanelMode.TRANSLATE
    if (wasInTranslate) {
      translateRawHistory = ""
      translateHangulComposing = ""
      uiState.update { state ->
        val edited = TranslateTextBuffer.insertAtCursor(state.translateSourceText, state.translateCursorIndex, text)
        state.copy(
          translateSourceText = edited.text,
          translateCursorIndex = edited.cursorIndex,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
    } else {
      currentInputConnection?.commitText(text, 1)
      togglePanel(ImePanelMode.NONE)
    }
  }

  private fun updateTranslateCursorIndex(offset: Int) {
    translateRawHistory = ""
    translateHangulComposing = ""
    pinyinRawBuffer = ""
    romajiBuffer = ""
    uiState.update { state ->
      state.copy(
        translateCursorIndex = TranslateTextBuffer.clampCursor(state.translateSourceText, offset),
      )
    }
  }

  private fun swapTranslationLanguages() {
    val curSource = uiState.value.sourceLang
    val curTarget = uiState.value.targetLang
    if (curSource.code == "auto") {
      val fallbackSource = if (curTarget.code == "vi") SupportedLanguage.findByCode("en") else SupportedLanguage.findByCode("vi")
      uiState.update { it.copy(sourceLang = curTarget, targetLang = fallbackSource, translationResult = null) }
    } else {
      uiState.update { it.copy(sourceLang = curTarget, targetLang = curSource, translationResult = null) }
    }
    serviceScope.launch {
      prefsRepo.setLanguages(uiState.value.sourceLang.code, uiState.value.targetLang.code)
    }
    scheduleLiveTranslationIfEnabled(afterDelimiter = true)
  }

  private fun pullTextFromHostEditor() {
    val ic = currentInputConnection ?: return
    val selected = ic.getSelectedText(0)?.toString()
    val text =
      if (!selected.isNullOrBlank()) {
        selected
      } else {
        ic.getTextBeforeCursor(300, 0)?.toString()?.trim().orEmpty()
      }
    if (text.isNotBlank()) {
      cancelActiveTranslation()
      translateRawHistory = ""
      translateHangulComposing = ""
      uiState.update {
        it.copy(
          translateSourceText = text,
          translateCursorIndex = text.length,
          translationResult = null,
        )
      }
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
    }
  }

  private fun pasteClipboardToTranslate() {
    val clip = clipboardManager?.primaryClip ?: return
    if (clip.itemCount > 0) {
      val pasted = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
      if (pasted.isNotBlank()) {
        cancelActiveTranslation()
        translateRawHistory = ""
        translateHangulComposing = ""
        uiState.update {
          it.copy(
            translateSourceText = pasted,
            translateCursorIndex = pasted.length,
            translationResult = null,
          )
        }
        scheduleLiveTranslationIfEnabled(afterDelimiter = true)
      }
    }
  }

  private fun toggleLiveTranslate(enabled: Boolean) {
    serviceScope.launch { prefsRepo.setLiveTranslateAsYouType(enabled) }
    uiState.update { it.copy(liveTranslateEnabled = enabled) }
    if (!enabled) {
      commitLiveHostComposingIfNeeded()
      debounceTranslationJob?.cancel()
      debounceTranslationJob = null
    } else if (uiState.value.panelMode == ImePanelMode.TRANSLATE) {
      val existing = uiState.value.translationResult
      if (existing is TranslationResult.Success && uiState.value.translateSourceText.isNotBlank()) {
        currentInputConnection?.setComposingText(existing.translatedText, 1)
        hasLiveComposingInHost = currentInputConnection != null
        uiState.update { it.copy(isLiveSyncedToHost = hasLiveComposingInHost) }
      } else {
        scheduleLiveTranslationIfEnabled(afterDelimiter = true)
      }
    }
  }

  private fun cycleTranslationMode() {
    val nextMode =
      when (uiState.value.translationMode) {
        TranslationMode.AUTO_HYBRID -> TranslationMode.OFFLINE_MLKIT
        TranslationMode.OFFLINE_MLKIT -> TranslationMode.ONLINE_AI
        TranslationMode.ONLINE_AI -> TranslationMode.AUTO_HYBRID
      }
    cancelActiveTranslation()
    uiState.update { it.copy(translationMode = nextMode, translationResult = null) }
    serviceScope.launch { prefsRepo.setTranslationMode(nextMode) }
    if (uiState.value.liveTranslateEnabled &&
      (uiState.value.aiPrivacyAccepted || nextMode == TranslationMode.OFFLINE_MLKIT) &&
      uiState.value.translateSourceText.trim().length >= 2
    ) {
      scheduleLiveTranslationIfEnabled(afterDelimiter = true)
    }
  }

  private fun scheduleLiveTranslationIfEnabled(afterDelimiter: Boolean) {
    debounceTranslationJob?.cancel()
    debounceTranslationJob = null
    cancelActiveTranslation()

    val state = uiState.value
    if (state.panelMode != ImePanelMode.TRANSLATE) return
    val trimmed = state.translateSourceText.trim()
    if (trimmed.isEmpty()) {
      clearLiveHostComposing()
      return
    }
    val hasConsent = state.aiPrivacyAccepted || state.translationMode == TranslationMode.OFFLINE_MLKIT
    if (!state.liveTranslateEnabled || !hasConsent || trimmed.length < 2) {
      return
    }

    val delayMs = if (afterDelimiter) 520L else 850L
    debounceTranslationJob =
      serviceScope.launch {
        delay(delayMs)
        performTranslation(liveSyncToHost = true)
      }
  }

  private fun triggerTranslation() {
    debounceTranslationJob?.cancel()
    debounceTranslationJob = null
    performTranslation(liveSyncToHost = uiState.value.liveTranslateEnabled)
  }

  private fun performTranslation(liveSyncToHost: Boolean) {
    val state = uiState.value
    val sourceText = state.translateSourceText.trim()
    val hasConsent = state.aiPrivacyAccepted || state.translationMode == TranslationMode.OFFLINE_MLKIT
    if (sourceText.isEmpty() || !hasConsent) return

    activeTranslationJob?.cancel()
    activeTranslationJob = null
    val requestSnapshot = sourceText
    val srcLang = state.sourceLang
    val tgtLang = state.targetLang
    val modeSnapshot = state.translationMode

    uiState.update { it.copy(isTranslating = true, translationResult = null) }
    activeTranslationJob =
      serviceScope.launch {
        val result =
          aiManager.translate(
            rawText = requestSnapshot,
            sourceLang = srcLang,
            targetLang = tgtLang,
            preferredModelId = settings.preferredAiModelId,
            autoSwitchOnLimit = settings.autoSwitchAiModel,
            optionalGeminiKey = settings.optionalGeminiApiKey,
            translationMode = modeSnapshot,
          )
        if (uiState.value.translateSourceText.trim() == requestSnapshot &&
          uiState.value.panelMode == ImePanelMode.TRANSLATE &&
          uiState.value.sourceLang == srcLang &&
          uiState.value.targetLang == tgtLang &&
          uiState.value.translationMode == modeSnapshot
        ) {
          if (result is TranslationResult.Success && liveSyncToHost) {
            val ic = currentInputConnection
            if (ic != null) {
              ic.setComposingText(result.translatedText, 1)
              hasLiveComposingInHost = true
            }
            uiState.update {
              it.copy(
                isTranslating = false,
                translationResult = result,
                isLiveSyncedToHost = ic != null,
              )
            }
          } else {
            uiState.update {
              it.copy(
                isTranslating = false,
                translationResult = result,
                isLiveSyncedToHost = false,
              )
            }
          }
        }
      }
  }

  private fun cancelActiveTranslation() {
    debounceTranslationJob?.cancel()
    debounceTranslationJob = null
    activeTranslationJob?.cancel()
    activeTranslationJob = null
    if (uiState.value.isTranslating) {
      uiState.update { it.copy(isTranslating = false) }
    }
  }

  private fun clearLiveHostComposing() {
    if (hasLiveComposingInHost) {
      currentInputConnection?.let { ic ->
        ic.setComposingText("", 1)
        ic.finishComposingText()
      }
      hasLiveComposingInHost = false
    }
    uiState.update { it.copy(isLiveSyncedToHost = false) }
  }

  private fun commitLiveHostComposingIfNeeded() {
    if (hasLiveComposingInHost) {
      currentInputConnection?.finishComposingText()
      hasLiveComposingInHost = false
    }
    uiState.update { it.copy(isLiveSyncedToHost = false) }
  }

  private fun insertTranslatedTextToHost(translated: String) {
    val ic = currentInputConnection ?: return
    if (activeWord.isNotEmpty()) {
      ic.finishComposingText()
      resetActiveWordBuffer()
    }
    if (hasLiveComposingInHost) {
      ic.setComposingText(translated, 1)
      ic.finishComposingText()
      hasLiveComposingInHost = false
    } else {
      ic.commitText(translated, 1)
    }
    translateRawHistory = ""
    translateHangulComposing = ""
    uiState.update {
      it.copy(
        translateSourceText = "",
        translateCursorIndex = 0,
        translationResult = null,
        isLiveSyncedToHost = false,
      )
    }
  }

  private fun copyToClipboard(text: String) {
    clipboardManager?.setPrimaryClip(ClipData.newPlainText("AI Translation", text))
  }

  /** Pushes theme palette, language layout, shift state, and scaling to the Java keyboard canvas. */
  private fun syncKeyboardCanvasConfig() {
    val canvas = keyboardCanvasView ?: return
    val palette = KeyboardThemePalette.resolve(this, settings)
    canvas.setPalette(palette)
    if (settings.activeThemeType == "PHOTO" && settings.activeThemeValue.isNotBlank()) {
      canvas.setPhotoBackground(settings.activeThemeValue, settings.customPhotoOpacity)
    } else {
      canvas.clearPhotoBackground()
    }
    val lang = settings.activeLanguage
    val spaceLabel =
      when (lang) {
        KeyboardLanguage.VIETNAMESE ->
          when (settings.inputMethodMode) {
            InputMethodMode.TELEX -> "Tiếng Việt • Telex"
            InputMethodMode.VNI -> "Tiếng Việt • VNI"
            InputMethodMode.OFF -> {
              val langCode = settings.appLanguageCode
              if (cachedSpaceOffLangCode != langCode || cachedSpaceOffLabel.isEmpty()) {
                cachedSpaceOffLangCode = langCode
                cachedSpaceOffLabel =
                  AppLocaleManager.createLocalizedContext(this, langCode)
                    .getString(R.string.space_vietnamese_off)
              }
              cachedSpaceOffLabel
            }
          }
        else -> lang.nativeName
      }
    val effectiveImeOptions =
      if (uiState.value.panelMode == ImePanelMode.TRANSLATE) {
        EditorInfo.IME_ACTION_NONE
      } else {
        currentImeOptions
      }
    canvas.setKeyboardConfig(
      currentPage,
      lang.layoutType,
      shiftState,
      settings.showNumberRow,
      spaceLabel,
      effectiveImeOptions,
      settings.keyboardHeightScale,
      settings.floatingMode,
    )
    if (settings.floatingMode) {
      updateFloatingCardChrome(palette, force = false)
    }
  }

  private fun updateAutoCapFromCursor() {
    if (shiftState == ShiftState.CAPS_LOCK) return
    val ic = currentInputConnection
    val capsMode =
      if (ic != null) {
        ic.getCursorCapsMode(currentInputType)
      } else {
        0
      }
    shiftState = if (capsMode != 0) ShiftState.SINGLE else ShiftState.OFF
    syncKeyboardCanvasConfig()
  }

  private fun isPasswordField(): Boolean {
    val variation = currentInputType and InputType.TYPE_MASK_VARIATION
    val cls = currentInputType and InputType.TYPE_MASK_CLASS
    return (cls == InputType.TYPE_CLASS_TEXT &&
      (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
      (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
  }

  private fun resetActiveWordBuffer() {
    activeWord = ""
    activeRawHistory = ""
    pinyinRawBuffer = ""
    romajiBuffer = ""
    translateHangulComposing = ""
    uiState.update {
      it.copy(
        pinyinCandidates = emptyList(),
        candidateComposingDisplay = "",
        suggestions = emptyList(),
        showSuggestionsStrip = false,
      )
    }
  }

  private fun findLastTokenStart(text: String): Int {
    var i = text.length
    while (i > 0 && VietnameseImeEngine.isWordChar(text[i - 1])) {
      i--
    }
    return i
  }

  private fun performHapticFeedback(action: KeyAction) {
    val vib = cachedVibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val effect =
        when (action) {
          KeyAction.ENTER -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
          KeyAction.SHIFT,
          KeyAction.BACKSPACE,
          KeyAction.SPACE,
          KeyAction.MODE_SYMBOLS,
          KeyAction.MODE_SYMBOLS_MORE,
          KeyAction.MODE_ALPHA -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
          else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
      vib.vibrate(effect)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val (duration, amplitude) =
        when (action) {
          KeyAction.ENTER -> Pair(22L, 210)
          KeyAction.SHIFT,
          KeyAction.BACKSPACE,
          KeyAction.SPACE,
          KeyAction.MODE_SYMBOLS,
          KeyAction.MODE_SYMBOLS_MORE,
          KeyAction.MODE_ALPHA -> Pair(15L, 150)
          else -> Pair(9L, 85)
        }
      vib.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    } else {
      @Suppress("DEPRECATION")
      val duration =
        when (action) {
          KeyAction.ENTER -> 22L
          KeyAction.SHIFT,
          KeyAction.BACKSPACE,
          KeyAction.SPACE,
          KeyAction.MODE_SYMBOLS,
          KeyAction.MODE_SYMBOLS_MORE,
          KeyAction.MODE_ALPHA -> 15L
          else -> 9L
        }
      @Suppress("DEPRECATION")
      vib.vibrate(duration)
    }
  }

  private fun dp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

  private fun dpInt(v: Float): Int = dp(v).toInt().coerceAtLeast(1)

  /** Lightweight crisp vector glyphs for the Gboard-style floating handle bar. */
  private class HandleGlyphView(
    context: Context,
    private val type: GlyphType,
  ) : View(context) {
    enum class GlyphType { DOCK, HIDE, DRAG }

    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.8f, resources.displayMetrics)
      }
    private val path = Path()

    fun setTint(color: Int) {
      paint.color = color
      invalidate()
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val cx = width / 2f
      val cy = height / 2f
      val d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5.5f, resources.displayMetrics)
      path.reset()
      when (type) {
        GlyphType.DOCK -> {
          // Box + downward arrow indicating dock back to bottom
          path.moveTo(cx - d * 1.1f, cy + d * 0.45f)
          path.lineTo(cx - d * 1.1f, cy + d * 1.0f)
          path.lineTo(cx + d * 1.1f, cy + d * 1.0f)
          path.lineTo(cx + d * 1.1f, cy + d * 0.45f)
          path.moveTo(cx, cy - d * 1.0f)
          path.lineTo(cx, cy + d * 0.45f)
          path.moveTo(cx - d * 0.5f, cy)
          path.lineTo(cx, cy + d * 0.45f)
          path.lineTo(cx + d * 0.5f, cy)
        }
        GlyphType.HIDE -> {
          // Down chevron indicating hide keyboard
          path.moveTo(cx - d * 0.9f, cy - d * 0.35f)
          path.lineTo(cx, cy + d * 0.45f)
          path.lineTo(cx + d * 0.9f, cy - d * 0.35f)
        }
        GlyphType.DRAG -> {
          // Tactile drag handle glyph: 2 horizontal rounded grip bars
          path.moveTo(cx - d * 1.1f, cy - d * 0.45f)
          path.lineTo(cx + d * 1.1f, cy - d * 0.45f)
          path.moveTo(cx - d * 1.1f, cy + d * 0.45f)
          path.lineTo(cx + d * 1.1f, cy + d * 0.45f)
        }
      }
      canvas.drawPath(path, paint)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun createResizeHandle(): ResizeHandleView {
    return ResizeHandleView(this).apply {
      visibility = View.GONE
      layoutParams =
        FrameLayout.LayoutParams(dpInt(32f), dpInt(32f), Gravity.BOTTOM or Gravity.END).apply {
          setMargins(0, 0, dpInt(2f), dpInt(2f))
        }
      contentDescription = "Resize floating keyboard"
      var startW = 0
      var startH = 0
      var startRawX = 0f
      var startRawY = 0f
      setOnTouchListener { _, event ->
        val card = keyboardCardContainer ?: return@setOnTouchListener false
        val root = rootFrame ?: return@setOnTouchListener false
        val dm = resources.displayMetrics
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            isDraggingFloating = true
            pendingOutsideHideJob?.cancel()
            startW = card.width
            startH = card.height
            startRawX = event.rawX
            startRawY = event.rawY
            true
          }
          MotionEvent.ACTION_MOVE -> {
            val dx = event.rawX - startRawX
            val dy = event.rawY - startRawY
            val newW = (startW + dx).toInt().coerceIn(dpInt(260f), dm.widthPixels - dpInt(16f))
            val newH = (startH + dy).toInt().coerceIn(dpInt(180f), dm.heightPixels * 3 / 4)
            card.layoutParams = card.layoutParams.apply {
              width = newW
              height = newH
            }
            root.requestLayout()
            true
          }
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            isDraggingFloating = false
            val dy = event.rawY - startRawY
            val scaleDelta = dy / 350f
            val newScale = (settings.keyboardHeightScale + scaleDelta).coerceIn(0.75f, 1.4f)
            serviceScope.launch { prefsRepo.setHeightScale(newScale) }
            root.requestLayout()
            true
          }
          else -> false
        }
      }
    }
  }

  private class ResizeHandleView(context: Context) : View(context) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88888888.toInt()
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
      }

    fun setTint(color: Int) {
      paint.color = color
      invalidate()
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val w = width.toFloat()
      val h = height.toFloat()
      canvas.drawLine(w * 0.3f, h * 0.85f, w * 0.85f, h * 0.3f, paint)
      canvas.drawLine(w * 0.5f, h * 0.85f, w * 0.85f, h * 0.5f, paint)
      canvas.drawLine(w * 0.7f, h * 0.85f, w * 0.85f, h * 0.7f, paint)
    }
  }

  /**
   * Root container that expands to full window dimensions in floating mode even when the IME host
   * wraps `mInputFrame` with `AT_MOST`, allowing unrestricted 2D movement across the display.
   */
  private inner class FloatingRootFrameLayout(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      if (settings.floatingMode) {
        val dm = resources.displayMetrics
        val w =
          if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MeasureSpec.getSize(widthMeasureSpec)
          } else {
            dm.widthPixels
          }
        val h =
          if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MeasureSpec.getSize(heightMeasureSpec)
          } else {
            dm.heightPixels
          }
        setMeasuredDimension(w, h)
      }
    }
  }

  /**
   * Custom [View.OnTouchListener] for the floating keyboard container that computes incremental
   * movement deltas from absolute screen coordinates ([MotionEvent.getRawX]/[MotionEvent.getRawY])
   * instead of view-relative offsets so dragging works smoothly anywhere on the screen.
   */
  private inner class FloatingKeyboardTouchListener : View.OnTouchListener {
    private var lastRawX = 0f
    private var lastRawY = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
      if (!settings.floatingMode) return false
      val root = rootFrame ?: return false
      val card = keyboardCardContainer ?: return false

      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          isDraggingFloating = true
          pendingOutsideHideJob?.cancel()
          lastRawX = event.rawX
          lastRawY = event.rawY
          return true
        }
        MotionEvent.ACTION_MOVE -> {
          val deltaX = event.rawX - lastRawX
          val deltaY = event.rawY - lastRawY
          lastRawX = event.rawX
          lastRawY = event.rawY

          val bounds = computeFloatingTravelBounds(root, card)
          card.translationX = (card.translationX + deltaX).coerceIn(bounds.minX, bounds.maxX)
          card.translationY = (card.translationY + deltaY).coerceIn(bounds.minY, bounds.maxY)
          return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          isDraggingFloating = false
          val bounds = computeFloatingTravelBounds(root, card)
          val xRange = (bounds.maxX - bounds.minX).coerceAtLeast(1f)
          val yRange = (bounds.maxY - bounds.minY).coerceAtLeast(1f)
          val xFrac = ((card.translationX - bounds.minX) / xRange).coerceIn(0f, 1f)
          val yFrac = ((card.translationY - bounds.minY) / yRange).coerceIn(0f, 1f)
          serviceScope.launch { prefsRepo.setFloatingPosition(xFrac, yFrac) }
          root.requestLayout()
          return true
        }
        else -> return false
      }
    }
  }
}
