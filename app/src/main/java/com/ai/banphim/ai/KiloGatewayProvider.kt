package com.ai.banphim.ai

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keyless OpenAI-compatible client for Kilo AI Gateway.
 * Explicitly strips Authorization and x-api-key headers on anonymous requests.
 */
class KiloGatewayProvider(
  private val httpClient: OkHttpClient,
) {

  /** Result of a single provider translation attempt before fallback/backoff routing. */
  sealed interface ProviderAttemptResult {
    /** Successful translation result containing [translatedText] and the resolved [modelId]. */
    data class Success(val translatedText: String, val modelId: String) : ProviderAttemptResult

    /** Classified failure result with optional HTTP Retry-After hint. */
    data class Failure(
      val errorType: AiErrorType,
      val detail: String,
      val retryAfterSeconds: Long? = null,
    ) : ProviderAttemptResult
  }

  /**
   * Executes a single keyless translation request against Kilo AI Gateway using [modelId].
   */
  suspend fun translate(
    text: String,
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
    modelId: String,
  ): ProviderAttemptResult =
    withContext(Dispatchers.IO) {
      val systemPrompt = buildSystemPrompt(sourceLang, targetLang)
      val payload =
        JSONObject()
          .put("model", modelId)
          .put("temperature", 0.2)
          .put("max_tokens", 1024)
          .put("stream", false)
          .put(
            "messages",
            JSONArray()
              .put(JSONObject().put("role", "system").put("content", systemPrompt))
              .put(JSONObject().put("role", "user").put("content", text)),
          )

      val request =
        Request.Builder()
          .url(CHAT_COMPLETIONS_ENDPOINT)
          .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
          .removeHeader("Authorization")
          .removeHeader("x-api-key")
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .build()

      try {
        httpClient.newCall(request).execute().use { response ->
          val code = response.code
          val bodyString = response.body?.string().orEmpty()

          if (!response.isSuccessful) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull()
            val errorType = classifyHttpError(code, bodyString)
            return@withContext ProviderAttemptResult.Failure(
              errorType = errorType,
              detail = "HTTP $code",
              retryAfterSeconds = retryAfter,
            )
          }

          // Some OpenAI-compatible gateways return HTTP 200 with a JSON {"error": ...} envelope
          extractEmbeddedError(bodyString)?.let { embedded ->
            return@withContext embedded
          }

          val cleaned = extractAndCleanTranslation(bodyString, originalInput = text)
          if (cleaned == null) {
            return@withContext ProviderAttemptResult.Failure(
              errorType = AiErrorType.RESPONSE_ERROR,
              detail = "Empty or null AI translation response",
            )
          }

          ProviderAttemptResult.Success(translatedText = cleaned, modelId = modelId)
        }
      } catch (io: IOException) {
        ProviderAttemptResult.Failure(
          errorType = AiErrorType.NETWORK_ERROR,
          detail = io.javaClass.simpleName,
        )
      } catch (e: Exception) {
        ProviderAttemptResult.Failure(
          errorType = AiErrorType.RESPONSE_ERROR,
          detail = e.javaClass.simpleName,
        )
      }
    }

  private fun extractEmbeddedError(bodyString: String): ProviderAttemptResult.Failure? {
    val root = runCatching { JSONObject(bodyString) }.getOrNull() ?: return null
    val errObj = root.optJSONObject("error") ?: return null
    val code = errObj.optInt("code", 429)
    val msg = errObj.optString("message", "")
    val type = errObj.optString("type", "")
    val errorType = classifyHttpError(if (code > 0) code else 429, "$type $msg")
    return ProviderAttemptResult.Failure(
      errorType = errorType,
      detail = msg.ifBlank { "Gateway error $code" },
    )
  }

  private fun buildSystemPrompt(
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
  ): String {
    val sourceClause =
      if (sourceLang.code == "auto") {
        "from its auto-detected language"
      } else {
        "from ${sourceLang.nativeName} (${sourceLang.code})"
      }
    return "You are a tolerant, natural conversational translator. Translate the user's message $sourceClause into ${targetLang.nativeName} (${targetLang.code}). " +
      "The user's text may contain typos, spelling errors, missing diacritics/accents, phonetic shorthand, or keyboard mistakes—silently infer the intended meaning from context and translate the corrected meaning naturally. " +
      "Preserve tone and nuance. Output ONLY the translated text with no quotes or explanations, and never output null."
  }

  private fun classifyHttpError(code: Int, body: String): AiErrorType {
    val lower = body.lowercase()
    return when {
      lower.contains("not found") ||
        lower.contains("does not exist") ||
        lower.contains("no endpoints") ||
        lower.contains("deprecated") -> AiErrorType.MODEL_UNAVAILABLE
      lower.contains("quota") ||
        lower.contains("credit") ||
        lower.contains("insufficient") ||
        lower.contains("balance") ||
        lower.contains("billing") ||
        lower.contains("free limit") ||
        lower.contains("daily limit") -> AiErrorType.QUOTA_EXCEEDED
      code == 429 ->
        if (lower.contains("exceeded") || lower.contains("limit")) AiErrorType.QUOTA_EXCEEDED
        else AiErrorType.RATE_LIMITED
      code == 402 || code == 401 || code == 403 -> AiErrorType.QUOTA_EXCEEDED
      code == 404 || (code == 400 && lower.contains("model")) -> AiErrorType.MODEL_UNAVAILABLE
      code >= 500 -> AiErrorType.SERVER_UNAVAILABLE
      else -> AiErrorType.RESPONSE_ERROR
    }
  }

  companion object {
    const val CHAT_COMPLETIONS_ENDPOINT = "${ModelCatalogRepository.GATEWAY_BASE_URL}/chat/completions"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)

    /**
     * Safely parses OpenAI-compatible responses, rejecting JSON null / literal "null" outputs,
     * handling content arrays, and stripping <think> reasoning tags.
     */
    internal fun extractAndCleanTranslation(rawJson: String, originalInput: String): String? {
      val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
      val choices = root.optJSONArray("choices") ?: return null
      if (choices.length() == 0) return null
      val first = choices.optJSONObject(0) ?: return null

      var extracted: String? = null
      val message = first.optJSONObject("message")
      if (message != null && !message.isNull("content")) {
        val rawContent = message.opt("content")
        extracted =
          when (rawContent) {
            is String -> rawContent
            is JSONArray -> {
              buildString {
                for (i in 0 until rawContent.length()) {
                  val part = rawContent.optJSONObject(i)
                  if (part != null && !part.isNull("text")) {
                    append(part.optString("text"))
                  } else if (rawContent.opt(i) is String) {
                    append(rawContent.optString(i))
                  }
                }
              }
            }
            else -> null
          }
      }
      if (extracted.isNullOrBlank() && !first.isNull("text")) {
        extracted = first.optString("text")
      }
      if (extracted.isNullOrBlank()) return null

      return cleanOutput(extracted, originalInput)
    }

    /**
     * Strips reasoning tags, enclosing quotes, and invalid literal `"null"`/`"undefined"` outputs.
     */
    internal fun cleanOutput(raw: String, originalInput: String): String? {
      var trimmed = THINK_BLOCK_REGEX.replace(raw, "").trim()
      if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
        trimmed = trimmed.substring(1, trimmed.length - 1).trim()
      }
      if (trimmed.isBlank()) return null

      val inputTrimmed = originalInput.trim()
      if ((trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) &&
        !inputTrimmed.equals(trimmed, ignoreCase = true)
      ) {
        return null
      }
      return trimmed
    }
  }
}
