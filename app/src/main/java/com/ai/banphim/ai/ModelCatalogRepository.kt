package com.ai.banphim.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches, filters, and caches free-only text models from Kilo AI Gateway (/models).
 * Strictly excludes paid models and does NOT require tool/function-calling capability.
 */
class ModelCatalogRepository(
  context: Context,
  private val httpClient: OkHttpClient,
) {
  private val prefs = context.getSharedPreferences("kilo_model_catalog", Context.MODE_PRIVATE)

  @Volatile private var memorySnapshot: CatalogSnapshot? = null

  /** Returns the in-memory or cached catalog, falling back to [DEFAULT_FALLBACK_MODELS]. */
  fun getCachedOrFallbackCatalog(): CatalogSnapshot {
    memorySnapshot?.let {
      return it
    }
    val rawJson = prefs.getString(KEY_MODELS_JSON, null)
    val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
    if (!rawJson.isNullOrBlank()) {
      val parsed = parseModelsFromArrayJson(rawJson)
      if (parsed.isNotEmpty()) {
        return CatalogSnapshot(parsed, updatedAt, fromNetwork = false).also { memorySnapshot = it }
      }
    }
    return CatalogSnapshot(DEFAULT_FALLBACK_MODELS, 0L, fromNetwork = false)
  }

  /** Returns a cached catalog if within TTL, or refreshes it over the network otherwise. */
  suspend fun ensureFreshCatalog(forceRefresh: Boolean = false): CatalogSnapshot {
    val current = getCachedOrFallbackCatalog()
    val ageMs = System.currentTimeMillis() - current.updatedAtMs
    if (!forceRefresh && current.updatedAtMs > 0L && ageMs < CATALOG_TTL_MS && current.models.isNotEmpty()) {
      return current
    }
    return refreshCatalogFromNetwork() ?: current
  }

  /** Queries Kilo AI Gateway `/models`, filters confirmed free text models, and updates cache. */
  suspend fun refreshCatalogFromNetwork(): CatalogSnapshot? =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url(MODELS_ENDPOINT)
          .get()
          .removeHeader("Authorization")
          .removeHeader("x-api-key")
          .header("Accept", "application/json")
          .build()

      runCatching {
          httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val freeModels = parseGatewayModelsResponse(body)
            if (freeModels.isEmpty()) return@use null

            val prioritized = prioritizeModels(freeModels)
            val now = System.currentTimeMillis()
            saveModelsCache(prioritized, now)
            CatalogSnapshot(prioritized, now, fromNetwork = true).also { memorySnapshot = it }
          }
        }
        .getOrNull()
    }

  /** Parses gateway `/models` JSON payload and keeps only verified free text-capable models. */
  internal fun parseGatewayModelsResponse(jsonBody: String): List<AiModelInfo> {
    val root = JSONObject(jsonBody)
    val dataArray = root.optJSONArray("data") ?: root.optJSONArray("models") ?: return emptyList()
    val result = mutableListOf<AiModelInfo>()
    for (i in 0 until dataArray.length()) {
      val obj = dataArray.optJSONObject(i) ?: continue
      val id = obj.optString("id").trim()
      if (id.isEmpty()) continue

      // Strictly confirm free status via provider metadata (isFree == true or 0 pricing)
      val hasIsFreeFlag = obj.has("isFree")
      val isFreeFlag = obj.optBoolean("isFree", false)
      val pricing = obj.optJSONObject("pricing")
      val isZeroPricing =
        pricing != null &&
          isZeroCost(pricing.optString("prompt")) &&
          isZeroCost(pricing.optString("completion"))
      val hasFreeSuffix = id.endsWith(":free") || id == PRIMARY_KILO_MODEL

      val confirmedFree =
        when {
          hasIsFreeFlag -> isFreeFlag
          pricing != null -> isZeroPricing || id == PRIMARY_KILO_MODEL
          else -> hasFreeSuffix
        }
      if (!confirmedFree) continue

      // Check text output capability if modality metadata is present (do NOT require tools/functions)
      val arch = obj.optJSONObject("architecture")
      val modality = arch?.optString("modality") ?: ""
      if (modality.isNotEmpty() && !modality.contains("text", ignoreCase = true)) {
        continue
      }

      val name = obj.optString("name").ifBlank { id }
      val contextLen = obj.optInt("context_length", 0)
      result.add(AiModelInfo(id = id, name = name, isFree = true, contextLength = contextLen))
    }
    return result.distinctBy { it.id }
  }

  private fun prioritizeModels(models: List<AiModelInfo>): List<AiModelInfo> {
    val primary = models.filter { it.id == PRIMARY_KILO_MODEL }
    val fastKeywords =
      listOf("flash", "mini", "nano", "lite", "small", "fast", "gemma", "qwen", "gemini", "llama")
    val fast =
      models.filter { m ->
        m.id != PRIMARY_KILO_MODEL && fastKeywords.any { m.id.contains(it, ignoreCase = true) }
      }
    val others =
      models.filter { m ->
        m.id != PRIMARY_KILO_MODEL && fastKeywords.none { m.id.contains(it, ignoreCase = true) }
      }
    val combined = (primary + fast + others)
    return if (combined.any { it.id == PRIMARY_KILO_MODEL }) {
      combined
    } else {
      listOf(DEFAULT_FALLBACK_MODELS.first()) + combined
    }
  }

  private fun saveModelsCache(models: List<AiModelInfo>, updatedAt: Long) {
    val arr = JSONArray()
    models.forEach { m ->
      arr.put(
        JSONObject()
          .put("id", m.id)
          .put("name", m.name)
          .put("isFree", m.isFree)
          .put("contextLength", m.contextLength)
      )
    }
    prefs
      .edit()
      .putString(KEY_MODELS_JSON, arr.toString())
      .putLong(KEY_UPDATED_AT, updatedAt)
      .apply()
  }

  private fun parseModelsFromArrayJson(raw: String): List<AiModelInfo> {
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { idx ->
          val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
          val id = obj.optString("id")
          if (id.isBlank() || !obj.optBoolean("isFree", false)) return@mapNotNull null
          AiModelInfo(
            id = id,
            name = obj.optString("name", id),
            isFree = true,
            contextLength = obj.optInt("contextLength", 0),
          )
        }
      }
      .getOrDefault(emptyList())
  }

  private fun isZeroCost(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val num = raw.trim().toDoubleOrNull() ?: return false
    return num == 0.0
  }

  companion object {
    /** Sentinel value indicating Smart Auto model selection with automatic failover. */
    const val AUTO_MODEL_ID = "auto"
    /** Default keyless free model identifier on Kilo AI Gateway. */
    const val PRIMARY_KILO_MODEL = "kilo-auto/free"
    /** Base URL of the OpenAI-compatible Kilo AI Gateway. */
    const val GATEWAY_BASE_URL = "https://api.kilo.ai/api/gateway"
    /** Endpoint returning the dynamic model catalog. */
    const val MODELS_ENDPOINT = "$GATEWAY_BASE_URL/models"
    private const val CATALOG_TTL_MS = 24L * 60L * 60L * 1000L
    private const val KEY_MODELS_JSON = "models_json"
    private const val KEY_UPDATED_AT = "updated_at_ms"

    /** Built-in offline fallback catalog used before any network fetch succeeds. */
    val DEFAULT_FALLBACK_MODELS =
      listOf(
        AiModelInfo(
          id = PRIMARY_KILO_MODEL,
          name = "Kilo Auto Free",
          isFree = true,
          contextLength = 128_000,
        )
      )
  }
}
