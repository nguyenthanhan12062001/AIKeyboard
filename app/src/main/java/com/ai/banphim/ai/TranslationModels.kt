package com.ai.banphim.ai

/**
 * Language option supported by the AI translation bar.
 *
 * @property code ISO 639-1 language code or `"auto"` for automatic language detection.
 * @property nativeName Autonym displayed alongside the localized label.
 */
data class SupportedLanguage(
  val code: String,
  val nativeName: String,
) {
  companion object {
    /** Complete ordered list of source translation language options including `"auto"`. */
    val ALL =
      listOf(
        SupportedLanguage("auto", "Auto"),
        SupportedLanguage("vi", "Tiếng Việt"),
        SupportedLanguage("en", "English"),
        SupportedLanguage("zh", "中文"),
        SupportedLanguage("ja", "日本語"),
        SupportedLanguage("ko", "한국어"),
        SupportedLanguage("fr", "Français"),
        SupportedLanguage("de", "Deutsch"),
        SupportedLanguage("es", "Español"),
        SupportedLanguage("th", "ไทย"),
        SupportedLanguage("id", "Bahasa Indonesia"),
        SupportedLanguage("ru", "Русский"),
      )

    /** Available target translation languages (excludes `"auto"`). */
    val TARGET_LANGUAGES = ALL.filter { it.code != "auto" }

    /** Looks up a [SupportedLanguage] by ISO [code], defaulting to English (`"en"`). */
    fun findByCode(code: String): SupportedLanguage =
      ALL.firstOrNull { it.code == code } ?: SupportedLanguage("en", "English")
  }
}

/**
 * Metadata describing a free AI model exposed by the Kilo AI Gateway catalog.
 */
data class AiModelInfo(
  val id: String,
  val name: String,
  val isFree: Boolean,
  val contextLength: Int = 0,
)

/**
 * Categorized failure reasons for AI translation requests.
 */
enum class AiErrorType {
  QUOTA_EXCEEDED,
  RATE_LIMITED,
  NETWORK_ERROR,
  SERVER_UNAVAILABLE,
  MODEL_UNAVAILABLE,
  RESPONSE_ERROR,
  CANCELLED,
}

/**
 * Outcome of an AI translation request.
 */
sealed interface TranslationResult {
  /** Successful translation containing [translatedText] and provider attribution. */
  data class Success(
    val translatedText: String,
    val providerName: String,
    val modelId: String,
    val fromCache: Boolean = false,
    val switchedFromModelId: String? = null,
  ) : TranslationResult

  /** Failed translation containing an error [type] and localized [userMessage]. */
  data class Error(
    val type: AiErrorType,
    val userMessage: String,
  ) : TranslationResult
}

/**
 * Snapshot of available free AI models along with cache freshness metadata.
 */
data class CatalogSnapshot(
  val models: List<AiModelInfo>,
  val updatedAtMs: Long,
  val fromNetwork: Boolean,
)
