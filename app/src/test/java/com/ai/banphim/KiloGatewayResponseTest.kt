package com.ai.banphim

import com.ai.banphim.ai.KiloGatewayProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KiloGatewayResponseTest {

  @Test
  fun jsonNullContentReturnsNullInsteadOfLiteralNullString() {
    val raw = """{"choices":[{"message":{"role":"assistant","content":null,"reasoning":"exhausted tokens"}}]}"""
    val result = KiloGatewayProvider.extractAndCleanTranslation(raw, "helo wrld")
    assertNull(result)
  }

  @Test
  fun literalNullContentRejectedForTypoInput() {
    val raw = """{"choices":[{"message":{"role":"assistant","content":"null"}}]}"""
    val result = KiloGatewayProvider.extractAndCleanTranslation(raw, "helo wrld")
    assertNull(result)
  }

  @Test
  fun literalNullAllowedWhenUserActuallyTypedNull() {
    val raw = """{"choices":[{"message":{"role":"assistant","content":"null"}}]}"""
    val result = KiloGatewayProvider.extractAndCleanTranslation(raw, "null")
    assertEquals("null", result)
  }

  @Test
  fun stripsThinkBlocksAndOuterQuotes() {
    val raw = """{"choices":[{"message":{"role":"assistant","content":"<think>thinking about translation</think>\n\"Hello world\""}}]}"""
    val result = KiloGatewayProvider.extractAndCleanTranslation(raw, "xin chao the gioi")
    assertEquals("Hello world", result)
  }

  @Test
  fun extractsContentArrayParts() {
    val raw = """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"Xin chào"}]}}]}"""
    val result = KiloGatewayProvider.extractAndCleanTranslation(raw, "hello")
    assertEquals("Xin chào", result)
  }

  @Test
  fun smartCandidateQueueSwitchesAwayFromRateLimitedPreferredModel() {
    val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val manager = com.ai.banphim.ai.AiProviderManager(ctx)
    val models =
      listOf(
        com.ai.banphim.ai.AiModelInfo("kilo-auto/free", "Kilo Auto", isFree = true),
        com.ai.banphim.ai.AiModelInfo("google/gemma-3-27b:free", "Gemma 3", isFree = true),
        com.ai.banphim.ai.AiModelInfo("qwen/qwen3-8b:free", "Qwen 3", isFree = true),
      )

    // Without failures, preferred model comes first
    val initialQueue =
      manager.buildSmartCandidateQueue(
        models = models,
        preferredModelId = "google/gemma-3-27b:free",
        autoSwitchOnLimit = true,
      )
    assertEquals("google/gemma-3-27b:free", initialQueue.first().id)
    assertEquals(3, initialQueue.size)

    // When autoSwitchOnLimit is disabled, only the selected model is kept
    val strictQueue =
      manager.buildSmartCandidateQueue(
        models = models,
        preferredModelId = "google/gemma-3-27b:free",
        autoSwitchOnLimit = false,
      )
    assertEquals(1, strictQueue.size)
    assertEquals("google/gemma-3-27b:free", strictQueue.first().id)
  }

  @Test
  fun freeLimitTrackerBlacklistsUnavailableAndCoolsDownQuotaModels() {
    val tracker = com.ai.banphim.ai.FreeLimitTracker()
    val now = 1_000_000L

    tracker.recordFailure("model-quota", com.ai.banphim.ai.AiErrorType.QUOTA_EXCEEDED, nowMs = now)
    assertEquals(false, tracker.isModelAvailable("model-quota", now + 10_000L))
    assertEquals(true, tracker.isModelAvailable("model-quota", now + 300_000L))

    tracker.recordFailure("model-gone", com.ai.banphim.ai.AiErrorType.MODEL_UNAVAILABLE, nowMs = now)
    assertEquals(true, tracker.isModelBlacklisted("model-gone"))
    assertEquals(false, tracker.isModelAvailable("model-gone", now + 9_999_999L))
  }
}
