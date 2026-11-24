package com.ai.banphim.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.banphim.R
import com.ai.banphim.data.KeyboardThemePreset
import com.ai.banphim.ui.localizedName
import com.ai.banphim.ui.theme.AppTheme

enum class StatusTone {
  PRIMARY,
  SUCCESS,
  WARNING,
  ERROR,
  NEUTRAL,
}

/**
 * Primary surface card with crisp 1.dp border hierarchy tuned for Light, Dark, and AMOLED modes.
 */
@Composable
fun PremiumCard(
  modifier: Modifier = Modifier,
  contentPadding: Dp = 18.dp,
  highlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val ext = AppTheme.colors
  val borderColor =
    if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else ext.cardBorder
  val shape = RoundedCornerShape(18.dp)

  Surface(
    color = ext.cardSurface,
    shape = shape,
    border = BorderStroke(if (highlighted) 1.25.dp else 1.dp, borderColor),
    tonalElevation = 0.dp,
    shadowElevation = if (ext.appearanceMode == com.ai.banphim.ui.theme.AppAppearanceMode.LIGHT) 1.dp else 0.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

/**
 * Hero container using a restrained Indigo -> Electric Blue wash with crisp hairline border.
 */
@Composable
fun HeroGradientCard(
  modifier: Modifier = Modifier,
  contentPadding: Dp = 18.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val ext = AppTheme.colors
  val shape = RoundedCornerShape(20.dp)
  val brush =
    Brush.linearGradient(
      colors = listOf(ext.heroGradientStart, ext.heroGradientEnd),
    )

  Surface(
    color = Color.Transparent,
    shape = shape,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isAmoled) 0.35f else 0.22f)),
    modifier = modifier.fillMaxWidth().clip(shape),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().background(brush).padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

/**
 * Unified section header inside cards or pages with a tinted icon badge and optional trailing slot.
 */
@Composable
fun SectionHeader(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(38.dp)
          .clip(RoundedCornerShape(11.dp))
          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (trailing != null) {
      trailing()
    }
  }
}

/**
 * Reusable semantic status pill badge with high-contrast foreground/background pairing.
 */
@Composable
fun StatusPill(
  text: String,
  tone: StatusTone = StatusTone.PRIMARY,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
) {
  val ext = AppTheme.colors
  val (bg, fg) =
    when (tone) {
      StatusTone.PRIMARY ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      StatusTone.SUCCESS -> ext.successContainer to ext.onSuccessContainer
      StatusTone.WARNING -> ext.warningContainer to ext.onWarningContainer
      StatusTone.ERROR ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
      StatusTone.NEUTRAL ->
        ext.cardSurfaceElevated to MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    color = bg,
    shape = CircleShape,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = fg,
          modifier = Modifier.size(13.dp),
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Unified settings row with optional leading icon, clear typography hierarchy, and M3 Switch.
 */
@Composable
fun PremiumSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
) {
  val ext = AppTheme.colors
  val iconBg by
    animateColorAsState(
      targetValue =
        if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else ext.cardSurfaceElevated,
      label = "switchIconBg",
    )
  val iconTint by
    animateColorAsState(
      targetValue =
        if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      label = "switchIconTint",
    )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 4.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (icon != null) {
      Box(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
          checkedTrackColor = MaterialTheme.colorScheme.primary,
          uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
          uncheckedTrackColor = ext.cardSurfaceElevated,
          uncheckedBorderColor = ext.cardBorder,
        ),
    )
  }
}

/**
 * Elevated selector row used for launching pickers, bottom sheets, or sub-screens.
 */
@Composable
fun PremiumSelectorRow(
  label: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  supportingText: String? = null,
  leadingIcon: ImageVector? = null,
) {
  val ext = AppTheme.colors
  val shape = RoundedCornerShape(14.dp)

  Surface(
    color = ext.cardSurfaceElevated,
    shape = shape,
    border = BorderStroke(1.dp, ext.cardBorder),
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 54.dp)
        .clip(shape)
        .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (leadingIcon != null) {
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!supportingText.isNullOrBlank()) {
          Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Harmoniously styled text field matching the surface/border token scale.
 */
@Composable
fun PremiumTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  singleLine: Boolean = false,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else 5,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  val ext = AppTheme.colors
  val resolvedTrailing: @Composable (() -> Unit)? =
    trailingIcon
      ?: if (value.isNotEmpty() && singleLine) {
        {
          IconButton(onClick = { onValueChange("") }) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.ime_translate_clear_cd),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      } else {
        null
      }

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = label?.let { { Text(it) } },
    placeholder = placeholder?.let { { Text(it) } },
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    leadingIcon = leadingIcon,
    trailingIcon = resolvedTrailing,
    visualTransformation = visualTransformation,
    shape = RoundedCornerShape(14.dp),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ext.cardSurfaceMuted,
        unfocusedContainerColor = ext.cardSurfaceElevated.copy(alpha = 0.65f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = ext.cardBorder,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
      ),
  )
}

/** Clean inline loading banner for async operations such as model catalog refresh or AI test. */
@Composable
fun PremiumLoadingRow(
  label: String,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  Surface(
    color = ext.cardSurfaceElevated,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, ext.subtleBorder),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.2.dp,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** High-contrast inline error banner. */
@Composable
fun PremiumErrorBanner(
  message: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(18.dp).padding(top = 1.dp),
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
  }
}

/** Polished empty state placeholder for lists and search results. */
@Composable
fun PremiumEmptyState(
  icon: ImageVector,
  message: String,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  Surface(
    color = ext.cardSurfaceElevated.copy(alpha = 0.55f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, ext.subtleBorder),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * Quick Material 3 Bottom Sheet letting users switch between System Dynamic, Light, Dark,
 * AMOLED Black, and Ocean Blue right from the Top Bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceModeBottomSheet(
  selectedPreset: KeyboardThemePreset,
  onSelectPreset: (KeyboardThemePreset) -> Unit,
  onDismiss: () -> Unit,
) {
  val ext = AppTheme.colors
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ext.cardSurface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.appearance_sheet_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = stringResource(R.string.appearance_sheet_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(2.dp))

      KeyboardThemePreset.entries.forEach { preset ->
        val isSelected = preset == selectedPreset
        val icon =
          when (preset) {
            KeyboardThemePreset.DYNAMIC -> Icons.Default.BrightnessAuto
            KeyboardThemePreset.LIGHT -> Icons.Default.LightMode
            KeyboardThemePreset.DARK -> Icons.Default.DarkMode
            KeyboardThemePreset.AMOLED -> Icons.Default.NightlightRound
            KeyboardThemePreset.OCEAN -> Icons.Default.WaterDrop
          }
        Surface(
          color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            else ext.cardSurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border =
            BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) MaterialTheme.colorScheme.primary else ext.cardBorder,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 52.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable {
                onSelectPreset(preset)
                onDismiss()
              }
              .testTag("appearance_option_${preset.name.lowercase()}"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = preset.localizedName(),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
              modifier = Modifier.weight(1f),
            )
            if (isSelected) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }
  }
}
