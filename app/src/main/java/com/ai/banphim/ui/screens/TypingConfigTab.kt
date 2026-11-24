package com.ai.banphim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.banphim.R
import com.ai.banphim.data.PersonalLexiconStats
import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.KeyboardLanguage
import com.ai.banphim.ime.engine.TonePlacementStyle
import com.ai.banphim.ui.components.PremiumCard
import com.ai.banphim.ui.components.PremiumSwitchRow
import com.ai.banphim.ui.components.SectionHeader
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.localizedLabel
import com.ai.banphim.ui.theme.AppTheme
import com.ai.banphim.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

@Composable
fun KeyboardLanguagesCard(
  activeLanguage: KeyboardLanguage,
  enabledLanguages: List<KeyboardLanguage>,
  onSelectActive: (KeyboardLanguage) -> Unit,
  onToggleEnabled: (KeyboardLanguage) -> Unit,
) {
  val ext = AppTheme.colors

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Language,
      title = stringResource(R.string.lang_card_title),
      subtitle = stringResource(R.string.lang_card_desc),
      trailing = {
        StatusPill(
          text = "${enabledLanguages.size}/${KeyboardLanguage.all().size}",
          tone = StatusTone.PRIMARY,
        )
      },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      KeyboardLanguage.all().forEach { lang ->
        val isActive = lang == activeLanguage
        val isEnabled = enabledLanguages.contains(lang)
        Surface(
          color =
            if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else ext.cardSurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border =
            BorderStroke(
              width = if (isActive) 1.25.dp else 1.dp,
              color = if (isActive) MaterialTheme.colorScheme.primary else ext.subtleBorder,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable { onSelectActive(lang) },
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = isActive, onClick = { onSelectActive(lang) })
              Spacer(Modifier.width(4.dp))
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = lang.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Surface(
                    color = ext.cardSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, ext.cardBorder),
                  ) {
                    Text(
                      text = lang.shortBadge,
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = MonoFontFamily,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
                Text(
                  text = lang.localizedLabel(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Checkbox(
              checked = isEnabled,
              onCheckedChange = { onToggleEnabled(lang) },
            )
          }
        }
      }
    }
  }
}

@Composable
fun InputMethodSelectionCard(
  selectedMode: InputMethodMode,
  toneStyle: TonePlacementStyle,
  onSelectMode: (InputMethodMode) -> Unit,
  onSelectToneStyle: (TonePlacementStyle) -> Unit,
) {
  val ext = AppTheme.colors

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Keyboard,
      title = stringResource(R.string.vietnamese_method_title),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(
        InputMethodMode.TELEX to stringResource(R.string.method_telex_desc),
        InputMethodMode.VNI to stringResource(R.string.method_vni_desc),
        InputMethodMode.OFF to stringResource(R.string.method_off_desc),
      ).forEach { (mode, desc) ->
        val isSelected = selectedMode == mode
        Surface(
          color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else ext.cardSurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border =
            BorderStroke(
              width = if (isSelected) 1.25.dp else 1.dp,
              color = if (isSelected) MaterialTheme.colorScheme.primary else ext.subtleBorder,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable { onSelectMode(mode) },
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = isSelected, onClick = { onSelectMode(mode) })
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = mode.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    HorizontalDivider(color = ext.subtleBorder)

    Text(
      text = stringResource(R.string.tone_style_title),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ToneStyleChip(
        label = stringResource(R.string.tone_modern_chip),
        selected = toneStyle == TonePlacementStyle.MODERN,
        onClick = { onSelectToneStyle(TonePlacementStyle.MODERN) },
        modifier = Modifier.weight(1f),
      )
      ToneStyleChip(
        label = stringResource(R.string.tone_classic_chip),
        selected = toneStyle == TonePlacementStyle.CLASSIC,
        onClick = { onSelectToneStyle(TonePlacementStyle.CLASSIC) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ToneStyleChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  Surface(
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else ext.cardSurfaceElevated,
    shape = RoundedCornerShape(12.dp),
    border =
      BorderStroke(
        width = if (selected) 1.25.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else ext.cardBorder,
      ),
    modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
  ) {
    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun TypingBehaviorCard(
  autoCorrect: Boolean,
  showSuggestions: Boolean,
  showNumberRow: Boolean,
  floatingMode: Boolean,
  haptic: Boolean,
  sound: Boolean,
  heightScale: Float,
  onAutoCorrectChange: (Boolean) -> Unit,
  onShowSuggestionsChange: (Boolean) -> Unit,
  onShowNumberRowChange: (Boolean) -> Unit,
  onFloatingModeChange: (Boolean) -> Unit,
  onHapticChange: (Boolean) -> Unit,
  onSoundChange: (Boolean) -> Unit,
  onHeightScaleChange: (Float) -> Unit,
) {
  val ext = AppTheme.colors

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Tune,
      title = stringResource(R.string.behavior_title),
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      PremiumSwitchRow(
        title = stringResource(R.string.setting_suggestions_title),
        subtitle = stringResource(R.string.setting_suggestions_desc),
        checked = showSuggestions,
        onCheckedChange = onShowSuggestionsChange,
        icon = Icons.Default.Lightbulb,
      )
      PremiumSwitchRow(
        title = stringResource(R.string.setting_autocorrect_title),
        subtitle = stringResource(R.string.setting_autocorrect_desc),
        checked = autoCorrect,
        onCheckedChange = onAutoCorrectChange,
        icon = Icons.Default.AutoFixHigh,
      )
      PremiumSwitchRow(
        title = stringResource(R.string.setting_number_row_title),
        subtitle = stringResource(R.string.setting_number_row_desc),
        checked = showNumberRow,
        onCheckedChange = onShowNumberRowChange,
        icon = Icons.Default.Dialpad,
      )
      PremiumSwitchRow(
        title = stringResource(R.string.setting_floating_title),
        subtitle = stringResource(R.string.setting_floating_desc),
        checked = floatingMode,
        onCheckedChange = onFloatingModeChange,
        icon = Icons.Default.PictureInPictureAlt,
      )
      PremiumSwitchRow(
        title = stringResource(R.string.setting_haptic_title),
        subtitle = stringResource(R.string.setting_haptic_desc),
        checked = haptic,
        onCheckedChange = onHapticChange,
        icon = Icons.Default.Vibration,
      )
      PremiumSwitchRow(
        title = stringResource(R.string.setting_sound_title),
        subtitle = stringResource(R.string.setting_sound_desc),
        checked = sound,
        onCheckedChange = onSoundChange,
        icon = Icons.Default.VolumeUp,
      )
    }

    HorizontalDivider(color = ext.subtleBorder)

    Surface(
      color = ext.cardSurfaceElevated,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, ext.subtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.setting_height_label, (heightScale * 100).roundToInt()),
            style = MaterialTheme.typography.titleSmall,
          )
          if (heightScale != 1.0f) {
            TextButton(onClick = { onHeightScaleChange(1.0f) }) {
              Text(stringResource(R.string.reset_height_btn))
            }
          }
        }
        Slider(
          value = heightScale,
          onValueChange = onHeightScaleChange,
          valueRange = 0.85f..1.20f,
          steps = 6,
          colors =
            SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary,
              inactiveTrackColor = ext.cardBorder,
            ),
        )
      }
    }
  }
}

@Composable
fun PersonalizedLearningCard(
  personalizedEnabled: Boolean,
  stats: PersonalLexiconStats,
  onTogglePersonalized: (Boolean) -> Unit,
  onClearLearnedWords: () -> Unit,
) {
  val ext = AppTheme.colors
  var showClearDialog by remember { mutableStateOf(false) }

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Psychology,
      title = stringResource(R.string.personalization_card_title),
      subtitle = stringResource(R.string.personalization_card_desc),
    )

    PremiumSwitchRow(
      title = stringResource(R.string.personalization_toggle_title),
      subtitle = stringResource(R.string.personalization_toggle_desc),
      checked = personalizedEnabled,
      onCheckedChange = onTogglePersonalized,
      icon = Icons.Default.Psychology,
    )

    StatusPill(
      text =
        stringResource(
          if (stats.isEncryptedAtRest) {
            R.string.personalization_stats_encrypted
          } else {
            R.string.personalization_stats_local
          },
          stats.wordCount,
          stats.bigramCount,
        ),
      tone = if (stats.isEncryptedAtRest) StatusTone.SUCCESS else StatusTone.PRIMARY,
      leadingIcon = Icons.Default.Lock,
    )

    Surface(
      color = ext.cardSurfaceElevated,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, ext.subtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = stringResource(R.string.personalization_privacy_rules),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
      )
    }

    OutlinedButton(
      onClick = { showClearDialog = true },
      enabled = stats.wordCount > 0 || stats.bigramCount > 0,
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
      Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.personalization_clear_btn))
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      title = { Text(stringResource(R.string.personalization_clear_dialog_title)) },
      text = {
        Text(
          stringResource(
            R.string.personalization_clear_dialog_body,
            stats.wordCount,
            stats.bigramCount,
          )
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onClearLearnedWords()
            showClearDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.personalization_clear_confirm), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text(stringResource(R.string.privacy_close))
        }
      },
    )
  }
}
