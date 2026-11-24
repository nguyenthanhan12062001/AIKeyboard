package com.ai.banphim.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.ai.banphim.R
import java.util.Locale

/**
 * Supported in-app UI language options for both the companion Settings app and IME overlays.
 */
enum class AppLanguageOption(
  val tag: String,
  val nativeName: String,
  @StringRes val labelRes: Int,
) {
  SYSTEM("", "System", R.string.app_lang_system),
  VIETNAMESE("vi", "Tiếng Việt", R.string.lang_vi),
  ENGLISH("en", "English", R.string.lang_en),
  INDONESIAN("id", "Bahasa Indonesia", R.string.lang_id),
  CHINESE_SIMPLIFIED("zh-CN", "简体中文", R.string.app_lang_zh_cn),
  CHINESE_TRADITIONAL("zh-TW", "繁體中文", R.string.app_lang_zh_tw),
  JAPANESE("ja", "日本語", R.string.lang_ja),
  KOREAN("ko", "한국어", R.string.lang_ko),
  FRENCH("fr", "Français", R.string.lang_fr),
  GERMAN("de", "Deutsch", R.string.lang_de),
  SPANISH("es", "Español", R.string.lang_es),
  THAI("th", "ไทย", R.string.lang_th),
  RUSSIAN("ru", "Русский", R.string.lang_ru);

  companion object {
    /** Resolves a persisted BCP-47 tag (including legacy `in` for Indonesian) to an [AppLanguageOption]. */
    fun fromTag(rawTag: String?): AppLanguageOption {
      val trimmed = rawTag?.trim().orEmpty()
      if (trimmed.isEmpty()) return SYSTEM
      val normalized =
        when {
          trimmed.equals("in", ignoreCase = true) -> "id"
          trimmed.equals("zh", ignoreCase = true) || trimmed.equals("zh-hans", ignoreCase = true) -> "zh-CN"
          trimmed.equals("zh-hant", ignoreCase = true) -> "zh-TW"
          else -> trimmed
        }
      return entries.firstOrNull { it.tag.equals(normalized, ignoreCase = true) } ?: SYSTEM
    }
  }
}

/**
 * Coordinates per-app UI localization across API 24–36, keeping synchronous startup preferences,
 * Compose configuration overrides, and Android 13+ [LocaleManager] in sync.
 */
object AppLocaleManager {
  private const val SYNC_PREFS_NAME = "banphim_locale_sync"
  private const val KEY_APP_LANGUAGE_TAG = "app_language_code"

  /** Reads the synchronously mirrored app language tag for Activity/Service context wrapping. */
  fun getSavedSyncTag(context: Context): String {
    return runCatching {
      val prefsContext = context.applicationContext ?: context
      prefsContext
        .getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_APP_LANGUAGE_TAG, "")
        .orEmpty()
    }.getOrDefault("")
  }

  /** Persists the normalized app language tag synchronously for instant cold-start resolution. */
  fun saveSyncTag(context: Context, rawTag: String) {
    val normalized = AppLanguageOption.fromTag(rawTag).tag
    runCatching {
      val prefsContext = context.applicationContext ?: context
      prefsContext
        .getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_APP_LANGUAGE_TAG, normalized)
        .apply()
    }
  }

  /** Returns the device's OS locale independent of any per-app resource override. */
  fun systemLocale(): Locale {
    return runCatching {
      val sysLocales = Resources.getSystem().configuration.locales
      if (!sysLocales.isEmpty) sysLocales.get(0) else Locale.getDefault()
    }.getOrElse { Locale.getDefault() }
  }

  /** Parses a persisted [AppLanguageOption.tag] into an Android [Locale]. */
  fun resolveLocale(rawTag: String): Locale {
    return when (val option = AppLanguageOption.fromTag(rawTag)) {
      AppLanguageOption.SYSTEM -> systemLocale()
      AppLanguageOption.INDONESIAN -> Locale.Builder().setLanguage("in").build()
      AppLanguageOption.CHINESE_SIMPLIFIED -> Locale.SIMPLIFIED_CHINESE
      AppLanguageOption.CHINESE_TRADITIONAL -> Locale.TRADITIONAL_CHINESE
      else -> Locale.forLanguageTag(option.tag)
    }
  }

  /**
   * Returns a [Configuration] copy configured for [rawTag] without mutating the source config.
   */
  fun buildLocalizedConfiguration(baseConfig: Configuration, rawTag: String): Configuration {
    val locale = resolveLocale(rawTag)
    return Configuration(baseConfig).apply {
      setLocale(locale)
      setLayoutDirection(locale)
    }
  }

  /**
   * Returns a resource-localized [Context] matching [rawTag] (or the saved sync tag by default).
   */
  fun createLocalizedContext(
    base: Context,
    rawTag: String = getSavedSyncTag(base),
  ): Context {
    val config = buildLocalizedConfiguration(base.resources.configuration, rawTag)
    return base.createConfigurationContext(config)
  }

  /** Resolves a string resource using the user's active per-app language override. */
  fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
    val localized = createLocalizedContext(context)
    return if (formatArgs.isEmpty()) {
      localized.getString(resId)
    } else {
      localized.getString(resId, *formatArgs)
    }
  }

  /** Synchronizes the system per-app language setting on Android 13+ when changed by the user. */
  fun applyPlatformLocaleIfSupported(context: Context, rawTag: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val normalized = AppLanguageOption.fromTag(rawTag).tag
    runCatching {
      val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
      val targetList =
        if (normalized.isEmpty()) {
          LocaleList.getEmptyLocaleList()
        } else {
          LocaleList.forLanguageTags(normalized)
        }
      if (localeManager.applicationLocales != targetList) {
        localeManager.applicationLocales = targetList
      }
    }
  }
}
