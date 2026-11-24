package com.ai.banphim.ai

import android.content.Context
import com.ai.banphim.R
import com.ai.banphim.data.AppLocaleManager
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device translation and language identification engine powered by Google ML Kit.
 * Operates 100% locally once language models (~30 MB each) are downloaded, ensuring
 * zero user text leaves the device in Offline mode.
 */
class OfflineTranslationManager(
  private val context: Context,
) {
  private fun modelManagerOrNull(): RemoteModelManager? =
    runCatching { RemoteModelManager.getInstance() }.getOrNull()

  /** Maps a [SupportedLanguage] code (e.g., `"vi"`) to an ML Kit [TranslateLanguage] tag. */
  fun toMlKitLanguage(code: String): String? =
    when (code.lowercase()) {
      "vi" -> TranslateLanguage.VIETNAMESE
      "en" -> TranslateLanguage.ENGLISH
      "zh", "zh-cn", "zh-tw" -> TranslateLanguage.CHINESE
      "ja" -> TranslateLanguage.JAPANESE
      "ko" -> TranslateLanguage.KOREAN
      "fr" -> TranslateLanguage.FRENCH
      "de" -> TranslateLanguage.GERMAN
      "es" -> TranslateLanguage.SPANISH
      "th" -> TranslateLanguage.THAI
      "id", "in" -> TranslateLanguage.INDONESIAN
      "ru" -> TranslateLanguage.RUSSIAN
      else -> TranslateLanguage.fromLanguageTag(code)
    }

  /** Normalizes an ML Kit language tag back into our [SupportedLanguage] code. */
  fun fromMlKitLanguage(mlKitTag: String): String? {
    val norm = mlKitTag.lowercase()
    return when {
      norm == TranslateLanguage.VIETNAMESE -> "vi"
      norm == TranslateLanguage.ENGLISH -> "en"
      norm == TranslateLanguage.CHINESE || norm.startsWith("zh") -> "zh"
      norm == TranslateLanguage.JAPANESE -> "ja"
      norm == TranslateLanguage.KOREAN -> "ko"
      norm == TranslateLanguage.FRENCH -> "fr"
      norm == TranslateLanguage.GERMAN -> "de"
      norm == TranslateLanguage.SPANISH -> "es"
      norm == TranslateLanguage.THAI -> "th"
      norm == TranslateLanguage.INDONESIAN || norm == "in" -> "id"
      norm == TranslateLanguage.RUSSIAN -> "ru"
      else -> null
    }
  }

  /** Returns the set of [SupportedLanguage] codes currently downloaded on-device. */
  suspend fun getDownloadedLanguages(): Set<String> {
    return runCatching {
      val manager = modelManagerOrNull() ?: return emptySet()
      val downloaded = awaitTask(manager.getDownloadedModels(TranslateRemoteModel::class.java))
      downloaded.mapNotNull { fromMlKitLanguage(it.language) }.toSet()
    }.getOrDefault(emptySet())
  }

  /** Returns whether the offline translation pack for [langCode] is already stored locally. */
  suspend fun isLanguageDownloaded(langCode: String): Boolean {
    val mlCode = toMlKitLanguage(langCode) ?: return false
    val model = TranslateRemoteModel.Builder(mlCode).build()
    return runCatching {
      val manager = modelManagerOrNull() ?: return false
      awaitTask(manager.isModelDownloaded(model))
    }.getOrDefault(false)
  }

  /** Downloads the offline ML Kit language pack for [langCode] (~30 MB). */
  suspend fun downloadLanguage(langCode: String): Result<Unit> {
    val mlCode =
      toMlKitLanguage(langCode)
        ?: return Result.failure(IllegalArgumentException("Unsupported language: $langCode"))
    return runCatching {
      val manager = modelManagerOrNull()
        ?: throw IllegalStateException("ML Kit RemoteModelManager unavailable")
      val model = TranslateRemoteModel.Builder(mlCode).build()
      val conditions = DownloadConditions.Builder().build()
      awaitTask(manager.download(model, conditions))
    }.map {}
  }

  /** Deletes the locally cached ML Kit language pack for [langCode] to reclaim storage. */
  suspend fun deleteLanguage(langCode: String): Result<Unit> {
    val mlCode =
      toMlKitLanguage(langCode)
        ?: return Result.failure(IllegalArgumentException("Unsupported language: $langCode"))
    return runCatching {
      val manager = modelManagerOrNull()
        ?: throw IllegalStateException("ML Kit RemoteModelManager unavailable")
      val model = TranslateRemoteModel.Builder(mlCode).build()
      awaitTask(manager.deleteDownloadedModel(model))
    }.map {}
  }

  /**
   * Translates [text] completely on-device using Google ML Kit.
   *
   * @param allowModelDownloadIfMissing When true (e.g., online in Offline-selected mode), ML Kit
   *   may download missing packs on demand; when false (pure offline or silent fallback), missing
   *   packs fail fast with a clear error message.
   */
  suspend fun translateOffline(
    text: String,
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
    allowModelDownloadIfMissing: Boolean,
  ): TranslationResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
      return TranslationResult.Error(
        AiErrorType.RESPONSE_ERROR,
        AppLocaleManager.getString(context, R.string.err_empty_input),
      )
    }

    val resolvedSrcCode =
      if (sourceLang.code == "auto") {
        detectSourceLanguageOffline(trimmed, targetLang.code)
      } else {
        sourceLang.code
      }

    if (resolvedSrcCode == targetLang.code) {
      return TranslationResult.Success(
        translatedText = trimmed,
        providerName = AppLocaleManager.getString(context, R.string.ai_provider_mlkit_offline),
        modelId = "on-device-$resolvedSrcCode",
        fromCache = false,
      )
    }

    val mlSrc = toMlKitLanguage(resolvedSrcCode)
    val mlTgt = toMlKitLanguage(targetLang.code)
    if (mlSrc == null || mlTgt == null) {
      return TranslationResult.Error(
        AiErrorType.MODEL_UNAVAILABLE,
        AppLocaleManager.getString(context, R.string.err_model_unavailable),
      )
    }

    val srcDownloaded = isLanguageDownloaded(resolvedSrcCode)
    val tgtDownloaded = isLanguageDownloaded(targetLang.code)
    if ((!srcDownloaded || !tgtDownloaded) && !allowModelDownloadIfMissing) {
      val missingLabels =
        buildList {
          if (!srcDownloaded) add(SupportedLanguage.findByCode(resolvedSrcCode).nativeName)
          if (!tgtDownloaded) add(targetLang.nativeName)
        }.joinToString(", ")
      return TranslationResult.Error(
        AiErrorType.MODEL_UNAVAILABLE,
        AppLocaleManager.getString(context, R.string.err_offline_pack_missing, missingLabels),
      )
    }

    val options =
      TranslatorOptions.Builder()
        .setSourceLanguage(mlSrc)
        .setTargetLanguage(mlTgt)
        .build()
    val translator = Translation.getClient(options)

    return try {
      if (!srcDownloaded || !tgtDownloaded) {
        val conditions = DownloadConditions.Builder().build()
        awaitTask(translator.downloadModelIfNeeded(conditions))
      }
      val translated = awaitTask(translator.translate(trimmed))
      TranslationResult.Success(
        translatedText = translated,
        providerName = AppLocaleManager.getString(context, R.string.ai_provider_mlkit_offline),
        modelId = "$resolvedSrcCode→${targetLang.code}",
        fromCache = false,
      )
    } catch (ce: CancellationException) {
      throw ce
    } catch (e: Exception) {
      val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
      TranslationResult.Error(
        AiErrorType.MODEL_UNAVAILABLE,
        msg ?: AppLocaleManager.getString(context, R.string.err_offline_translate_failed),
      )
    } finally {
      translator.close()
    }
  }

  private suspend fun detectSourceLanguageOffline(text: String, targetCode: String): String {
    val identifier = LanguageIdentification.getClient()
    val identifiedTag =
      try {
        awaitTask(identifier.identifyLanguage(text))
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Exception) {
        "und"
      } finally {
        identifier.close()
      }

    val mapped = if (identifiedTag != "und") fromMlKitLanguage(identifiedTag) else null
    if (mapped != null) return mapped

    // Lightweight offline script heuristic if LanguageID returns "und" for very short strings
    return when {
      text.any { it in '\u0102'..'\u1EF9' } -> "vi"
      text.any { it in '\uAC00'..'\uD7AF' } -> "ko"
      text.any { it in '\u3040'..'\u30FF' } -> "ja"
      text.any { it in '\u4E00'..'\u9FFF' } -> "zh"
      text.any { it in '\u0E00'..'\u0E7F' } -> "th"
      text.any { it in '\u0400'..'\u04FF' } -> "ru"
      targetCode == "vi" -> "en"
      else -> "vi"
    }
  }

  private suspend fun <T> awaitTask(task: Task<T>): T =
    suspendCancellableCoroutine { cont ->
      task
        .addOnSuccessListener { result ->
          if (cont.isActive) cont.resume(result)
        }
        .addOnFailureListener { exc ->
          if (cont.isActive) cont.resumeWithException(exc)
        }
        .addOnCanceledListener {
          if (cont.isActive) cont.cancel()
        }
    }
}
