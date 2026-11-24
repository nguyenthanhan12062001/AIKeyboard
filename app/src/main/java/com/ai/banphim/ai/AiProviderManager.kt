package com.ai.banphim.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import com.ai.banphim.R
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.data.TranslationMode
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Coordinates keyless free AI translation via Kilo AI Gateway, with dynamic free catalog
 * fallback, exponential backoff, request cancellation, in-memory LRU caching, and optional BYOK.
 * Never routes to paid APIs automatically and never logs user input text.
 */
class AiProviderManager(
  private val context: Context,
) {
  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(10, TimeUnit.SECONDS)
      .build()

  /** Repository managing the dynamically fetched and cached free Kilo model catalog. */
  val catalogRepository = ModelCatalogRepository(context.applicationContext, httpClient)
  /** On-device Google ML Kit translation engine and offline model pack manager. */
  val offlineManager = OfflineTranslationManager(context.applicationContext)
  private val freeLimitTracker = FreeLimitTracker()
  private val kiloProvider = KiloGatewayProvider(httpClient)
  private val geminiByokProvider = OptionalGeminiByokProvider(httpClient)

  // In-memory LRU cache only (not persisted to disk for privacy)
  private val translationCache = LruCache<String, TranslationResult.Success>(50)

  /** Checks whether an active network transport with Internet capability is available. */
  fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  /** Evicts all in-memory translation results cached during the current session. */
  fun clearSessionCache() {
    translationCache.evictAll()
  }

  /** Clears cooldown trackers and refreshes the free model catalog from Kilo AI Gateway. */
  suspend fun refreshFreeModels(): CatalogSnapshot? {
    freeLimitTracker.clearAll()
    return catalogRepository.refreshCatalogFromNetwork()
  }

  /** Returns active cooldown durations (in seconds) keyed by model ID for Settings diagnostics. */
  fun getActiveCooldownsSeconds(): Map<String, Long> = freeLimitTracker.getActiveCooldownsSeconds()

  /**
   * Translates [rawText] from [sourceLang] into [targetLang] using free Kilo AI Gateway models
   * with smart model ordering, immediate failover on free/rate limits, and optional BYOK fallback.
   */
  suspend fun translate(
    rawText: String,
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
    preferredModelId: String = ModelCatalogRepository.AUTO_MODEL_ID,
    autoSwitchOnLimit: Boolean = true,
    optionalGeminiKey: String = "",
    translationMode: TranslationMode = TranslationMode.AUTO_HYBRID,
  ): TranslationResult {
    val text = rawText.trim()
    if (text.isEmpty()) {
      return TranslationResult.Error(
        AiErrorType.RESPONSE_ERROR,
        AppLocaleManager.getString(context, R.string.err_empty_input),
      )
    }

    val normalizedPreferred = preferredModelId.trim().ifBlank { ModelCatalogRepository.AUTO_MODEL_ID }
    val cacheKey = "${translationMode.name}:${sourceLang.code}->${targetLang.code}:$normalizedPreferred:$text"
    translationCache.get(cacheKey)?.let { cached ->
      return cached.copy(fromCache = true)
    }

    if (translationMode == TranslationMode.OFFLINE_MLKIT) {
      val offlineRes =
        offlineManager.translateOffline(
          text = text,
          sourceLang = sourceLang,
          targetLang = targetLang,
          allowModelDownloadIfMissing = isNetworkAvailable(),
        )
      if (offlineRes is TranslationResult.Success) {
        translationCache.put(cacheKey, offlineRes)
      }
      return offlineRes
    }

    if (!isNetworkAvailable()) {
      if (translationMode == TranslationMode.AUTO_HYBRID) {
        val offlineFallback =
          offlineManager.translateOffline(
            text = text,
            sourceLang = sourceLang,
            targetLang = targetLang,
            allowModelDownloadIfMissing = false,
          )
        if (offlineFallback is TranslationResult.Success) {
          val tagged = offlineFallback.copy(switchedFromModelId = "Online AI")
          translationCache.put(cacheKey, tagged)
          return tagged
        }
        return offlineFallback
      }
      return TranslationResult.Error(
        AiErrorType.NETWORK_ERROR,
        AppLocaleManager.getString(context, R.string.err_no_network),
      )
    }

    // Load cached or fresh free model catalog without blocking if /models fails
    var catalog =
      runCatching { catalogRepository.ensureFreshCatalog() }.getOrElse {
        catalogRepository.getCachedOrFallbackCatalog()
      }

    val candidateModels =
      buildSmartCandidateQueue(
        models = catalog.models,
        preferredModelId = normalizedPreferred,
        autoSwitchOnLimit = autoSwitchOnLimit,
      )

    var lastError: KiloGatewayProvider.ProviderAttemptResult.Failure? = null
    val triedModelIds = LinkedHashSet<String>()
    val initialCandidateId = candidateModels.firstOrNull()?.id

    fun resolveSwitchedFrom(actualModelId: String): String? {
      if (normalizedPreferred != ModelCatalogRepository.AUTO_MODEL_ID && actualModelId != normalizedPreferred) {
        return normalizedPreferred
      }
      if (initialCandidateId != null && actualModelId != initialCandidateId && lastError != null) {
        return initialCandidateId
      }
      return null
    }

    for ((modelIndex, model) in candidateModels.withIndex()) {
      triedModelIds.add(model.id)
      val isLastModel = modelIndex == candidateModels.lastIndex
      val maxAttempts = if (isLastModel) 2 else 1

      for (attempt in 0 until maxAttempts) {
        try {
          val attemptResult =
            kiloProvider.translate(
              text = text,
              sourceLang = sourceLang,
              targetLang = targetLang,
              modelId = model.id,
            )

          when (attemptResult) {
            is KiloGatewayProvider.ProviderAttemptResult.Success -> {
              freeLimitTracker.recordSuccess(model.id)
              val success =
                TranslationResult.Success(
                  translatedText = attemptResult.translatedText,
                  providerName = "Kilo AI Gateway",
                  modelId = attemptResult.modelId,
                  fromCache = false,
                  switchedFromModelId = resolveSwitchedFrom(attemptResult.modelId),
                )
              translationCache.put(cacheKey, success)
              return success
            }
            is KiloGatewayProvider.ProviderAttemptResult.Failure -> {
              lastError = attemptResult
              freeLimitTracker.recordFailure(
                modelId = model.id,
                errorType = attemptResult.errorType,
                retryAfterSeconds = attemptResult.retryAfterSeconds,
              )

              // Retry on the same model ONLY if we are on the final fallback candidate and error is transient
              val shouldRetrySameModel =
                isLastModel &&
                  attempt == 0 &&
                  (attemptResult.errorType == AiErrorType.RATE_LIMITED ||
                    attemptResult.errorType == AiErrorType.SERVER_UNAVAILABLE ||
                    attemptResult.errorType == AiErrorType.RESPONSE_ERROR)

              if (shouldRetrySameModel) {
                delay(FreeLimitTracker.backoffDelayMs(attempt))
                continue
              } else {
                // Immediately fail over to the next healthy free model!
                break
              }
            }
          }
        } catch (ce: CancellationException) {
          throw ce
        }
      }

      if (lastError?.errorType == AiErrorType.NETWORK_ERROR) {
        break
      }
    }

    // If all tried models failed due to limits/unavailability and auto-switch is enabled,
    // refresh the remote catalog (if older than 60s) to discover additional free models
    if (autoSwitchOnLimit && lastError?.errorType != AiErrorType.NETWORK_ERROR) {
      val catalogAgeMs = System.currentTimeMillis() - catalog.updatedAtMs
      if (catalogAgeMs > 60_000L) {
        val refreshed = runCatching { catalogRepository.refreshCatalogFromNetwork() }.getOrNull()
        if (refreshed != null) {
          catalog = refreshed
          val extraCandidates =
            refreshed.models
              .filter {
                it.isFree &&
                  it.id !in triedModelIds &&
                  freeLimitTracker.isModelAvailable(it.id)
              }
              .take(4)
          for (model in extraCandidates) {
            triedModelIds.add(model.id)
            try {
              when (
                val attemptResult =
                  kiloProvider.translate(
                    text = text,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    modelId = model.id,
                  )
              ) {
                is KiloGatewayProvider.ProviderAttemptResult.Success -> {
                  freeLimitTracker.recordSuccess(model.id)
                  val success =
                    TranslationResult.Success(
                      translatedText = attemptResult.translatedText,
                      providerName = "Kilo AI Gateway",
                      modelId = attemptResult.modelId,
                      fromCache = false,
                      switchedFromModelId = resolveSwitchedFrom(attemptResult.modelId),
                    )
                  translationCache.put(cacheKey, success)
                  return success
                }
                is KiloGatewayProvider.ProviderAttemptResult.Failure -> {
                  lastError = attemptResult
                  freeLimitTracker.recordFailure(
                    modelId = model.id,
                    errorType = attemptResult.errorType,
                    retryAfterSeconds = attemptResult.retryAfterSeconds,
                  )
                  if (attemptResult.errorType == AiErrorType.NETWORK_ERROR) break
                }
              }
            } catch (ce: CancellationException) {
              throw ce
            }
          }
        }
      }
    }

    // Optional fallback: Only if user explicitly configured their own Gemini API Key in Settings
    if (optionalGeminiKey.isNotBlank()) {
      val byokResult =
        geminiByokProvider.translate(
          text = text,
          sourceLang = sourceLang,
          targetLang = targetLang,
          apiKey = optionalGeminiKey,
        )
      if (byokResult is KiloGatewayProvider.ProviderAttemptResult.Success) {
        val success =
          TranslationResult.Success(
            translatedText = byokResult.translatedText,
            providerName = "Gemini (BYOK)",
            modelId = byokResult.modelId,
            fromCache = false,
            switchedFromModelId = resolveSwitchedFrom(byokResult.modelId),
          )
        translationCache.put(cacheKey, success)
        return success
      }
    }

    // In AUTO_HYBRID mode, silently fall back to already-downloaded ML Kit packs if Online AI fails
    if (translationMode == TranslationMode.AUTO_HYBRID) {
      val offlineFallback =
        offlineManager.translateOffline(
          text = text,
          sourceLang = sourceLang,
          targetLang = targetLang,
          allowModelDownloadIfMissing = false,
        )
      if (offlineFallback is TranslationResult.Success) {
        val tagged = offlineFallback.copy(switchedFromModelId = "Online AI")
        translationCache.put(cacheKey, tagged)
        return tagged
      }
    }

    val errorType = lastError?.errorType ?: AiErrorType.QUOTA_EXCEEDED
    val resId =
      when (errorType) {
        AiErrorType.QUOTA_EXCEEDED -> R.string.err_quota
        AiErrorType.RATE_LIMITED -> R.string.err_rate_limit
        AiErrorType.NETWORK_ERROR -> R.string.err_network
        AiErrorType.SERVER_UNAVAILABLE -> R.string.err_server_busy
        AiErrorType.MODEL_UNAVAILABLE -> R.string.err_model_unavailable
        AiErrorType.RESPONSE_ERROR -> R.string.err_response
        AiErrorType.CANCELLED -> R.string.err_cancelled
      }
    return TranslationResult.Error(errorType, AppLocaleManager.getString(context, resId))
  }

  /**
   * Builds an ordered list of candidate models:
   * - Respects [preferredModelId] first when available (or skips straight to healthy fallbacks if it is cooling down).
   * - Prioritizes models with recent successful responses and zero active cooldowns.
   * - Falls back to soonest-expiring cooldown models if every model is currently throttled.
   */
  internal fun buildSmartCandidateQueue(
    models: List<AiModelInfo>,
    preferredModelId: String,
    autoSwitchOnLimit: Boolean,
    nowMs: Long = System.currentTimeMillis(),
  ): List<AiModelInfo> {
    val freeModels = models.filter { it.isFree }.ifEmpty { ModelCatalogRepository.DEFAULT_FALLBACK_MODELS }
    val isSpecificPreferred = preferredModelId != ModelCatalogRepository.AUTO_MODEL_ID

    if (isSpecificPreferred && !autoSwitchOnLimit) {
      val chosen = freeModels.firstOrNull { it.id == preferredModelId }
        ?: AiModelInfo(id = preferredModelId, name = preferredModelId, isFree = true)
      return listOf(chosen)
    }

    val healthyAvailable =
      freeModels
        .filter { freeLimitTracker.isModelAvailable(it.id, nowMs) }
        .sortedWith(
          compareByDescending<AiModelInfo> { it.id == preferredModelId }
            .thenByDescending { freeLimitTracker.getLastSuccessTimestamp(it.id) > 0L }
            .thenByDescending { freeLimitTracker.getLastSuccessTimestamp(it.id) }
        )

    val ordered = LinkedHashMap<String, AiModelInfo>()
    if (isSpecificPreferred) {
      val preferredObj = freeModels.firstOrNull { it.id == preferredModelId }
        ?: AiModelInfo(id = preferredModelId, name = preferredModelId, isFree = true)
      if (freeLimitTracker.isModelAvailable(preferredObj.id, nowMs)) {
        ordered[preferredObj.id] = preferredObj
      }
    }

    for (m in healthyAvailable) {
      if (ordered.size >= MAX_SMART_CANDIDATES) break
      ordered.putIfAbsent(m.id, m)
    }

    // If few or no healthy models remain, append models whose cooldown is closest to expiring
    if (ordered.size < 2) {
      val coolingSorted =
        freeModels
          .filter {
            !ordered.containsKey(it.id) && !freeLimitTracker.isModelBlacklisted(it.id)
          }
          .sortedBy { freeLimitTracker.remainingCooldownSeconds(it.id, nowMs) }
      for (m in coolingSorted.take(3)) {
        ordered.putIfAbsent(m.id, m)
      }
    }

    return ordered.values.toList().ifEmpty { ModelCatalogRepository.DEFAULT_FALLBACK_MODELS }
  }

  companion object {
    private const val MAX_SMART_CANDIDATES = 8
  }
}
