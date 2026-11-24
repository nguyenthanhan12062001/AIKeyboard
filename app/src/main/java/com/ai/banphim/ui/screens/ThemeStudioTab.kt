package com.ai.banphim.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.banphim.R
import com.ai.banphim.data.KeyboardThemePreset
import com.ai.banphim.ime.view.KeyboardThemePalette
import com.ai.banphim.ui.SettingsViewModel
import com.ai.banphim.ui.components.HeroGradientCard
import com.ai.banphim.ui.components.SectionHeader
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.discovery.DiscoveryTargetId
import com.ai.banphim.ui.discovery.DiscoveryTargetRegistry
import com.ai.banphim.ui.discovery.discoveryTarget
import com.ai.banphim.ui.localizedName
import com.ai.banphim.ui.theme.AppTheme

data class CustomizerState(
  val type: String, // "COLOR" or "PHOTO"
  val value: String,
  val opacity: Float = 0.7f,
)

@Composable
fun ThemeStudioHeaderCard(selectedPreset: KeyboardThemePreset) {
  HeroGradientCard {
    SectionHeader(
      icon = Icons.Default.Palette,
      title = stringResource(R.string.theme_choose_title),
      subtitle = stringResource(R.string.appearance_sheet_desc),
      trailing = {
        StatusPill(
          text = selectedPreset.localizedName(),
          tone = StatusTone.PRIMARY,
        )
      },
    )
  }
}

/**
 * Interactive keyboard & companion theme card displaying a miniature multi-row keyboard preview
 * that reflects live dynamic/light/dark/AMOLED/ocean palette tokens.
 */
@Composable
fun ThemePresetStudioItem(
  preset: KeyboardThemePreset,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  val uiMode = LocalConfiguration.current.uiMode
  val ext = AppTheme.colors
  val preview = remember(preset, uiMode) { KeyboardThemePalette.resolve(context, preset) }

  val modeIcon =
    when (preset) {
      KeyboardThemePreset.DYNAMIC -> Icons.Default.BrightnessAuto
      KeyboardThemePreset.LIGHT -> Icons.Default.LightMode
      KeyboardThemePreset.DARK -> Icons.Default.DarkMode
      KeyboardThemePreset.AMOLED -> Icons.Default.NightlightRound
      KeyboardThemePreset.OCEAN -> Icons.Default.WaterDrop
    }

  val shape = RoundedCornerShape(18.dp)
  Surface(
    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else ext.cardSurface,
    shape = shape,
    border =
      BorderStroke(
        width = if (selected) 1.75.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else ext.cardBorder,
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clip(shape)
        .clickable(onClick = onClick)
        .testTag("theme_preset_${preset.name.lowercase()}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Realistic mini-keyboard preview
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(preview.keyboardBackground))
            .border(1.dp, Color(preview.keyDivider), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        // Mini toolbar strip
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(Color(preview.toolbarBackground))
              .padding(horizontal = 6.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(width = 22.dp, height = 8.dp)
                .clip(CircleShape)
                .background(Color(preview.accent)),
          )
          repeat(4) {
            Box(
              modifier =
                Modifier.size(8.dp)
                  .clip(CircleShape)
                  .background(Color(preview.keyHintText).copy(alpha = 0.45f)),
            )
          }
          Spacer(Modifier.weight(1f))
          Box(
            modifier =
              Modifier.size(width = 16.dp, height = 6.dp)
                .clip(CircleShape)
                .background(Color(preview.keyHintText).copy(alpha = 0.35f)),
          )
        }

        // Top letter row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          repeat(8) {
            Box(
              modifier =
                Modifier.weight(1f)
                  .height(14.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(Color(preview.keyBackground)),
            )
          }
        }

        // Bottom space + enter row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.weight(1.2f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(preview.specialKeyBackground)),
          )
          Box(
            modifier =
              Modifier.weight(3.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(preview.keyBackground)),
          )
          Box(
            modifier =
              Modifier.weight(1.4f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(preview.enterKeyBackground)),
          )
        }
      }

      // Label + radio / active check
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                  if (selected) MaterialTheme.colorScheme.primaryContainer
                  else ext.cardSurfaceElevated
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = modeIcon,
              contentDescription = null,
              tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
          Text(
            text = preset.localizedName(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (selected) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.ime_lang_use_hint),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
        } else {
          RadioButton(selected = false, onClick = onClick)
        }
      }
    }
  }
}

/**
 * Color Swatch item representing color background themes.
 */
@Composable
fun ColorSwatchStudioItem(
  colorInt: Int,
  name: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val ext = AppTheme.colors
  val isDarkColor = (android.graphics.Color.red(colorInt) * 0.299 +
    android.graphics.Color.green(colorInt) * 0.587 +
    android.graphics.Color.blue(colorInt) * 0.114) < 128

  val shape = RoundedCornerShape(16.dp)
  Surface(
    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else ext.cardSurface,
    shape = shape,
    border = BorderStroke(
      width = if (selected) 2.dp else 1.dp,
      color = if (selected) MaterialTheme.colorScheme.primary else ext.cardBorder,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(colorInt))
          .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
      ) {
        if (selected) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (isDarkColor) Color.White else Color.Black,
            modifier = Modifier.size(24.dp),
          )
        }
      }
      Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Custom Theme Preview & Customization Dialog (Preview & Apply flow).
 */
@Composable
fun ThemeCustomizerDialog(
  initialType: String, // "COLOR" or "PHOTO"
  initialValue: String,
  initialOpacity: Float,
  onDismiss: () -> Unit,
  onApply: (type: String, value: String, opacity: Float) -> Unit,
) {
  var opacity by remember { mutableStateOf(initialOpacity) }
  val context = LocalContext.current

  val photoBitmap by produceState<Bitmap?>(initialValue = null, initialType, initialValue) {
    value =
      if (initialType == "PHOTO") {
        withContext(Dispatchers.IO) { decodeDownsampledPreview(context, initialValue, 640) }
      } else {
        null
      }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = stringResource(R.string.create_theme_title))
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Live Preview Box simulating keyboard surface
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
              if (initialType == "COLOR") Color(initialValue.toIntOrNull() ?: android.graphics.Color.DKGRAY)
              else Color.DarkGray
            ),
          contentAlignment = Alignment.Center,
        ) {
          val previewBmp = photoBitmap
          if (initialType == "PHOTO" && previewBmp != null) {
            androidx.compose.foundation.Image(
              bitmap = previewBmp.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp)),
              contentScale = androidx.compose.ui.layout.ContentScale.Crop,
              alpha = opacity,
            )
          }
          // Mini preview keyboard keys layout
          Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              repeat(7) {
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = Color.White.copy(alpha = 0.85f),
                  modifier = Modifier.size(width = 28.dp, height = 22.dp),
                ) {}
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(width = 60.dp, height = 24.dp),
              ) {}
              Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(width = 120.dp, height = 24.dp),
              ) {}
            }
          }
        }

        // Opacity slider if photo background
        if (initialType == "PHOTO") {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(R.string.opacity_label, (opacity * 100).toInt()),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Slider(
              value = opacity,
              onValueChange = { opacity = it },
              valueRange = 0.1f..1.0f,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onApply(initialType, initialValue, opacity)
        },
      ) {
        Text(stringResource(R.string.theme_apply_btn))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.theme_cancel_btn))
      }
    },
  )
}

/**
 * LazyListScope extension rendering the full Theme Studio tab content.
 */
fun LazyListScope.themeStudioTabContent(
  settings: com.ai.banphim.data.KeyboardSettings,
  viewModel: SettingsViewModel,
  useTwoColumns: Boolean,
  targetRegistry: DiscoveryTargetRegistry,
  onOpenCustomizer: (CustomizerState) -> Unit,
  onLaunchPhotoPicker: () -> Unit,
) {
  item {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .discoveryTarget(DiscoveryTargetId.THEME_STUDIO, targetRegistry),
    ) {
      ThemeStudioHeaderCard(selectedPreset = settings.themePreset)
    }
  }

  // 2.1. Giao diện của tôi (My Themes)
  item {
    SectionHeader(
      icon = Icons.Default.Collections,
      title = stringResource(R.string.my_themes_title),
      subtitle = "",
    )
  }
  item {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        color = AppTheme.colors.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
        modifier = Modifier
          .size(100.dp)
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onLaunchPhotoPicker),
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.create_theme_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }

  // 2.2. Giao diện mặc định (Default Themes)
  item(key = "default_themes_header") {
    SectionHeader(
      icon = Icons.Default.Palette,
      title = stringResource(R.string.default_themes_title),
      subtitle = "",
    )
  }
  if (useTwoColumns) {
    items(PRESET_PAIRS, key = { pair -> pair.joinToString("_") { it.name } }) { pair ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        pair.forEach { preset ->
          Box(modifier = Modifier.weight(1f)) {
            ThemePresetStudioItem(
              preset = preset,
              selected = settings.activeThemeType == "PRESET" && preset == settings.themePreset,
              onClick = { viewModel.updateThemePreset(preset) },
            )
          }
        }
        if (pair.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  } else {
    items(KeyboardThemePreset.entries, key = { it.name }) { preset ->
      ThemePresetStudioItem(
        preset = preset,
        selected = settings.activeThemeType == "PRESET" && preset == settings.themePreset,
        onClick = { viewModel.updateThemePreset(preset) },
      )
    }
  }

  // 2.3. Màu sắc (Colors)
  item(key = "colors_header") {
    SectionHeader(
      icon = Icons.Default.ColorLens,
      title = stringResource(R.string.colors_section_title),
      subtitle = "",
    )
  }
  if (useTwoColumns) {
    items(COLOR_SWATCH_TRIOS, key = { trio -> trio.joinToString("_") { it.first.toString() } }) { trio ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        trio.forEach { (colorInt, name, _) ->
          Box(modifier = Modifier.weight(1f)) {
            ColorSwatchStudioItem(
              colorInt = colorInt,
              name = name,
              selected =
                settings.activeThemeType == "COLOR" &&
                  settings.activeThemeValue == colorInt.toString(),
              onClick = {
                onOpenCustomizer(CustomizerState("COLOR", colorInt.toString(), 1.0f))
              },
            )
          }
        }
        repeat(3 - trio.size) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  } else {
    items(COLOR_SWATCH_PAIRS, key = { pair -> pair.joinToString("_") { it.first.toString() } }) { pair ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        pair.forEach { (colorInt, name, _) ->
          Box(modifier = Modifier.weight(1f)) {
            ColorSwatchStudioItem(
              colorInt = colorInt,
              name = name,
              selected =
                settings.activeThemeType == "COLOR" &&
                  settings.activeThemeValue == colorInt.toString(),
              onClick = {
                onOpenCustomizer(CustomizerState("COLOR", colorInt.toString(), 1.0f))
              },
            )
          }
        }
        if (pair.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }

  // 2.4. Ảnh nền cá nhân (Personal Photo Background)
  item(key = "photo_bg_header") {
    SectionHeader(
      icon = Icons.Default.Image,
      title = stringResource(R.string.photo_background_title),
      subtitle = "",
    )
  }
  item(key = "photo_bg_button") {
    Button(
      onClick = onLaunchPhotoPicker,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(Icons.Default.PhotoLibrary, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(stringResource(R.string.pick_photo_btn))
    }
  }
}

private val PRESET_PAIRS = KeyboardThemePreset.entries.chunked(2)

private val COLOR_SWATCHES = listOf(
  Triple(android.graphics.Color.WHITE, "Trắng", false),
  Triple(0xFFECEFF4.toInt(), "Xám sáng", false),
  Triple(0xFF2E3136.toInt(), "Xám đậm", true),
  Triple(android.graphics.Color.BLACK, "Đen", true),
  Triple(0xFFD32F2F.toInt(), "Đỏ", true),
  Triple(0xFF388E3C.toInt(), "Xanh lá", true),
  Triple(0xFF1A73E8.toInt(), "Xanh dương", true),
  Triple(0xFF00796B.toInt(), "Xanh ngọc", true),
  Triple(0xFF00ACC1.toInt(), "Xanh cyan", true),
)

private val COLOR_SWATCH_TRIOS = COLOR_SWATCHES.chunked(3)
private val COLOR_SWATCH_PAIRS = COLOR_SWATCHES.chunked(2)

private fun decodeDownsampledPreview(
  context: android.content.Context,
  uriString: String,
  maxDimPx: Int,
): Bitmap? {
  return runCatching {
    val uri = Uri.parse(uriString)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, bounds)
    }
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimPx || bounds.outHeight / sampleSize > maxDimPx) {
      sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, decodeOpts)
    }
  }.getOrNull()
}
