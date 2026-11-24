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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.banphim.BuildConfig
import com.ai.banphim.R
import com.ai.banphim.ai.ModelCatalogRepository
import com.ai.banphim.ai.SupportedLanguage
import com.ai.banphim.ai.TranslationResult
import com.ai.banphim.data.AppLanguageOption
import com.ai.banphim.data.TranslationMode
import com.ai.banphim.ui.AiStatusUiState
import com.ai.banphim.ui.components.PremiumCard
import com.ai.banphim.ui.components.PremiumEmptyState
import com.ai.banphim.ui.components.PremiumErrorBanner
import com.ai.banphim.ui.components.PremiumLoadingRow
import com.ai.banphim.ui.components.PremiumSelectorRow
import com.ai.banphim.ui.components.PremiumSwitchRow
import com.ai.banphim.ui.components.PremiumTextField
import com.ai.banphim.ui.components.SectionHeader
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.localizedLabel
import com.ai.banphim.ui.localizedName
import com.ai.banphim.ui.theme.AppTheme
import com.ai.banphim.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LangSheetTarget { NONE, SOURCE, TARGET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageCard(
  selectedCode: String,
  onSelectCode: (String) -> Unit,
) {
  val ext = AppTheme.colors
  val currentOption = remember(selectedCode) { AppLanguageOption.fromTag(selectedCode) }
  var showSheet by rememberSaveable { mutableStateOf(false) }

  val displayText =
    if (currentOption == AppLanguageOption.SYSTEM) {
      currentOption.localizedLabel()
    } else {
      "${currentOption.nativeName} • ${currentOption.localizedLabel()}"
    }

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Language,
      title = stringResource(R.string.app_lang_card_title),
      subtitle = stringResource(R.string.app_lang_card_desc),
    )

    PremiumSelectorRow(
      label = stringResource(R.string.app_lang_current),
      value = displayText,
      onClick = { showSheet = true },
      modifier = Modifier.testTag("app_language_picker_btn"),
    )
  }

  if (showSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showSheet = false },
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
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(R.string.app_lang_dialog_title),
          style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(AppLanguageOption.entries, key = { it.name }) { option ->
            val selected = option == currentOption
            Surface(
              color =
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else ext.cardSurfaceElevated,
              shape = RoundedCornerShape(14.dp),
              border =
                BorderStroke(
                  width = if (selected) 1.25.dp else 1.dp,
                  color = if (selected) MaterialTheme.colorScheme.primary else ext.subtleBorder,
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 52.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    onSelectCode(option.tag)
                    showSheet = false
                  }
                  .testTag("app_language_option_${option.name.lowercase()}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = selected,
                  onClick = {
                    onSelectCode(option.tag)
                    showSheet = false
                  },
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (option == AppLanguageOption.SYSTEM) option.localizedLabel() else option.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  )
                  if (option != AppLanguageOption.SYSTEM) {
                    Text(
                      text = option.localizedLabel(),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                if (selected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private data class ModeProsConsSpec(
  val mode: TranslationMode,
  val icon: ImageVector,
  val titleRes: Int,
  val prosRes: Int,
  val consRes: Int,
)

@Composable
fun TranslationModeCard(
  selectedMode: TranslationMode,
  aiStatus: AiStatusUiState,
  onSelectMode: (TranslationMode) -> Unit,
  onDownloadPack: (String) -> Unit,
  onDeletePack: (String) -> Unit,
) {
  val ext = AppTheme.colors
  var packsExpanded by rememberSaveable { mutableStateOf(selectedMode != TranslationMode.ONLINE_AI) }

  val modeSpecs =
    remember {
      listOf(
        ModeProsConsSpec(
          mode = TranslationMode.AUTO_HYBRID,
          icon = Icons.Default.AutoAwesome,
          titleRes = R.string.mode_hybrid_title,
          prosRes = R.string.mode_hybrid_pros,
          consRes = R.string.mode_hybrid_cons,
        ),
        ModeProsConsSpec(
          mode = TranslationMode.OFFLINE_MLKIT,
          icon = Icons.Default.WifiOff,
          titleRes = R.string.mode_offline_title,
          prosRes = R.string.mode_offline_pros,
          consRes = R.string.mode_offline_cons,
        ),
        ModeProsConsSpec(
          mode = TranslationMode.ONLINE_AI,
          icon = Icons.Default.Cloud,
          titleRes = R.string.mode_online_title,
          prosRes = R.string.mode_online_pros,
          consRes = R.string.mode_online_cons,
        ),
      )
    }

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Translate,
      title = stringResource(R.string.translation_mode_section_title),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      modeSpecs.forEach { spec ->
        val selected = spec.mode == selectedMode
        Surface(
          color =
            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else ext.cardSurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border =
            BorderStroke(
              width = if (selected) 1.4.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else ext.subtleBorder,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable {
                onSelectMode(spec.mode)
                if (spec.mode == TranslationMode.OFFLINE_MLKIT) {
                  packsExpanded = true
                }
              }
              .testTag("translation_mode_${spec.mode.name.lowercase()}"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              RadioButton(
                selected = selected,
                onClick = {
                  onSelectMode(spec.mode)
                  if (spec.mode == TranslationMode.OFFLINE_MLKIT) {
                    packsExpanded = true
                  }
                },
              )
              Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
              )
              Text(
                text = stringResource(spec.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
            }

            // Pros note
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = ext.success,
                modifier = Modifier.padding(top = 2.dp).size(15.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${stringResource(R.string.translation_mode_pros_label)} ${stringResource(spec.prosRes)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }

            // Cons note
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.padding(top = 2.dp).size(15.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${stringResource(R.string.translation_mode_cons_label)} ${stringResource(spec.consRes)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    HorizontalDivider(color = ext.subtleBorder)

    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .clickable { packsExpanded = !packsExpanded }
          .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.offline_packs_title),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text = stringResource(R.string.offline_packs_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusPill(
        text = "${aiStatus.downloadedOfflineLangs.size}/${SupportedLanguage.TARGET_LANGUAGES.size}",
        tone = if (aiStatus.downloadedOfflineLangs.size >= 2) StatusTone.SUCCESS else StatusTone.NEUTRAL,
      )
    }

    if (aiStatus.offlinePackError != null) {
      PremiumErrorBanner(message = aiStatus.offlinePackError)
    }

    if (packsExpanded) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SupportedLanguage.TARGET_LANGUAGES.forEach { lang ->
          val isDownloaded = aiStatus.downloadedOfflineLangs.contains(lang.code)
          val isDownloading = aiStatus.downloadingOfflineLangCode == lang.code
          Surface(
            color = ext.cardSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ext.subtleBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${lang.localizedName()} (${lang.nativeName})",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    if (isDownloaded) stringResource(R.string.offline_pack_downloaded)
                    else stringResource(R.string.offline_pack_size_hint),
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isDownloaded) ext.success
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              when {
                isDownloading -> {
                  CircularProgressIndicator(
                    modifier = Modifier.padding(8.dp).size(18.dp),
                    strokeWidth = 2.dp,
                  )
                }
                isDownloaded -> {
                  IconButton(
                    onClick = { onDeletePack(lang.code) },
                    modifier = Modifier.testTag("delete_offline_pack_${lang.code}"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = stringResource(R.string.offline_pack_delete_cd),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                else -> {
                  FilledTonalButton(
                    onClick = { onDownloadPack(lang.code) },
                    enabled = aiStatus.downloadingOfflineLangCode == null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("download_offline_pack_${lang.code}"),
                  ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.offline_pack_download_btn))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGatewayCard(
  aiStatus: AiStatusUiState,
  liveTranslateEnabled: Boolean,
  preferredModelId: String,
  autoSwitchOnLimit: Boolean,
  sourceCode: String,
  targetCode: String,
  onLiveTranslateChange: (Boolean) -> Unit,
  onSelectModel: (String) -> Unit,
  onAutoSwitchChange: (Boolean) -> Unit,
  onSourceSelected: (String) -> Unit,
  onTargetSelected: (String) -> Unit,
  onSwapLanguages: () -> Unit,
  onRefreshCatalog: () -> Unit,
  onRunQuickTest: (String) -> Unit,
) {
  val ext = AppTheme.colors
  var langSheetTarget by remember { mutableStateOf(LangSheetTarget.NONE) }
  var showModelSheet by rememberSaveable { mutableStateOf(false) }
  val defaultSample = stringResource(R.string.ai_test_sample_default)
  var testInput by remember(defaultSample) { mutableStateOf(defaultSample) }

  val currentLocale = Locale.getDefault()
  val fallbackUpdated = stringResource(R.string.ai_updated_fallback)
  val updatedStr =
    remember(aiStatus.catalog.updatedAtMs, currentLocale, fallbackUpdated) {
      if (aiStatus.catalog.updatedAtMs > 0L) {
        SimpleDateFormat("HH:mm dd/MM", currentLocale).format(Date(aiStatus.catalog.updatedAtMs))
      } else {
        fallbackUpdated
      }
    }

  val selectedModelInfo =
    remember(preferredModelId, aiStatus.catalog.models) {
      aiStatus.catalog.models.firstOrNull { it.id == preferredModelId }
    }
  val isAutoSelected = preferredModelId == ModelCatalogRepository.AUTO_MODEL_ID

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.AutoAwesome,
      title = stringResource(R.string.ai_card_title),
      subtitle = stringResource(R.string.ai_card_subtitle),
      trailing = {
        FilledTonalButton(
          onClick = onRefreshCatalog,
          enabled = !aiStatus.isRefreshingCatalog,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("refresh_kilo_catalog_btn"),
        ) {
          if (aiStatus.isRefreshingCatalog) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.btn_refresh))
          }
        }
      },
    )

    if (aiStatus.isRefreshingCatalog) {
      PremiumLoadingRow(label = stringResource(R.string.ai_models_count, aiStatus.catalog.models.size, updatedStr))
    } else {
      StatusPill(
        text = stringResource(R.string.ai_models_count, aiStatus.catalog.models.size, updatedStr),
        tone = StatusTone.PRIMARY,
        leadingIcon = Icons.Default.Psychology,
      )
    }

    if (aiStatus.refreshError != null) {
      PremiumErrorBanner(message = aiStatus.refreshError)
    }

    PremiumSelectorRow(
      label = stringResource(R.string.ai_model_select_title),
      value =
        if (isAutoSelected) stringResource(R.string.ai_model_auto_title)
        else selectedModelInfo?.name ?: preferredModelId,
      supportingText =
        if (isAutoSelected) stringResource(R.string.ai_model_auto_desc)
        else preferredModelId,
      leadingIcon = Icons.Default.Psychology,
      onClick = { showModelSheet = true },
      modifier = Modifier.testTag("ai_model_picker_btn"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      PremiumSwitchRow(
        title = stringResource(R.string.ai_model_auto_switch_title),
        subtitle = stringResource(R.string.ai_model_auto_switch_desc),
        checked = autoSwitchOnLimit,
        onCheckedChange = onAutoSwitchChange,
        icon = Icons.Default.Autorenew,
      )
      PremiumSwitchRow(
        title = stringResource(R.string.ai_live_translate_title),
        subtitle = stringResource(R.string.ai_live_translate_desc),
        checked = liveTranslateEnabled,
        onCheckedChange = onLiveTranslateChange,
        icon = Icons.Default.Translate,
      )
    }

    HorizontalDivider(color = ext.subtleBorder)

    Text(
      text = stringResource(R.string.ai_default_langs_title),
      style = MaterialTheme.typography.titleSmall,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LanguagePairPill(
        caption = stringResource(R.string.ime_translate_source_select),
        value = SupportedLanguage.findByCode(sourceCode).localizedName(),
        onClick = { langSheetTarget = LangSheetTarget.SOURCE },
        modifier = Modifier.weight(1f),
      )
      Surface(
        color = ext.cardSurfaceElevated,
        shape = CircleShape,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier =
          Modifier.size(38.dp)
            .clip(CircleShape)
            .clickable {
              if (sourceCode != "auto") {
                onSwapLanguages()
              }
            },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = stringResource(R.string.ime_translate_swap_cd),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      LanguagePairPill(
        caption = stringResource(R.string.ime_translate_target_select),
        value = SupportedLanguage.findByCode(targetCode).localizedName(),
        onClick = { langSheetTarget = LangSheetTarget.TARGET },
        modifier = Modifier.weight(1f),
      )
    }

    HorizontalDivider(color = ext.subtleBorder)

    Text(
      text = stringResource(R.string.ai_test_title),
      style = MaterialTheme.typography.titleSmall,
    )
    PremiumTextField(
      value = testInput,
      onValueChange = { testInput = it },
      label = stringResource(R.string.ai_test_label),
      maxLines = 3,
    )
    Button(
      onClick = { onRunQuickTest(testInput) },
      enabled = testInput.isNotBlank() && !aiStatus.isTestingTranslation,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_ai_translate_btn"),
    ) {
      if (aiStatus.isTestingTranslation) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.width(8.dp))
      } else {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(stringResource(R.string.ai_test_btn))
    }

    when (val res = aiStatus.testResult) {
      is TranslationResult.Success -> {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = res.translatedText,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            val switchedNote =
              res.switchedFromModelId?.let { prevId ->
                stringResource(R.string.ai_model_switched_note, prevId)
              }
            Text(
              text =
                buildString {
                  append("${res.providerName} • ${res.modelId}")
                  if (switchedNote != null) append(" ($switchedNote)")
                },
              style = MaterialTheme.typography.labelSmall,
              fontFamily = MonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      is TranslationResult.Error -> {
        PremiumErrorBanner(message = res.userMessage)
      }
      null -> Unit
    }
  }

  if (langSheetTarget != LangSheetTarget.NONE) {
    val isSource = langSheetTarget == LangSheetTarget.SOURCE
    val list = if (isSource) SupportedLanguage.ALL else SupportedLanguage.TARGET_LANGUAGES
    val currentCode = if (isSource) sourceCode else targetCode
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
      onDismissRequest = { langSheetTarget = LangSheetTarget.NONE },
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
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text =
            if (isSource) stringResource(R.string.ime_translate_source_select)
            else stringResource(R.string.ime_translate_target_select),
          style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(list, key = { it.code }) { lang ->
            val selected = lang.code == currentCode
            Surface(
              color =
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else ext.cardSurfaceElevated,
              shape = RoundedCornerShape(14.dp),
              border =
                BorderStroke(
                  width = if (selected) 1.25.dp else 1.dp,
                  color = if (selected) MaterialTheme.colorScheme.primary else ext.subtleBorder,
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 50.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    if (isSource) onSourceSelected(lang.code) else onTargetSelected(lang.code)
                    langSheetTarget = LangSheetTarget.NONE
                  },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    text = lang.localizedName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  )
                  Text(
                    text = "(${lang.nativeName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (selected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showModelSheet) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredModels =
      remember(aiStatus.catalog.models, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
          aiStatus.catalog.models
        } else {
          aiStatus.catalog.models.filter {
            it.id.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true)
          }
        }
      }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
      onDismissRequest = { showModelSheet = false },
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
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(R.string.ai_model_dialog_title),
          style = MaterialTheme.typography.titleLarge,
        )
        PremiumTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = stringResource(R.string.ai_model_search_hint),
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
        )

        if (filteredModels.isEmpty() && searchQuery.isNotBlank()) {
          PremiumEmptyState(
            icon = Icons.Default.Search,
            message = stringResource(R.string.err_model_unavailable),
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (searchQuery.isBlank()) {
              item(key = ModelCatalogRepository.AUTO_MODEL_ID) {
                val isSelected = preferredModelId == ModelCatalogRepository.AUTO_MODEL_ID
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
                      .clip(RoundedCornerShape(14.dp))
                      .clickable {
                        onSelectModel(ModelCatalogRepository.AUTO_MODEL_ID)
                        showModelSheet = false
                      }
                      .testTag("ai_model_option_auto"),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    RadioButton(
                      selected = isSelected,
                      onClick = {
                        onSelectModel(ModelCatalogRepository.AUTO_MODEL_ID)
                        showModelSheet = false
                      },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = stringResource(R.string.ai_model_auto_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                      )
                      Text(
                        text = stringResource(R.string.ai_model_auto_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }

            items(filteredModels, key = { it.id }) { model ->
              val isSelected = preferredModelId == model.id
              val cooldownSec = aiStatus.cooldownSecondsByModel[model.id] ?: 0L
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
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                      onSelectModel(model.id)
                      showModelSheet = false
                    },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  RadioButton(
                    selected = isSelected,
                    onClick = {
                      onSelectModel(model.id)
                      showModelSheet = false
                    },
                  )
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                      text = model.name,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text =
                        buildString {
                          append(model.id)
                          if (model.contextLength > 0) {
                            append(" • ${model.contextLength / 1000}k")
                          }
                        },
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = MonoFontFamily,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  StatusPill(
                    text =
                      if (cooldownSec > 0L) stringResource(R.string.ai_model_cooldown_sec, cooldownSec.toInt())
                      else stringResource(R.string.ai_model_ready),
                    tone = if (cooldownSec > 0L) StatusTone.ERROR else StatusTone.SUCCESS,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LanguagePairPill(
  caption: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  Surface(
    color = ext.cardSurfaceElevated,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, ext.cardBorder),
    modifier = modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = caption,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun OptionalByokCard(
  currentKey: String,
  onSaveKey: (String) -> Unit,
) {
  var keyInput by remember(currentKey) { mutableStateOf(currentKey) }
  var keyVisible by remember { mutableStateOf(false) }

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Key,
      title = stringResource(R.string.byok_title),
      subtitle = stringResource(R.string.byok_desc),
    )

    PremiumTextField(
      value = keyInput,
      onValueChange = { keyInput = it },
      label = stringResource(R.string.byok_label),
      visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
      singleLine = true,
      trailingIcon = {
        IconButton(onClick = { keyVisible = !keyVisible }) {
          Icon(
            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = stringResource(R.string.byok_toggle_visibility_cd),
          )
        }
      },
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilledTonalButton(
        onClick = { onSaveKey(keyInput) },
        shape = RoundedCornerShape(12.dp),
      ) {
        Text(stringResource(R.string.byok_save))
      }
      if (currentKey.isNotBlank()) {
        OutlinedButton(
          onClick = {
            keyInput = ""
            onSaveKey("")
          },
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(stringResource(R.string.byok_clear))
        }
      }
    }
  }
}

@Composable
fun AboutEntryCard(onOpenAbout: () -> Unit) {
  PremiumCard(
    onClick = onOpenAbout,
    modifier = Modifier.testTag("settings_about_entry_btn"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.about_entry_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.about_entry_subtitle, BuildConfig.VERSION_NAME),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
