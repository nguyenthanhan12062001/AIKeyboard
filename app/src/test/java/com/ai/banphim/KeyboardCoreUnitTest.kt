package com.ai.banphim

import com.ai.banphim.ai.AiErrorType
import com.ai.banphim.ai.FreeLimitTracker
import com.ai.banphim.data.AppLanguageOption
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.data.KeyboardSettings
import com.ai.banphim.ime.TranslateTextBuffer
import com.ai.banphim.ime.suggestion.WordSuggestionEngine
import com.ai.banphim.ui.discovery.DiscoveryArea
import com.ai.banphim.ui.discovery.FeatureDiscoveryCatalog
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardCoreUnitTest {

  @Test
  fun translateTextBufferSupportsCursorEditingNewlinesAndSurrogates() {
    val insertedMid = TranslateTextBuffer.insertAtCursor("Xin chao", 3, "\nban")
    assertEquals("Xin\nban chao", insertedMid.text)
    assertEquals(7, insertedMid.cursorIndex)

    val deletedChar = TranslateTextBuffer.backspaceAtCursor("Xin\nban chao", 4, wordDelete = false)
    assertEquals("Xinban chao", deletedChar.text)
    assertEquals(3, deletedChar.cursorIndex)

    val emojiText = "Hi 😀!"
    // Cursor landing between high and low surrogate of 😀 (index 4) advances to 5
    assertEquals(5, TranslateTextBuffer.clampCursor(emojiText, 4))
    val deletedEmoji = TranslateTextBuffer.backspaceAtCursor(emojiText, 5, wordDelete = false)
    assertEquals("Hi !", deletedEmoji.text)
    assertEquals(3, deletedEmoji.cursorIndex)

    val deletedWord = TranslateTextBuffer.backspaceAtCursor("hello world again", 11, wordDelete = true)
    assertEquals("hello  again", deletedWord.text)
    assertEquals(6, deletedWord.cursorIndex)
  }

  @Test
  fun suggestionEngineSuggestsVietnameseBigramsAndTypos() {
    val engine = WordSuggestionEngine()
    val bigram = engine.suggest(currentWord = "", previousWord = "xin", vietnameseEnabled = true, autoCorrectEnabled = true)
    assertTrue(bigram.candidates.contains("chào"))

    val typo = engine.suggest(currentWord = "teh", previousWord = "", vietnameseEnabled = true, autoCorrectEnabled = true)
    assertEquals("the", typo.autoCorrectTarget)
  }

  @Test
  fun suggestionEngineSupportsIndonesianPrefixesAndBigrams() {
    val engine = WordSuggestionEngine()
    val prefix =
      engine.suggest(
        currentWord = "selam",
        previousWord = "",
        vietnameseEnabled = false,
        autoCorrectEnabled = true,
        languageCode = "id",
      )
    assertTrue(prefix.candidates.contains("selamat"))

    val bigram =
      engine.suggest(
        currentWord = "",
        previousWord = "selamat",
        vietnameseEnabled = false,
        autoCorrectEnabled = true,
        languageCode = "id",
      )
    assertTrue(bigram.candidates.contains("pagi"))
  }

  @Test
  fun freeLimitTrackerRespectsCooldownAndModelRemoval() {
    val tracker = FreeLimitTracker()
    val now = 1_000_000L
    assertTrue(tracker.isModelAvailable("kilo-auto/free", now))

    tracker.recordFailure("kilo-auto/free", AiErrorType.RATE_LIMITED, retryAfterSeconds = 20L, nowMs = now)
    assertFalse(tracker.isModelAvailable("kilo-auto/free", now + 5_000L))
    assertTrue(tracker.isModelAvailable("kilo-auto/free", now + 21_000L))

    tracker.recordFailure("removed/model:free", AiErrorType.MODEL_UNAVAILABLE, nowMs = now)
    assertFalse(tracker.isModelAvailable("removed/model:free", now + 999_999L))
  }

  @Test
  fun appLanguageOptionNormalizesLegacyAndRegionalTags() {
    assertEquals(AppLanguageOption.SYSTEM, AppLanguageOption.fromTag(""))
    assertEquals(AppLanguageOption.SYSTEM, AppLanguageOption.fromTag(null))
    assertEquals(AppLanguageOption.SYSTEM, AppLanguageOption.fromTag("unknown-xx"))
    assertEquals(AppLanguageOption.VIETNAMESE, AppLanguageOption.fromTag("vi"))
    assertEquals(AppLanguageOption.INDONESIAN, AppLanguageOption.fromTag("in"))
    assertEquals(AppLanguageOption.INDONESIAN, AppLanguageOption.fromTag("id"))
    assertEquals(AppLanguageOption.CHINESE_SIMPLIFIED, AppLanguageOption.fromTag("zh"))
    assertEquals(AppLanguageOption.CHINESE_SIMPLIFIED, AppLanguageOption.fromTag("zh-Hans"))
    assertEquals(AppLanguageOption.CHINESE_SIMPLIFIED, AppLanguageOption.fromTag("zh-CN"))
    assertEquals(AppLanguageOption.CHINESE_TRADITIONAL, AppLanguageOption.fromTag("zh-TW"))
    assertEquals(AppLanguageOption.CHINESE_TRADITIONAL, AppLanguageOption.fromTag("zh-Hant"))
  }

  @Test
  fun appLocaleManagerResolvesChineseAndIndonesianLocales() {
    assertEquals(Locale.SIMPLIFIED_CHINESE, AppLocaleManager.resolveLocale("zh-CN"))
    assertEquals(Locale.TRADITIONAL_CHINESE, AppLocaleManager.resolveLocale("zh-TW"))
    assertTrue(AppLocaleManager.resolveLocale("id").language in setOf("in", "id"))
    assertEquals("vi", AppLocaleManager.resolveLocale("vi").language)
  }

  @Test
  fun suggestionEngineFiltersSensitiveTokensFromLearning() {
    assertFalse(WordSuggestionEngine.isSafeToLearn("123456"))
    assertFalse(WordSuggestionEngine.isSafeToLearn("otp2025"))
    assertFalse(WordSuggestionEngine.isSafeToLearn("user@example.com"))
    assertFalse(WordSuggestionEngine.isSafeToLearn("https://example.vn"))
    assertFalse(WordSuggestionEngine.isSafeToLearn("a"))
    assertFalse(WordSuggestionEngine.isSafeToLearn("p@ss!"))
    assertFalse(WordSuggestionEngine.isSafeToLearn("SECRETKEY"))

    assertTrue(WordSuggestionEngine.isSafeToLearn("không"))
    assertTrue(WordSuggestionEngine.isSafeToLearn("xin"))
    assertTrue(WordSuggestionEngine.isSafeToLearn("hello"))
    assertTrue(WordSuggestionEngine.isSafeToLearn("AI"))
  }

  @Test
  fun suggestionEngineLearnsRanksWithRecencyAndForgets() {
    val engine = WordSuggestionEngine()
    val now = 1_700_000_000_000L

    // Older word repeated 3 times 30 days ago vs newer word repeated 3 times today
    val oldMs = now - 30L * 86_400_000L
    repeat(3) { engine.learnWord("xylophonist", previousWord = "chào", languageCode = "vi", nowMs = oldMs) }
    repeat(3) { engine.learnWord("xylene", previousWord = "chào", languageCode = "vi", nowMs = now) }

    val prefixResult =
      engine.suggest(
        currentWord = "xyl",
        previousWord = "",
        vietnameseEnabled = true,
        autoCorrectEnabled = true,
        languageCode = "vi",
        nowMs = now,
      )
    assertEquals("xylene", prefixResult.candidates.first())

    val bigramResult =
      engine.suggest(
        currentWord = "",
        previousWord = "chào",
        vietnameseEnabled = true,
        autoCorrectEnabled = true,
        languageCode = "vi",
        nowMs = now,
      )
    assertTrue(bigramResult.candidates.contains("xylene"))

    engine.forgetWord("xylene", languageCode = "vi")
    val afterForget =
      engine.suggest(
        currentWord = "xyl",
        previousWord = "",
        vietnameseEnabled = true,
        autoCorrectEnabled = true,
        languageCode = "vi",
        nowMs = now,
      )
    assertFalse(afterForget.candidates.contains("xylene"))
    assertTrue(afterForget.candidates.contains("xylophonist"))

    engine.clearUserLexicon()
    val afterClear =
      engine.suggest(
        currentWord = "xyl",
        previousWord = "",
        vietnameseEnabled = true,
        autoCorrectEnabled = true,
        languageCode = "vi",
        nowMs = now,
      )
    assertFalse(afterClear.candidates.contains("xylophonist"))
  }

  @Test
  fun suggestionEngineSupportsQwertyFuzzyAndAccentFoldedMatches() {
    val engine = WordSuggestionEngine()
    val qwertyTypo =
      engine.suggest(
        currentWord = "hellp",
        previousWord = "",
        vietnameseEnabled = false,
        autoCorrectEnabled = true,
        languageCode = "en",
      )
    assertEquals("hello", qwertyTypo.autoCorrectTarget)
    assertTrue(qwertyTypo.candidates.contains("hello"))

    val folded =
      engine.suggest(
        currentWord = "kghong",
        previousWord = "",
        vietnameseEnabled = true,
        autoCorrectEnabled = true,
        languageCode = "vi",
      )
    assertTrue(folded.candidates.contains("không"))
  }

  @Test
  fun onboardingDefaultsAndFeatureDiscoveryCatalogAreConsistent() {
    val defaultSettings = KeyboardSettings()
    assertFalse(defaultSettings.hasCompletedOnboarding)
    assertTrue(defaultSettings.seenDiscoveryAreas.isEmpty())
    assertFalse(defaultSettings.discoveryTipsDismissed)

    val allSteps = FeatureDiscoveryCatalog.allSteps
    assertEquals(8, allSteps.size)
    assertEquals(
      DiscoveryArea.entries.toSet(),
      allSteps.map { it.area }.toSet(),
    )
    DiscoveryArea.entries.forEach { area ->
      val areaSteps = FeatureDiscoveryCatalog.stepsForArea(area)
      assertTrue(areaSteps.isNotEmpty())
      areaSteps.forEach { step ->
        assertTrue(step.singleColumnItemIndex >= 0)
        assertTrue(step.twoColumnItemIndex >= 0)
      }
    }
  }
}
