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
 * Optional BYOK (Bring Your Own Key) provider for users who manually enter a Gemini API Key
 * in advanced settings. Disabled by default and never required.
 */
class OptionalGeminiByokProvider(
  private val httpClient: OkHttpClient,
) {
  /**
   * Translates [text] using the user's optional Gemini API key as a fallback provider.
   */
  suspend fun translate(
    text: String,
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
    apiKey: String,
  ): KiloGatewayProvider.ProviderAttemptResult =
    withContext(Dispatchers.IO) {
      val cleanKey = apiKey.trim()
      if (cleanKey.isEmpty()) {
        return@withContext KiloGatewayProvider.ProviderAttemptResult.Failure(
          AiErrorType.QUOTA_EXCEEDED,
          "No API key",
        )
      }

      val sourceLabel =
        if (sourceLang.code == "auto") "auto-detected language" else "${sourceLang.nativeName} (${sourceLang.code})"
      val prompt =
        "Translate $sourceLabel into ${targetLang.nativeName} (${targetLang.code}). " +
          "The input may contain typos, spelling mistakes, or missing diacritics/accents—silently infer the intended meaning and translate naturally. " +
          "Keep natural conversational tone and nuance. Output ONLY the translated text and never output null.\n\n$text"

      val body =
        JSONObject()
          .put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
          )
          .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 1024))

      val request =
        Request.Builder()
          .url(ENDPOINT)
          .header("x-goog-api-key", cleanKey)
          .header("Content-Type", "application/json")
          .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
          .build()

      try {
        httpClient.newCall(request).execute().use { response ->
          val code = response.code
          val raw = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val type =
              when {
                code == 429 -> AiErrorType.RATE_LIMITED
                code == 401 || code == 403 -> AiErrorType.QUOTA_EXCEEDED
                code >= 500 -> AiErrorType.SERVER_UNAVAILABLE
                else -> AiErrorType.RESPONSE_ERROR
              }
            return@withContext KiloGatewayProvider.ProviderAttemptResult.Failure(type, "HTTP $code")
          }
          val root = JSONObject(raw)
          val candidates = root.optJSONArray("candidates")
          val partObj =
            candidates
              ?.optJSONObject(0)
              ?.optJSONObject("content")
              ?.optJSONArray("parts")
              ?.optJSONObject(0)
          val textOut =
            if (partObj != null && !partObj.isNull("text")) {
              KiloGatewayProvider.cleanOutput(partObj.optString("text"), text)
            } else {
              null
            }
          if (textOut.isNullOrBlank()) {
            KiloGatewayProvider.ProviderAttemptResult.Failure(
              AiErrorType.RESPONSE_ERROR,
              "Empty Gemini response",
            )
          } else {
            KiloGatewayProvider.ProviderAttemptResult.Success(textOut, MODEL_ID)
          }
        }
      } catch (io: IOException) {
        KiloGatewayProvider.ProviderAttemptResult.Failure(
          AiErrorType.NETWORK_ERROR,
          io.javaClass.simpleName,
        )
      } catch (e: Exception) {
        KiloGatewayProvider.ProviderAttemptResult.Failure(
          AiErrorType.RESPONSE_ERROR,
          e.javaClass.simpleName,
        )
      }
    }

  companion object {
    private const val MODEL_ID = "gemini-2.5-flash"
    private const val ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:generateContent"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}
