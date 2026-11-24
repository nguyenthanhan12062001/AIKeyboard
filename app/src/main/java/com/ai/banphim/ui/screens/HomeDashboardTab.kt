package com.ai.banphim.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.banphim.R
import com.ai.banphim.data.ClipboardClipEntity
import com.ai.banphim.data.KeyboardSettings
import com.ai.banphim.ime.engine.InputMethodMode
import com.ai.banphim.ime.engine.TonePlacementStyle
import com.ai.banphim.ui.ImeSetupStatus
import com.ai.banphim.ui.components.HeroGradientCard
import com.ai.banphim.ui.components.PremiumCard
import com.ai.banphim.ui.components.PremiumEmptyState
import com.ai.banphim.ui.components.PremiumTextField
import com.ai.banphim.ui.components.SectionHeader
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.localizedName
import com.ai.banphim.ui.theme.AppTheme

@Composable
fun ImeSetupHeroCard(
  imeStatus: ImeSetupStatus,
  onRefresh: () -> Unit,
) {
  val context = LocalContext.current
  val ext = AppTheme.colors
  val completedSteps = (if (imeStatus.isEnabled) 1 else 0) + (if (imeStatus.isSelectedDefault) 1 else 0)
  val isFullyReady = completedSteps == 2

  HeroGradientCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(
          text =
            if (isFullyReady) stringResource(R.string.setup_ready_badge)
            else stringResource(R.string.setup_pending_badge),
          tone = if (isFullyReady) StatusTone.SUCCESS else StatusTone.WARNING,
          leadingIcon = if (isFullyReady) Icons.Default.CheckCircle else Icons.Default.Keyboard,
        )
        Text(
          text = stringResource(R.string.setup_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      IconButton(
        onClick = onRefresh,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(ext.cardSurface.copy(alpha = 0.7f)),
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = stringResource(R.string.setup_refresh_cd),
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }

    LinearProgressIndicator(
      progress = { completedSteps / 2f },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
      color = if (isFullyReady) ext.success else MaterialTheme.colorScheme.primary,
      trackColor = ext.cardSurface.copy(alpha = 0.65f),
      strokeCap = StrokeCap.Round,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SetupStepRow(done = imeStatus.isEnabled, label = stringResource(R.string.setup_step1))
      SetupStepRow(done = imeStatus.isSelectedDefault, label = stringResource(R.string.setup_step2))
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = {
          context.startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_ime_settings_btn"),
      ) {
        Text(stringResource(R.string.setup_open_ime), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Button(
        onClick = {
          val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
          imm?.showInputMethodPicker()
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pick_ime_btn"),
      ) {
        Text(stringResource(R.string.setup_pick_ime), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun SetupStepRow(done: Boolean, label: String) {
  val ext = AppTheme.colors
  Surface(
    color = ext.cardSurface.copy(alpha = 0.72f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, ext.subtleBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (done) ext.success else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (done) FontWeight.Medium else FontWeight.Normal,
        color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun PlaygroundStudioCard(
  text: String,
  onTextChange: (String) -> Unit,
  inputMode: InputMethodMode,
) {
  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Keyboard,
      title = stringResource(R.string.playground_title),
      subtitle = stringResource(R.string.playground_desc, inputMode.name),
      trailing = {
        StatusPill(
          text = stringResource(R.string.playground_char_count, text.length),
          tone = if (text.isNotEmpty()) StatusTone.PRIMARY else StatusTone.NEUTRAL,
        )
      },
    )
    PremiumTextField(
      value = text,
      onValueChange = onTextChange,
      modifier = Modifier.testTag("test_playground_input"),
      label = stringResource(R.string.playground_hint),
      minLines = 2,
      maxLines = 4,
    )
  }
}

@Composable
fun StatusOverviewCard(
  settings: KeyboardSettings,
  clipCount: Int,
  freeModelCount: Int,
  onClearUnpinned: () -> Unit,
) {
  val ext = AppTheme.colors
  val toneStr =
    if (settings.tonePlacementStyle == TonePlacementStyle.MODERN) {
      stringResource(R.string.status_tone_modern)
    } else {
      stringResource(R.string.status_tone_classic)
    }
  val floatingStr =
    if (settings.floatingMode) {
      stringResource(R.string.status_floating_on)
    } else {
      stringResource(R.string.status_floating_off)
    }

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Tune,
      title = stringResource(R.string.status_title),
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatusTile(
        icon = Icons.Default.Language,
        text = stringResource(R.string.status_keyboard_lang, settings.activeLanguage.nativeName, settings.enabledLanguages.size).removePrefix("• "),
        modifier = Modifier.weight(1f),
      )
      StatusTile(
        icon = Icons.Default.Keyboard,
        text = stringResource(R.string.status_vietnamese_mode, settings.inputMethodMode.name, toneStr).removePrefix("• "),
        modifier = Modifier.weight(1f),
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatusTile(
        icon = Icons.Default.Palette,
        text = "${settings.themePreset.localizedName()} • $floatingStr",
        modifier = Modifier.weight(1f),
      )
      StatusTile(
        icon = Icons.Default.AutoAwesome,
        text = stringResource(R.string.status_ai_info, freeModelCount).removePrefix("• "),
        modifier = Modifier.weight(1f),
      )
    }

    HorizontalDivider(color = ext.subtleBorder)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.status_clipboard_count, clipCount).removePrefix("• "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      if (clipCount > 0) {
        OutlinedButton(
          onClick = onClearUnpinned,
          shape = RoundedCornerShape(10.dp),
        ) {
          Text(stringResource(R.string.status_clear_unpinned))
        }
      }
    }
  }
}

@Composable
private fun StatusTile(
  icon: ImageVector,
  text: String,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  Surface(
    color = ext.cardSurfaceElevated,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, ext.subtleBorder),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp).padding(top = 2.dp),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun ClipboardManagerCard(
  clips: List<ClipboardClipEntity>,
  onAddPinned: (String) -> Unit,
  onTogglePin: (Long) -> Unit,
  onDeleteClip: (Long) -> Unit,
  onClearUnpinned: () -> Unit,
) {
  val ext = AppTheme.colors
  var newClipText by remember { mutableStateOf("") }
  val pinnedCount = remember(clips) { clips.count { it.isPinned } }
  val unpinnedCount = clips.size - pinnedCount

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.ContentPaste,
      title = stringResource(R.string.clipboard_card_title),
      trailing = {
        StatusPill(
          text = stringResource(R.string.clipboard_pinned_count, pinnedCount, clips.size),
          tone = if (pinnedCount > 0) StatusTone.PRIMARY else StatusTone.NEUTRAL,
        )
      },
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.weight(1f)) {
        PremiumTextField(
          value = newClipText,
          onValueChange = { newClipText = it },
          label = stringResource(R.string.clipboard_add_hint),
          modifier = Modifier.testTag("clipboard_new_input"),
          singleLine = true,
        )
      }
      FilledTonalButton(
        onClick = {
          if (newClipText.isNotBlank()) {
            onAddPinned(newClipText)
            newClipText = ""
          }
        },
        enabled = newClipText.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.heightIn(min = 52.dp).testTag("clipboard_add_pin_btn"),
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.clipboard_add_pin_btn), maxLines = 1)
      }
    }

    if (clips.isEmpty()) {
      PremiumEmptyState(
        icon = Icons.Default.ContentPaste,
        message = stringResource(R.string.clipboard_empty_settings),
      )
    } else {
      val displayedClips = remember(clips) { clips.take(30) }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        displayedClips.forEach { clip ->
          key(clip.id) {
            Surface(
              color =
                if (clip.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                else ext.cardSurfaceElevated,
              shape = RoundedCornerShape(14.dp),
              border =
                BorderStroke(
                  width = if (clip.isPinned) 1.25.dp else 1.dp,
                  color =
                    if (clip.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else ext.subtleBorder,
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (clip.isPinned) {
                  StatusPill(
                    text = stringResource(R.string.ime_clipboard_pinned_badge),
                    tone = StatusTone.PRIMARY,
                    leadingIcon = Icons.Filled.PushPin,
                  )
                }
                Text(
                  text = clip.text,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { onTogglePin(clip.id) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_clipboard_pin_${clip.id}"),
              ) {
                Icon(
                  imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                  contentDescription =
                    stringResource(
                      if (clip.isPinned) R.string.ime_clipboard_unpin_cd else R.string.ime_clipboard_pin_cd
                    ),
                  tint =
                    if (clip.isPinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { onDeleteClip(clip.id) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_clipboard_delete_${clip.id}"),
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = stringResource(R.string.ime_clipboard_delete_cd),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          }
        }
      }
      if (unpinnedCount > 0) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          OutlinedButton(
            onClick = onClearUnpinned,
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(stringResource(R.string.status_clear_unpinned))
          }
        }
      }
    }
  }
}
