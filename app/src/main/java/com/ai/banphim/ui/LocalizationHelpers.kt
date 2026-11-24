package com.ai.banphim.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.banphim.R
import com.ai.banphim.ai.SupportedLanguage
import com.ai.banphim.data.AppLanguageOption
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.data.KeyboardThemePreset
import com.ai.banphim.ime.engine.KeyboardLanguage

private class LocalizedContextWrapper(
  base: Context,
  private val localizedResources: Resources,
) : ContextWrapper(base) {
  override fun getResources(): Resources = localizedResources
}

/**
 * Provides a localized [Context] and [Configuration] matching [localeTag] to all descendant
 * Compose `stringResource(...)` lookups while preserving the host Activity/Service context chain.
 */
@Composable
fun ProvideAppLocale(
  localeTag: String,
  content: @Composable () -> Unit,
) {
  val baseContext = LocalContext.current
  val baseConfig = LocalConfiguration.current
  val localizedConfig =
    remember(baseConfig, localeTag) {
      AppLocaleManager.buildLocalizedConfiguration(baseConfig, localeTag)
    }
  val localizedContext =
    remember(baseContext, localizedConfig) {
      val localizedResources = baseContext.createConfigurationContext(localizedConfig).resources
      LocalizedContextWrapper(baseContext, localizedResources)
    }
  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedConfig,
    content = content,
  )
}

/** Returns the localized display label for an [AppLanguageOption]. */
@Composable
fun AppLanguageOption.localizedLabel(): String = stringResource(labelRes)

/**
 * Resolves the localized string resource ID for a [KeyboardThemePreset].
 */
@StringRes
fun KeyboardThemePreset.labelRes(): Int =
  when (this) {
    KeyboardThemePreset.DYNAMIC -> R.string.theme_dynamic
    KeyboardThemePreset.LIGHT -> R.string.theme_light
    KeyboardThemePreset.DARK -> R.string.theme_dark
    KeyboardThemePreset.AMOLED -> R.string.theme_oled
    KeyboardThemePreset.OCEAN -> R.string.theme_ocean
  }

/** Returns the localized display name of this [KeyboardThemePreset]. */
@Composable
fun KeyboardThemePreset.localizedName(): String = stringResource(labelRes())

/**
 * Resolves the localized string resource ID for a [SupportedLanguage] translation language.
 */
@StringRes
fun SupportedLanguage.labelRes(): Int =
  when (code) {
    "auto" -> R.string.lang_auto
    "vi" -> R.string.lang_vi
    "en" -> R.string.lang_en
    "zh" -> R.string.lang_zh
    "ja" -> R.string.lang_ja
    "ko" -> R.string.lang_ko
    "fr" -> R.string.lang_fr
    "de" -> R.string.lang_de
    "es" -> R.string.lang_es
    "th" -> R.string.lang_th
    "id" -> R.string.lang_id
    "ru" -> R.string.lang_ru
    else -> R.string.lang_en
  }

/** Returns the localized display name of this [SupportedLanguage]. */
@Composable
fun SupportedLanguage.localizedName(): String = stringResource(labelRes())

/** Returns the localized display name of this [SupportedLanguage] using a [Context]. */
fun SupportedLanguage.localizedName(context: Context): String =
  AppLocaleManager.getString(context, labelRes())

/**
 * Resolves the localized subtype string resource ID for a [KeyboardLanguage].
 */
@StringRes
fun KeyboardLanguage.subtypeLabelRes(): Int =
  when (this) {
    KeyboardLanguage.VIETNAMESE -> R.string.subtype_vietnamese
    KeyboardLanguage.ENGLISH -> R.string.subtype_english
    KeyboardLanguage.CHINESE_PINYIN -> R.string.subtype_chinese_pinyin
    KeyboardLanguage.JAPANESE_ROMAJI -> R.string.subtype_japanese
    KeyboardLanguage.KOREAN_HANGUL -> R.string.subtype_korean
    KeyboardLanguage.RUSSIAN -> R.string.subtype_russian
    KeyboardLanguage.FRENCH -> R.string.subtype_french
    KeyboardLanguage.GERMAN -> R.string.subtype_german
    KeyboardLanguage.SPANISH -> R.string.subtype_spanish
    KeyboardLanguage.THAI -> R.string.subtype_thai
    KeyboardLanguage.INDONESIAN -> R.string.subtype_indonesian
  }

/** Returns the localized subtype label of this [KeyboardLanguage]. */
@Composable
fun KeyboardLanguage.localizedLabel(): String = stringResource(subtypeLabelRes())
