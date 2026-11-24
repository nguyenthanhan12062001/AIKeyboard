package com.ai.banphim.data

/**
 * Routing mode for the keyboard's translation panel.
 */
enum class TranslationMode {
  /** Uses Online AI Gateway when online & healthy, automatically falling back to ML Kit Offline. */
  AUTO_HYBRID,
  /** Uses Google ML Kit On-Device Translation exclusively without sending text off-device. */
  OFFLINE_MLKIT,
  /** Uses Online AI Gateway (or optional BYOK Gemini) exclusively for highest fluency. */
  ONLINE_AI,
}
