package com.ai.banphim.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks rate limits, quota cooldowns, and unavailable models inspired by AndroidHarness's
 * HarnessFreeLimit pattern, tailored for lightweight IME responsiveness.
 */
class FreeLimitTracker {

  private data class CooldownEntry(
    val untilMs: Long,
    val errorType: AiErrorType,
    val consecutiveFailures: Int,
  )

  private val modelCooldowns = ConcurrentHashMap<String, CooldownEntry>()
  private val removedModels = ConcurrentHashMap.newKeySet<String>()
  private val lastSuccessTimestamps = ConcurrentHashMap<String, Long>()

  /** Returns true if [modelId] is neither permanently removed nor under an active cooldown window. */
  fun isModelAvailable(modelId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (removedModels.contains(modelId)) return false
    val entry = modelCooldowns[modelId] ?: return true
    if (nowMs >= entry.untilMs) {
      modelCooldowns.remove(modelId)
      return true
    }
    return false
  }

  /** Returns true if [modelId] was permanently marked unavailable (e.g. HTTP 404/deprecated). */
  fun isModelBlacklisted(modelId: String): Boolean = removedModels.contains(modelId)

  /** Returns the timestamp in ms of the last successful call for [modelId], or 0 if never succeeded. */
  fun getLastSuccessTimestamp(modelId: String): Long = lastSuccessTimestamps[modelId] ?: 0L

  /** Returns the remaining cooldown duration in seconds for [modelId], or 0 if ready. */
  fun remainingCooldownSeconds(modelId: String, nowMs: Long = System.currentTimeMillis()): Long {
    val entry = modelCooldowns[modelId] ?: return 0L
    val diff = entry.untilMs - nowMs
    return if (diff > 0) (diff / 1000L).coerceAtLeast(1L) else 0L
  }

  /** Returns a snapshot map of currently cooling-down model IDs to remaining seconds. */
  fun getActiveCooldownsSeconds(nowMs: Long = System.currentTimeMillis()): Map<String, Long> {
    val result = mutableMapOf<String, Long>()
    val iter = modelCooldowns.entries.iterator()
    while (iter.hasNext()) {
      val (id, entry) = iter.next()
      val diff = entry.untilMs - nowMs
      if (diff > 0L) {
        result[id] = (diff / 1000L).coerceAtLeast(1L)
      } else {
        iter.remove()
      }
    }
    return result
  }

  /** Clears any active cooldown entry after [modelId] responds successfully. */
  fun recordSuccess(modelId: String, nowMs: Long = System.currentTimeMillis()) {
    modelCooldowns.remove(modelId)
    lastSuccessTimestamps[modelId] = nowMs
  }

  /** Applies an exponential cooldown or permanently blacklists [modelId] based on [errorType]. */
  fun recordFailure(
    modelId: String,
    errorType: AiErrorType,
    retryAfterSeconds: Long? = null,
    nowMs: Long = System.currentTimeMillis(),
  ) {
    if (errorType == AiErrorType.MODEL_UNAVAILABLE) {
      removedModels.add(modelId)
      lastSuccessTimestamps.remove(modelId)
      return
    }
    val prev = modelCooldowns[modelId]
    val count = (prev?.consecutiveFailures ?: 0) + 1
    val durationMs =
      when (errorType) {
        AiErrorType.QUOTA_EXCEEDED -> {
          (retryAfterSeconds ?: 240L).coerceIn(30L, 900L) * 1000L
        }
        AiErrorType.RATE_LIMITED -> {
          val base = retryAfterSeconds ?: (15L shl (count - 1).coerceAtMost(3))
          base.coerceIn(10L, 120L) * 1000L
        }
        AiErrorType.SERVER_UNAVAILABLE -> {
          (10L shl (count - 1).coerceAtMost(3)).coerceAtMost(60L) * 1000L
        }
        AiErrorType.RESPONSE_ERROR -> 20_000L
        AiErrorType.NETWORK_ERROR,
        AiErrorType.CANCELLED,
        AiErrorType.MODEL_UNAVAILABLE -> 0L
      }
    if (durationMs > 0L) {
      modelCooldowns[modelId] = CooldownEntry(nowMs + durationMs, errorType, count)
    }
  }

  /** Resets all tracked cooldowns and removed-model flags. */
  fun clearAll() {
    modelCooldowns.clear()
    removedModels.clear()
  }

  companion object {
    /** Computes the retry backoff delay in milliseconds for [attemptIndex]. */
    fun backoffDelayMs(attemptIndex: Int): Long {
      return when (attemptIndex) {
        0 -> 450L
        1 -> 1100L
        else -> 1800L
      }
    }
  }
}
