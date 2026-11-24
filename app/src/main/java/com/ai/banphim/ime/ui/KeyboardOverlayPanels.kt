package com.ai.banphim.ime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.ai.banphim.R
import com.ai.banphim.ai.SupportedLanguage
import com.ai.banphim.ai.TranslationResult
import com.ai.banphim.data.ClipboardClipEntity
import com.ai.banphim.data.KeyboardThemePreset
import com.ai.banphim.data.TranslationMode
import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.KeyboardLanguage
import com.ai.banphim.ime.engine.PinyinEngine
import com.ai.banphim.ime.view.KeyboardThemePalette
import com.ai.banphim.ui.localizedLabel
import com.ai.banphim.ui.localizedName

/** Active overlay panel rendered in the IME header or body area. */
enum class ImePanelMode {
  NONE,
  TRANSLATE,
  CLIPBOARD,
  EMOJI,
  THEME,
  LANGUAGE,
}

/** Immutable UI state consumed by the IME Compose header and overlay body panels. */
data class ImeUiState(
  val panelMode: ImePanelMode = ImePanelMode.NONE,
  val activeLanguage: KeyboardLanguage = KeyboardLanguage.VIETNAMESE,
  val enabledLanguages: List<KeyboardLanguage> =
    listOf(KeyboardLanguage.VIETNAMESE, KeyboardLanguage.ENGLISH),
  val inputMode: InputMethodMode = InputMethodMode.TELEX,
  val themePreset: KeyboardThemePreset = KeyboardThemePreset.DYNAMIC,
  val appLanguageCode: String = "",
  val floatingMode: Boolean = false,
  val isLandscape: Boolean = false,
  val suggestions: List<String> = emptyList(),
  val pinyinCandidates: List<PinyinEngine.Candidate> = emptyList(),
  val candidateComposingDisplay: String = "",
  val showSuggestionsStrip: Boolean = false,
  val autoCorrectPreview: String? = null,
  val translateSourceText: String = "",
  val translateCursorIndex: Int = 0,
  val sourceLang: SupportedLanguage = SupportedLanguage.findByCode("auto"),
  val targetLang: SupportedLanguage = SupportedLanguage.findByCode("en"),
  val isTranslating: Boolean = false,
  val translationResult: TranslationResult? = null,
  val aiPrivacyAccepted: Boolean = false,
  val liveTranslateEnabled: Boolean = true,
  val translationMode: TranslationMode = TranslationMode.AUTO_HYBRID,
  val isLiveSyncedToHost: Boolean = false,
  val clips: List<ClipboardClipEntity> = emptyList(),
  val recentEmojis: List<String> = emptyList(),
)

/**
 * Top IME header strip hosting either Pinyin/word suggestions or the evenly distributed toolbar,
 * plus the expandable inline AI Translation card when [ImePanelMode.TRANSLATE] is active.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImeHeaderHost(
  state: ImeUiState,
  palette: KeyboardThemePalette,
  onTogglePanel: (ImePanelMode) -> Unit,
  onCycleInputMode: () -> Unit,
  onCycleLanguage: () -> Unit,
  onToggleFloatingMode: () -> Unit,
  onHideKeyboard: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleToolbarMode: (Boolean) -> Unit,
  onSuggestionClick: (String) -> Unit,
  onForgetSuggestion: (String) -> Unit,
  onPinyinCandidateClick: (PinyinEngine.Candidate) -> Unit,
  onSourceLangSelected: (SupportedLanguage) -> Unit,
  onTargetLangSelected: (SupportedLanguage) -> Unit,
  onSwapLanguages: () -> Unit,
  onTranslateNow: () -> Unit,
  onClearTranslateSource: () -> Unit,
  onPullFromEditor: () -> Unit,
  onPasteToTranslate: () -> Unit,
  onInsertTranslation: (String) -> Unit,
  onCopyTranslation: (String) -> Unit,
  onAcceptAiPrivacy: () -> Unit,
  onToggleLiveTranslate: (Boolean) -> Unit,
  onCycleTranslationMode: () -> Unit,
  onTranslateCursorChanged: (Int) -> Unit,
  onDropdownOpenChanged: (Boolean) -> Unit,
) {
  val bg = Color(palette.toolbarBackground)
  val fg = Color(palette.keyText)
  val hint = Color(palette.keyHintText)
  val accent = Color(palette.accent)
  val keyBg = Color(palette.keyBackground)
  val compactHeader = state.isLandscape || state.floatingMode
  val barHeight = if (compactHeader) 36.dp else 44.dp

  Column(
    modifier = Modifier.fillMaxWidth().background(bg),
  ) {
    // Top Toolbar or Suggestion Strip
    BoxWithConstraints(
      modifier = Modifier.fillMaxWidth().height(barHeight).padding(horizontal = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      val ultraNarrow = maxWidth < 285.dp
      val denseToolbar = compactHeader || maxWidth < 370.dp

      if (state.pinyinCandidates.isNotEmpty() &&
        (state.panelMode == ImePanelMode.NONE || state.panelMode == ImePanelMode.TRANSLATE)
      ) {
        Row(
          modifier = Modifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (state.candidateComposingDisplay.isNotEmpty()) {
            Surface(
              color = keyBg,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.padding(end = 6.dp),
            ) {
              Text(
                text = state.candidateComposingDisplay,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            state.pinyinCandidates.forEachIndexed { idx, cand ->
              Box(
                modifier =
                  Modifier.height(if (compactHeader) 30.dp else 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPinyinCandidateClick(cand) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = cand.text,
                  color = if (idx == 0) accent else fg,
                  fontSize = if (compactHeader) 15.sp else 16.sp,
                  fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }
          }
        }
      } else if (state.showSuggestionsStrip &&
        state.suggestions.isNotEmpty() &&
        (state.panelMode == ImePanelMode.NONE || state.panelMode == ImePanelMode.TRANSLATE)
      ) {
        var pendingForgetWord by remember(state.suggestions) { mutableStateOf<String?>(null) }

        if (pendingForgetWord != null) {
          val targetWord = pendingForgetWord!!
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.ime_forget_word_prompt, targetWord),
              color = fg,
              fontSize = if (compactHeader) 12.sp else 13.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Surface(
                color = Color(0xFFEF4444).copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable {
                      onForgetSuggestion(targetWord)
                      pendingForgetWord = null
                    }
                    .testTag("ime_forget_confirm_btn"),
              ) {
                Text(
                  text = stringResource(R.string.ime_forget_word_confirm),
                  color = Color(0xFFEF4444),
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
              Surface(
                color = keyBg,
                shape = RoundedCornerShape(8.dp),
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { pendingForgetWord = null }
                    .testTag("ime_forget_cancel_btn"),
              ) {
                Text(
                  text = stringResource(R.string.ime_forget_word_cancel),
                  color = hint,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
        } else {
          Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (state.panelMode == ImePanelMode.NONE) {
              IconButton(
                onClick = { onToggleToolbarMode(false) },
                modifier = Modifier.size(if (compactHeader) 32.dp else 38.dp).testTag("ime_show_toolbar_btn"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                  contentDescription = stringResource(R.string.ime_cd_show_toolbar),
                  tint = fg,
                )
              }
            }
            state.suggestions.take(3).forEachIndexed { idx, word ->
              val isAutoTarget = state.autoCorrectPreview == word || (idx == 0 && state.suggestions.size == 1)
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(if (compactHeader) 30.dp else 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                      onClick = { onSuggestionClick(word) },
                      onLongClick = { pendingForgetWord = word },
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = word,
                  color = if (isAutoTarget) accent else fg,
                  fontSize = if (compactHeader) 14.sp else 15.sp,
                  fontWeight = if (isAutoTarget) FontWeight.SemiBold else FontWeight.Normal,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      } else {
        val rowModifier =
          if (ultraNarrow) {
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
          } else {
            Modifier.fillMaxSize()
          }
        Row(
          modifier = rowModifier,
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement =
            if (ultraNarrow) Arrangement.spacedBy(4.dp) else Arrangement.SpaceBetween,
        ) {
          val iconSize = if (denseToolbar) 16.dp else 18.dp
          ToolbarActionChip(
            icon = {
              Icon(
                Icons.Default.Translate,
                contentDescription = stringResource(R.string.ime_cd_ai_translate),
                tint = if (state.panelMode == ImePanelMode.TRANSLATE) Color(palette.enterKeyText) else accent,
                modifier = Modifier.size(if (denseToolbar) 14.dp else 16.dp),
              )
            },
            label = stringResource(R.string.ime_toolbar_translate),
            compact = denseToolbar,
            selected = state.panelMode == ImePanelMode.TRANSLATE,
            accent = accent,
            selectedFg = Color(palette.enterKeyText),
            fg = fg,
            onClick = { onTogglePanel(ImePanelMode.TRANSLATE) },
            testTag = "toolbar_translate_btn",
          )
          ToolbarActionChip(
            icon = {
              Icon(
                Icons.Default.Language,
                contentDescription = stringResource(R.string.ime_cd_choose_lang),
                tint = if (state.panelMode == ImePanelMode.LANGUAGE) Color(palette.enterKeyText) else fg,
                modifier = Modifier.size(if (denseToolbar) 14.dp else 16.dp),
              )
            },
            label = state.activeLanguage.shortBadge,
            compact = denseToolbar,
            selected = state.panelMode == ImePanelMode.LANGUAGE,
            accent = accent,
            selectedFg = Color(palette.enterKeyText),
            fg = fg,
            onClick = { onTogglePanel(ImePanelMode.LANGUAGE) },
            testTag = "toolbar_language_btn",
          )
          ToolbarIconButton(
            compact = denseToolbar,
            selected = state.panelMode == ImePanelMode.CLIPBOARD,
            accent = accent,
            fg = fg,
            selectedFg = Color(palette.enterKeyText),
            onClick = { onTogglePanel(ImePanelMode.CLIPBOARD) },
            contentDescription = stringResource(R.string.ime_toolbar_clipboard),
            testTag = "toolbar_clipboard_btn",
          ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(iconSize))
          }
          ToolbarIconButton(
            compact = denseToolbar,
            selected = state.panelMode == ImePanelMode.EMOJI,
            accent = accent,
            fg = fg,
            selectedFg = Color(palette.enterKeyText),
            onClick = { onTogglePanel(ImePanelMode.EMOJI) },
            contentDescription = stringResource(R.string.ime_toolbar_emoji),
            testTag = "toolbar_emoji_btn",
          ) {
            Icon(Icons.Default.EmojiEmotions, contentDescription = null, modifier = Modifier.size(iconSize))
          }
          ToolbarIconButton(
            compact = denseToolbar,
            selected = state.panelMode == ImePanelMode.THEME,
            accent = accent,
            fg = fg,
            selectedFg = Color(palette.enterKeyText),
            onClick = { onTogglePanel(ImePanelMode.THEME) },
            contentDescription = stringResource(R.string.ime_toolbar_theme),
            testTag = "toolbar_theme_btn",
          ) {
            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(iconSize))
          }
          ToolbarIconButton(
            compact = denseToolbar,
            selected = state.floatingMode,
            accent = accent,
            fg = fg,
            selectedFg = Color(palette.enterKeyText),
            onClick = onToggleFloatingMode,
            contentDescription = stringResource(R.string.ime_toolbar_floating),
            testTag = "toolbar_floating_btn",
          ) {
            Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(iconSize))
          }
          if (state.activeLanguage == KeyboardLanguage.VIETNAMESE) {
            val modeText =
              when (state.inputMode) {
                InputMethodMode.TELEX -> "TELEX"
                InputMethodMode.VNI -> "VNI"
                InputMethodMode.OFF -> "EN"
              }
            Surface(
              color = keyBg,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.height(if (denseToolbar) 25.dp else 30.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onCycleInputMode() }
                  .testTag("toolbar_mode_toggle"),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = if (denseToolbar) 7.dp else 9.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = modeText,
                  color = fg,
                  fontSize = if (denseToolbar) 10.sp else 11.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                )
              }
            }
          } else if (state.enabledLanguages.size > 1) {
            Surface(
              color = keyBg,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.height(if (denseToolbar) 25.dp else 30.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onCycleLanguage() }
                  .testTag("toolbar_quick_cycle_lang"),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = if (denseToolbar) 6.dp else 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = stringResource(R.string.ime_toolbar_switch_lang),
                  color = fg,
                  fontSize = if (denseToolbar) 9.5.sp else 10.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                )
              }
            }
          }
          ToolbarIconButton(
            compact = denseToolbar,
            selected = false,
            accent = accent,
            fg = hint,
            selectedFg = fg,
            onClick = onOpenSettings,
            contentDescription = stringResource(R.string.ime_toolbar_settings),
            testTag = "toolbar_settings_btn",
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(iconSize))
          }
          ToolbarIconButton(
            compact = denseToolbar,
            selected = false,
            accent = accent,
            fg = hint,
            selectedFg = fg,
            onClick = onHideKeyboard,
            contentDescription = stringResource(R.string.ime_toolbar_hide_kb),
            testTag = "toolbar_hide_kb_btn",
          ) {
            Icon(Icons.Default.KeyboardHide, contentDescription = null, modifier = Modifier.size(iconSize))
          }
        }
      }
    }

    // Inline AI Translation Bar
    AnimatedVisibility(visible = state.panelMode == ImePanelMode.TRANSLATE) {
      InlineAiTranslationBar(
        state = state,
        palette = palette,
        compact = compactHeader,
        onSourceLangSelected = onSourceLangSelected,
        onTargetLangSelected = onTargetLangSelected,
        onSwapLanguages = onSwapLanguages,
        onTranslateNow = onTranslateNow,
        onClearSource = onClearTranslateSource,
        onPullFromEditor = onPullFromEditor,
        onPasteToTranslate = onPasteToTranslate,
        onInsertTranslation = onInsertTranslation,
        onCopyTranslation = onCopyTranslation,
        onAcceptPrivacy = onAcceptAiPrivacy,
        onToggleLiveTranslate = onToggleLiveTranslate,
        onCycleTranslationMode = onCycleTranslationMode,
        onTranslateCursorChanged = onTranslateCursorChanged,
        onDropdownOpenChanged = onDropdownOpenChanged,
        onClose = { onTogglePanel(ImePanelMode.NONE) },
      )
    }
  }
}

@Composable
private fun ToolbarActionChip(
  icon: @Composable () -> Unit,
  label: String,
  compact: Boolean,
  selected: Boolean,
  accent: Color,
  selectedFg: Color,
  fg: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    color = if (selected) accent else Color.Transparent,
    shape = RoundedCornerShape(16.dp),
    modifier =
      Modifier.height(if (compact) 26.dp else 32.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = if (compact) 6.dp else 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
    ) {
      icon()
      Text(
        text = label,
        color = if (selected) selectedFg else fg,
        fontSize = if (compact) 11.sp else 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ToolbarIconButton(
  compact: Boolean,
  selected: Boolean,
  accent: Color,
  fg: Color,
  selectedFg: Color,
  onClick: () -> Unit,
  contentDescription: String,
  testTag: String,
  icon: @Composable () -> Unit,
) {
  Box(
    modifier =
      Modifier.size(if (compact) 28.dp else 34.dp)
        .clip(CircleShape)
        .background(if (selected) accent else Color.Transparent)
        .semantics {
          this.contentDescription = contentDescription
          this.role = Role.Button
        }
        .clickable(onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    androidx.compose.runtime.CompositionLocalProvider(
      androidx.compose.material3.LocalContentColor provides (if (selected) selectedFg else fg)
    ) {
      icon()
    }
  }
}

private enum class TranslationMenuMode { NONE, SOURCE, TARGET }

/** Expandable Gboard-style AI translation bar with language pickers and live-sync controls. */

@Composable
private fun InlineAiTranslationBar(
  state: ImeUiState,
  palette: KeyboardThemePalette,
  compact: Boolean,
  onSourceLangSelected: (SupportedLanguage) -> Unit,
  onTargetLangSelected: (SupportedLanguage) -> Unit,
  onSwapLanguages: () -> Unit,
  onTranslateNow: () -> Unit,
  onClearSource: () -> Unit,
  onPullFromEditor: () -> Unit,
  onPasteToTranslate: () -> Unit,
  onInsertTranslation: (String) -> Unit,
  onCopyTranslation: (String) -> Unit,
  onAcceptPrivacy: () -> Unit,
  onToggleLiveTranslate: (Boolean) -> Unit,
  onCycleTranslationMode: () -> Unit,
  onTranslateCursorChanged: (Int) -> Unit,
  onDropdownOpenChanged: (Boolean) -> Unit,
  onClose: () -> Unit,
) {
  val cardBg = Color(palette.keyBackground)
  val fg = Color(palette.keyText)
  val hint = Color(palette.keyHintText)
  val accent = Color(palette.accent)
  val divider = Color(palette.keyDivider)

  var activeMenuMode by remember { mutableStateOf(TranslationMenuMode.NONE) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(cardBg)
        .border(1.dp, divider, RoundedCornerShape(14.dp))
        .padding(if (compact) 6.dp else 8.dp),
    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
  ) {
    if (!state.aiPrivacyAccepted && state.translationMode != TranslationMode.OFFLINE_MLKIT) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.privacy_notice_title),
          color = fg,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = stringResource(R.string.privacy_inline_body),
          color = hint,
          fontSize = 11.sp,
          lineHeight = 14.sp,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onClose) { Text(stringResource(R.string.privacy_close), color = hint) }
          FilledTonalButton(
            onClick = onAcceptPrivacy,
            modifier = Modifier.testTag("accept_ai_privacy_btn"),
          ) {
            Text(stringResource(R.string.privacy_accept))
          }
        }
      }
      return@Column
    }

    // Language & action row
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Surface(
          color = if (activeMenuMode == TranslationMenuMode.SOURCE) accent.copy(alpha = 0.16f) else Color.Transparent,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(0.8.dp, if (activeMenuMode == TranslationMenuMode.SOURCE) accent else divider),
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .clickable {
                val next =
                  if (activeMenuMode == TranslationMenuMode.SOURCE) {
                    TranslationMenuMode.NONE
                  } else {
                    TranslationMenuMode.SOURCE
                  }
                activeMenuMode = next
                onDropdownOpenChanged(next != TranslationMenuMode.NONE)
              },
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = state.sourceLang.localizedName(),
              color = if (activeMenuMode == TranslationMenuMode.SOURCE) accent else fg,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(
              if (activeMenuMode == TranslationMenuMode.SOURCE) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
              contentDescription = null,
              tint = if (activeMenuMode == TranslationMenuMode.SOURCE) accent else hint,
              modifier = Modifier.size(16.dp),
            )
          }
        }

        IconButton(
          onClick = {
            activeMenuMode = TranslationMenuMode.NONE
            onDropdownOpenChanged(false)
            onSwapLanguages()
          },
          modifier = Modifier.size(26.dp),
        ) {
          Icon(
            Icons.Default.SwapHoriz,
            contentDescription = stringResource(R.string.ime_translate_swap_cd),
            tint = hint,
            modifier = Modifier.size(17.dp),
          )
        }

        Surface(
          color = if (activeMenuMode == TranslationMenuMode.TARGET) accent.copy(alpha = 0.16f) else Color.Transparent,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(0.8.dp, if (activeMenuMode == TranslationMenuMode.TARGET) accent else divider),
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .clickable {
                val next =
                  if (activeMenuMode == TranslationMenuMode.TARGET) {
                    TranslationMenuMode.NONE
                  } else {
                    TranslationMenuMode.TARGET
                  }
                activeMenuMode = next
                onDropdownOpenChanged(next != TranslationMenuMode.NONE)
              },
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = state.targetLang.localizedName(),
              color = if (activeMenuMode == TranslationMenuMode.TARGET) accent else fg,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(
              if (activeMenuMode == TranslationMenuMode.TARGET) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
              contentDescription = null,
              tint = if (activeMenuMode == TranslationMenuMode.TARGET) accent else hint,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        val modeLabel =
          when (state.translationMode) {
            TranslationMode.AUTO_HYBRID -> stringResource(R.string.ime_translate_mode_auto)
            TranslationMode.OFFLINE_MLKIT -> stringResource(R.string.ime_translate_mode_offline)
            TranslationMode.ONLINE_AI -> stringResource(R.string.ime_translate_mode_online)
          }
        val isOfflineMode = state.translationMode == TranslationMode.OFFLINE_MLKIT
        Surface(
          color = if (isOfflineMode) Color(0xFF10B981).copy(alpha = 0.16f) else accent.copy(alpha = 0.12f),
          shape = RoundedCornerShape(10.dp),
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .border(
                width = 0.8.dp,
                color = if (isOfflineMode) Color(0xFF10B981) else divider,
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onCycleTranslationMode() }
              .testTag("ime_translation_mode_pill"),
        ) {
          Text(
            text = modeLabel,
            color = if (isOfflineMode) Color(0xFF10B981) else fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
          )
        }
        Surface(
          color = if (state.liveTranslateEnabled) accent.copy(alpha = 0.16f) else Color.Transparent,
          shape = RoundedCornerShape(10.dp),
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .border(
                width = 0.8.dp,
                color = if (state.liveTranslateEnabled) accent else divider,
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onToggleLiveTranslate(!state.liveTranslateEnabled) }
              .testTag("ime_live_translate_toggle"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Box(
              modifier =
                Modifier.size(6.dp)
                  .clip(CircleShape)
                  .background(if (state.liveTranslateEnabled) accent else hint),
            )
            Text(
              text = stringResource(R.string.ime_live_badge),
              color = if (state.liveTranslateEnabled) accent else hint,
              fontSize = 10.sp,
              fontWeight = if (state.liveTranslateEnabled) FontWeight.SemiBold else FontWeight.Normal,
            )
          }
        }
        Text(
          text = stringResource(R.string.ime_translate_pull),
          color = hint,
          fontSize = 10.5.sp,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .clickable { onPullFromEditor() }
              .padding(horizontal = 4.dp, vertical = 3.dp),
        )
        Text(
          text = stringResource(R.string.ime_translate_paste),
          color = hint,
          fontSize = 10.5.sp,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .clickable { onPasteToTranslate() }
              .padding(horizontal = 4.dp, vertical = 3.dp),
        )
        IconButton(
          onClick = {
            activeMenuMode = TranslationMenuMode.NONE
            onDropdownOpenChanged(false)
            onClose()
          },
          modifier = Modifier.size(24.dp),
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.ime_translate_close_cd),
            tint = hint,
            modifier = Modifier.size(15.dp),
          )
        }
      }
    }

    // Inline Language Dropdown Picker: bounded to 3-4 visible languages, scroll/swipe up to browse more
    AnimatedVisibility(visible = activeMenuMode != TranslationMenuMode.NONE) {
      val isSource = activeMenuMode == TranslationMenuMode.SOURCE
      val currentLang = if (isSource) state.sourceLang else state.targetLang
      val languages = if (isSource) SupportedLanguage.ALL else SupportedLanguage.TARGET_LANGUAGES

      Surface(
        color = Color(palette.toolbarBackground),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.8.dp, divider),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (isSource) {
                  stringResource(R.string.ime_translate_source_select)
                } else {
                  stringResource(R.string.ime_translate_target_select)
                },
              color = hint,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
            )
            IconButton(
              onClick = {
                activeMenuMode = TranslationMenuMode.NONE
                onDropdownOpenChanged(false)
              },
              modifier = Modifier.size(22.dp),
            ) {
              Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.ime_translate_close_cd),
                tint = hint,
                modifier = Modifier.size(13.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(3.dp))

          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 136.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            items(languages, key = { it.code }) { lang ->
              val isSelected = lang == currentLang
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable {
                      activeMenuMode = TranslationMenuMode.NONE
                      onDropdownOpenChanged(false)
                      if (isSource) onSourceLangSelected(lang) else onTargetLangSelected(lang)
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    text = lang.localizedName(),
                    color = if (isSelected) accent else fg,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  )
                  Text(
                    text = "(${lang.nativeName})",
                    color = hint,
                    fontSize = 10.5.sp,
                  )
                }
                if (isSelected) {
                  Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                  )
                }
              }
            }
          }
        }
      }
    }

    // Source input display + Translate button
    val placeholderText =
      if (state.liveTranslateEnabled) {
        stringResource(R.string.ime_translate_placeholder_live)
      } else {
        stringResource(R.string.ime_translate_placeholder_manual)
      }

    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Color(palette.toolbarBackground))
          .padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TranslateSourceInputField(
        text = state.translateSourceText,
        cursorIndex = state.translateCursorIndex,
        placeholderText = placeholderText,
        textColor = fg,
        hintColor = hint,
        accentColor = accent,
        compact = compact,
        onCursorChanged = onTranslateCursorChanged,
        modifier = Modifier.weight(1f),
      )
      if (state.translateSourceText.isNotEmpty()) {
        IconButton(onClick = onClearSource, modifier = Modifier.size(26.dp)) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.ime_translate_clear_cd),
            tint = hint,
            modifier = Modifier.size(14.dp),
          )
        }
      }
      Spacer(modifier = Modifier.width(4.dp))
      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (state.translateSourceText.isNotBlank()) accent else divider)
            .clickable(enabled = state.translateSourceText.isNotBlank() && !state.isTranslating) {
              onTranslateNow()
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("ime_translate_submit_btn"),
        contentAlignment = Alignment.Center,
      ) {
        if (state.isTranslating) {
          CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Color(palette.enterKeyText),
          )
        } else {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(R.string.ime_translate_btn),
              tint = Color(palette.enterKeyText),
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = stringResource(R.string.ime_translate_btn),
              color = Color(palette.enterKeyText),
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // Result / Error area
    when (val res = state.translationResult) {
      is TranslationResult.Success -> {
        HorizontalDivider(color = divider, thickness = 0.6.dp)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val cacheLabel = stringResource(R.string.ime_translate_cache)
          val syncingLabel = stringResource(R.string.ime_translate_syncing)
          val switchedNote =
            res.switchedFromModelId?.let { prevId ->
              stringResource(R.string.ai_model_switched_note, prevId)
            }
          val resultScrollState = rememberScrollState()
          Column(modifier = Modifier.weight(1f)) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(max = if (compact) 54.dp else 88.dp)
                  .verticalScroll(resultScrollState),
            ) {
              Text(
                text = res.translatedText,
                color = fg,
                fontSize = if (compact) 13.sp else 14.sp,
                lineHeight = if (compact) 17.sp else 19.sp,
                fontWeight = FontWeight.Medium,
                softWrap = true,
              )
            }
            Text(
              text =
                buildString {
                  append(res.providerName)
                  append(" • ")
                  append(res.modelId)
                  if (switchedNote != null) append(" • $switchedNote")
                  if (res.fromCache) append(" ($cacheLabel)")
                  if (state.isLiveSyncedToHost) append(" • $syncingLabel")
                },
              color = hint,
              fontSize = 9.5.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(onClick = { onCopyTranslation(res.translatedText) }, modifier = Modifier.size(30.dp)) {
            Icon(
              Icons.Default.ContentCopy,
              contentDescription = stringResource(R.string.ime_translate_copy_cd),
              tint = hint,
              modifier = Modifier.size(15.dp),
            )
          }
          Spacer(modifier = Modifier.width(2.dp))
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(accent)
                .clickable { onInsertTranslation(res.translatedText) }
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .testTag("ime_insert_translation_btn"),
          ) {
            Text(
              text =
                if (state.isLiveSyncedToHost) {
                  stringResource(R.string.ime_translate_done)
                } else {
                  stringResource(R.string.ime_translate_insert)
                },
              color = Color(palette.enterKeyText),
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
      is TranslationResult.Error -> {
        Text(
          text = res.userMessage,
          color = Color(0xFFEF4444),
          fontSize = 11.sp,
          lineHeight = 14.sp,
        )
      }
      null -> Unit
    }
  }
}

/**
 * Scrollable multi-line text input display for the AI Translation Panel.
 * Renders an animated blinking cursor, wraps arbitrarily long text without truncation,
 * supports tap-to-reposition cursor, and auto-scrolls the viewport to follow cursor movement.
 */
@Composable
private fun TranslateSourceInputField(
  text: String,
  cursorIndex: Int,
  placeholderText: String,
  textColor: Color,
  hintColor: Color,
  accentColor: Color,
  compact: Boolean,
  onCursorChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var viewportHeightPx by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current

  val isEmpty = text.isEmpty()
  val displayText = if (isEmpty) placeholderText else text
  val clampedCursor = if (isEmpty) 0 else cursorIndex.coerceIn(0, text.length)

  var cursorVisible by remember(text, clampedCursor) { mutableStateOf(true) }
  LaunchedEffect(text, clampedCursor) {
    cursorVisible = true
    while (true) {
      delay(530L)
      cursorVisible = !cursorVisible
    }
  }

  LaunchedEffect(
    text,
    clampedCursor,
    textLayoutResult,
    viewportHeightPx,
    scrollState.maxValue,
  ) {
    val layout = textLayoutResult ?: return@LaunchedEffect
    if (layout.layoutInput.text.text != displayText || viewportHeightPx <= 0) return@LaunchedEffect

    val safeOffset = clampedCursor.coerceIn(0, layout.layoutInput.text.length)
    val cursorRect = layout.getCursorRect(safeOffset)
    val marginPx = with(density) { 4.dp.toPx() }
    val visibleTop = scrollState.value.toFloat()
    val visibleBottom = visibleTop + viewportHeightPx.toFloat()
    val targetTop = (cursorRect.top - marginPx).coerceAtLeast(0f)
    val targetBottom = cursorRect.bottom + marginPx

    val desiredScroll =
      when {
        targetBottom > visibleBottom -> (targetBottom - viewportHeightPx).roundToInt()
        targetTop < visibleTop -> targetTop.roundToInt()
        else -> null
      }

    if (desiredScroll != null) {
      val clampedScroll = desiredScroll.coerceIn(0, scrollState.maxValue)
      if (clampedScroll != scrollState.value) {
        scrollState.scrollTo(clampedScroll)
      }
    }
  }

  Box(
    modifier =
      modifier
        .padding(vertical = 2.dp)
        .heightIn(min = if (compact) 22.dp else 26.dp, max = if (compact) 68.dp else 108.dp)
        .onSizeChanged { viewportHeightPx = it.height }
        .verticalScroll(scrollState)
        .testTag("ime_translate_source_input"),
  ) {
    Text(
      text = displayText,
      color = if (isEmpty) hintColor else textColor,
      fontSize = if (compact) 12.5.sp else 13.5.sp,
      lineHeight = if (compact) 17.sp else 19.sp,
      softWrap = true,
      onTextLayout = { textLayoutResult = it },
      modifier =
        Modifier.fillMaxWidth()
          .pointerInput(text) {
            detectTapGestures { tapOffset ->
              if (text.isEmpty()) {
                onCursorChanged(0)
              } else {
                val layout = textLayoutResult ?: return@detectTapGestures
                if (layout.layoutInput.text.text == displayText) {
                  val tappedOffset = layout.getOffsetForPosition(tapOffset).coerceIn(0, text.length)
                  onCursorChanged(tappedOffset)
                }
              }
            }
          }
          .drawWithContent {
            drawContent()
            if (cursorVisible) {
              val layout = textLayoutResult
              if (layout != null && layout.layoutInput.text.text == displayText) {
                val safeOffset = clampedCursor.coerceIn(0, layout.layoutInput.text.length)
                val rect = layout.getCursorRect(safeOffset)
                val strokeWidth = 2.dp.toPx()
                val left = rect.left.coerceIn(0f, (size.width - strokeWidth).coerceAtLeast(0f))
                val top = (rect.top + 1.dp.toPx()).coerceAtMost(rect.bottom)
                val cursorHeight = (rect.height - 2.dp.toPx()).coerceAtLeast(10.sp.toPx())
                drawRect(
                  color = accentColor,
                  topLeft = Offset(left, top),
                  size = Size(strokeWidth, cursorHeight),
                )
              }
            }
          },
    )
  }
}

/**
 * Body panel host displayed in place of the keyboard surface for Emoji, Clipboard, Theme, or Language selection.
 */
@Composable
fun ImeBodyPanelHost(
  state: ImeUiState,
  palette: KeyboardThemePalette,
  panelHeightDp: Int,
  onClosePanel: () -> Unit,
  onEmojiPicked: (String) -> Unit,
  onBackspaceClick: () -> Unit,
  onInsertClip: (String) -> Unit,
  onTogglePinClip: (Long) -> Unit,
  onDeleteClip: (Long) -> Unit,
  onClearUnpinnedClips: () -> Unit,
  onSelectThemePreset: (KeyboardThemePreset) -> Unit,
  onSelectKeyboardLanguage: (KeyboardLanguage) -> Unit,
  onToggleLanguageEnabled: (KeyboardLanguage) -> Unit,
  onShowSystemImePicker: () -> Unit,
) {
  val bg = Color(palette.keyboardBackground)
  val cardBg = Color(palette.keyBackground)
  val fg = Color(palette.keyText)
  val hint = Color(palette.keyHintText)
  val accent = Color(palette.accent)
  val divider = Color(palette.keyDivider)

  Box(
    modifier = Modifier.fillMaxWidth().height(panelHeightDp.dp).background(bg),
  ) {
    when (state.panelMode) {
      ImePanelMode.EMOJI -> {
        EmojiPanelContent(
          recentEmojis = state.recentEmojis,
          fg = fg,
          hint = hint,
          accent = accent,
          cardBg = cardBg,
          onEmojiPicked = onEmojiPicked,
          onBackspaceClick = onBackspaceClick,
          onClose = onClosePanel,
        )
      }
      ImePanelMode.CLIPBOARD -> {
        ClipboardPanelContent(
          clips = state.clips,
          fg = fg,
          hint = hint,
          accent = accent,
          cardBg = cardBg,
          divider = divider,
          onInsertClip = onInsertClip,
          onTogglePin = onTogglePinClip,
          onDeleteClip = onDeleteClip,
          onClearUnpinned = onClearUnpinnedClips,
          onClose = onClosePanel,
        )
      }
      ImePanelMode.THEME -> {
        ThemeQuickPanelContent(
          selected = state.themePreset,
          fg = fg,
          hint = hint,
          accent = accent,
          cardBg = cardBg,
          onSelect = onSelectThemePreset,
          onClose = onClosePanel,
        )
      }
      ImePanelMode.LANGUAGE -> {
        LanguagePanelContent(
          activeLanguage = state.activeLanguage,
          enabledLanguages = state.enabledLanguages,
          fg = fg,
          hint = hint,
          accent = accent,
          cardBg = cardBg,
          divider = divider,
          onSelectLanguage = onSelectKeyboardLanguage,
          onToggleEnabled = onToggleLanguageEnabled,
          onShowSystemPicker = onShowSystemImePicker,
          onClose = onClosePanel,
        )
      }
      ImePanelMode.NONE, ImePanelMode.TRANSLATE -> Unit
    }
  }
}

/** Emoji grid panel organized by category with recents and quick backspace. */
@Composable
private fun EmojiPanelContent(
  recentEmojis: List<String>,
  fg: Color,
  hint: Color,
  accent: Color,
  cardBg: Color,
  onEmojiPicked: (String) -> Unit,
  onBackspaceClick: () -> Unit,
  onClose: () -> Unit,
) {
  val catRecent = stringResource(R.string.ime_emoji_recent)
  val catEmotion = stringResource(R.string.ime_emoji_emotion)
  val catGesture = stringResource(R.string.ime_emoji_gesture)
  val catLife = stringResource(R.string.ime_emoji_life)
  val catSymbol = stringResource(R.string.ime_emoji_symbol)

  val categories = remember(recentEmojis, catRecent, catEmotion, catGesture, catLife, catSymbol) {
    listOf(
      catRecent to recentEmojis.ifEmpty { listOf("😀", "😂", "❤️", "👍", "🙏", "🔥") },
      catEmotion to listOf("😀", "😃", "😄", "😁", "😅", "😂", "🤣", "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😋", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤯", "😳", "🥵", "🥶", "😱", "😨", "🤗", "🤔", "🤫", "🤭", "😴"),
      catGesture to listOf("👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "👋", "🤚", "🖐️", "✋", "👏", "🙌", "🤝", "🙏", "💪", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘"),
      catLife to listOf("☕", "🍵", "🧋", "🍺", "🍜", "🍲", "🍚", "🍞", "🥐", "🍕", "🍔", "🍟", "🍗", "🍎", "🍉", "🍓", "🥭", "🎂", "🍰", "🍫", "🌸", "🌹", "🌻", "🌿", "🍀", "🔥", "✨", "⭐", "🌟", "⚡", "🌈", "☀️", "🌙", "☔", "❄️", "🎉", "🎊", "🎁", "🎈", "🏆"),
      catSymbol to listOf("✅", "❌", "⚠️", "❗", "❓", "💯", "🔔", "📌", "📍", "📎", "📝", "📅", "⏰", "🔒", "🔑", "💡", "🔍", "📱", "💻", "⌨️", "🚀", "✈️", "🚗", "🏠", "🏢", "🇻🇳", "🇺🇸", "🇯🇵", "🇰🇷", "🇬🇧"),
    )
  }
  var selectedTab by remember { mutableIntStateOf(0) }

  Column(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 40.dp),
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(6.dp),
    ) {
      val activeEmojis = categories[selectedTab].second
      items(
        count = activeEmojis.size,
        key = { index -> "${selectedTab}_$index" },
      ) { index ->
        val emoji = activeEmojis[index]
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable { onEmojiPicked(emoji) },
          contentAlignment = Alignment.Center,
        ) {
          Text(text = emoji, fontSize = 22.sp)
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().height(38.dp).background(cardBg).padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "ABC",
        color = fg,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        modifier =
          Modifier.clip(RoundedCornerShape(8.dp))
            .clickable { onClose() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
      )
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        categories.forEachIndexed { idx, (title, _) ->
          val active = idx == selectedTab
          Text(
            text = title,
            color = if (active) accent else hint,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { selectedTab = idx }
                .padding(horizontal = 8.dp, vertical = 5.dp),
          )
        }
      }
      IconButton(onClick = onBackspaceClick, modifier = Modifier.size(36.dp)) {
        Icon(
          Icons.AutoMirrored.Filled.Backspace,
          contentDescription = stringResource(R.string.ime_clipboard_delete_cd),
          tint = fg,
          modifier = Modifier.size(17.dp),
        )
      }
    }
  }
}

/**
 * Local clipboard manager panel featuring separate Pinned and Recent sections,
 * pin badges, single-tap paste, and quick cleanup of unpinned items.
 */
@Composable
private fun ClipboardPanelContent(
  clips: List<ClipboardClipEntity>,
  fg: Color,
  hint: Color,
  accent: Color,
  cardBg: Color,
  divider: Color,
  onInsertClip: (String) -> Unit,
  onTogglePin: (Long) -> Unit,
  onDeleteClip: (Long) -> Unit,
  onClearUnpinned: () -> Unit,
  onClose: () -> Unit,
) {
  val pinnedClips = remember(clips) { clips.filter { it.isPinned } }
  val recentClips = remember(clips) { clips.filter { !it.isPinned } }

  Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.ime_clipboard_title),
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (recentClips.isNotEmpty()) {
          Text(
            text = stringResource(R.string.ime_clipboard_clear_unpinned),
            color = accent,
            fontSize = 11.5.sp,
            modifier =
              Modifier.clip(RoundedCornerShape(6.dp))
                .clickable { onClearUnpinned() }
                .padding(horizontal = 7.dp, vertical = 4.dp),
          )
        }
        Text(
          text = stringResource(R.string.ime_clipboard_back),
          color = hint,
          fontSize = 11.5.sp,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .clickable { onClose() }
              .padding(horizontal = 7.dp, vertical = 4.dp),
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    if (clips.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = stringResource(R.string.ime_clipboard_empty),
          color = hint,
          fontSize = 12.sp,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (pinnedClips.isNotEmpty()) {
          item(key = "header_pinned") {
            ClipboardSectionHeader(
              title = stringResource(R.string.ime_clipboard_pinned_section, pinnedClips.size),
              color = accent,
              pinned = true,
            )
          }
          items(pinnedClips, key = { "pin_${it.id}" }) { clip ->
            ClipboardItemRow(
              clip = clip,
              fg = fg,
              hint = hint,
              accent = accent,
              cardBg = cardBg,
              divider = divider,
              onInsertClip = onInsertClip,
              onTogglePin = onTogglePin,
              onDeleteClip = onDeleteClip,
            )
          }
        } else {
          item(key = "pin_discovery_hint") {
            Text(
              text = stringResource(R.string.ime_clipboard_pin_hint),
              color = hint,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
          }
        }

        if (recentClips.isNotEmpty()) {
          item(key = "header_recent") {
            ClipboardSectionHeader(
              title = stringResource(R.string.ime_clipboard_recent_section, recentClips.size),
              color = hint,
              pinned = false,
            )
          }
          items(recentClips, key = { "recent_${it.id}" }) { clip ->
            ClipboardItemRow(
              clip = clip,
              fg = fg,
              hint = hint,
              accent = accent,
              cardBg = cardBg,
              divider = divider,
              onInsertClip = onInsertClip,
              onTogglePin = onTogglePin,
              onDeleteClip = onDeleteClip,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ClipboardSectionHeader(
  title: String,
  color: Color,
  pinned: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (pinned) {
      Icon(
        imageVector = Icons.Filled.PushPin,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(12.dp),
      )
    }
    Text(
      text = title,
      color = color,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun ClipboardItemRow(
  clip: ClipboardClipEntity,
  fg: Color,
  hint: Color,
  accent: Color,
  cardBg: Color,
  divider: Color,
  onInsertClip: (String) -> Unit,
  onTogglePin: (Long) -> Unit,
  onDeleteClip: (Long) -> Unit,
) {
  val rowBg = if (clip.isPinned) accent.copy(alpha = 0.08f) else cardBg
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(cardBg)
        .background(rowBg)
        .border(
          width = if (clip.isPinned) 1.dp else 0.7.dp,
          color = if (clip.isPinned) accent else divider,
          shape = RoundedCornerShape(10.dp),
        )
        .clickable { onInsertClip(clip.text) }
        .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      if (clip.isPinned) {
        Surface(
          color = accent.copy(alpha = 0.16f),
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = stringResource(R.string.ime_clipboard_pinned_badge),
            color = accent,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
          )
        }
      }
      Text(
        text = clip.text,
        color = fg,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(modifier = Modifier.width(4.dp))
    Box(
      modifier =
        Modifier.size(30.dp)
          .clip(CircleShape)
          .background(if (clip.isPinned) accent.copy(alpha = 0.16f) else Color.Transparent)
          .clickable { onTogglePin(clip.id) }
          .testTag("clipboard_pin_btn_${clip.id}"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
        contentDescription =
          stringResource(
            if (clip.isPinned) R.string.ime_clipboard_unpin_cd else R.string.ime_clipboard_pin_cd
          ),
        tint = if (clip.isPinned) accent else hint,
        modifier = Modifier.size(16.dp),
      )
    }
    IconButton(
      onClick = { onDeleteClip(clip.id) },
      modifier = Modifier.size(30.dp).testTag("clipboard_delete_btn_${clip.id}"),
    ) {
      Icon(
        imageVector = Icons.Default.DeleteOutline,
        contentDescription = stringResource(R.string.ime_clipboard_delete_cd),
        tint = hint,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

/** Quick inline theme preset picker inside the IME body panel. */
@Composable
private fun ThemeQuickPanelContent(
  selected: KeyboardThemePreset,
  fg: Color,
  hint: Color,
  accent: Color,
  cardBg: Color,
  onSelect: (KeyboardThemePreset) -> Unit,
  onClose: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(text = stringResource(R.string.ime_theme_title), color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
      Text(
        text = stringResource(R.string.ime_done),
        color = accent,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .clickable { onClose() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(KeyboardThemePreset.entries, key = { it.name }) { preset ->
        val isSelected = preset == selected
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(cardBg)
              .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) accent else hint.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onSelect(preset) }
              .padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(text = preset.localizedName(), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
          if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

/** Language selector panel allowing instant switching and enabling/disabling rotation languages. */
@Composable
private fun LanguagePanelContent(
  activeLanguage: KeyboardLanguage,
  enabledLanguages: List<KeyboardLanguage>,
  fg: Color,
  hint: Color,
  accent: Color,
  cardBg: Color,
  divider: Color,
  onSelectLanguage: (KeyboardLanguage) -> Unit,
  onToggleEnabled: (KeyboardLanguage) -> Unit,
  onShowSystemPicker: () -> Unit,
  onClose: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.ime_lang_panel_title),
          color = fg,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = stringResource(R.string.ime_lang_panel_hint),
          color = hint,
          fontSize = 10.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.ime_lang_other_ime),
          color = hint,
          fontSize = 11.5.sp,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .clickable { onShowSystemPicker() }
              .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(
          text = stringResource(R.string.ime_done),
          color = accent,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .clickable { onClose() }
              .padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(KeyboardLanguage.all(), key = { it.code }) { lang ->
        val isActive = lang == activeLanguage
        val isEnabled = enabledLanguages.contains(lang)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(cardBg)
              .border(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = if (isActive) accent else divider,
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onSelectLanguage(lang) }
              .padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              color = if (isActive) accent else hint.copy(alpha = 0.16f),
              shape = RoundedCornerShape(8.dp),
            ) {
              Box(
                modifier = Modifier.size(width = 36.dp, height = 28.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = lang.shortBadge,
                  color = if (isActive) Color.White else fg,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            Column {
              Text(
                text = lang.nativeName,
                color = fg,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
              )
              Text(
                text = lang.localizedLabel(),
                color = hint,
                fontSize = 10.5.sp,
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isActive) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.ime_lang_use_hint),
                tint = accent,
                modifier = Modifier.size(17.dp),
              )
            }
            Surface(
              color = if (isEnabled) accent.copy(alpha = 0.16f) else Color.Transparent,
              shape = RoundedCornerShape(8.dp),
              modifier =
                Modifier.size(28.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .border(0.7.dp, if (isEnabled) accent else divider, RoundedCornerShape(8.dp))
                  .clickable { onToggleEnabled(lang) },
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Add,
                  contentDescription = null,
                  tint = if (isEnabled) accent else hint,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
