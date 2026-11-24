package com.ai.banphim.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class DecryptedUserWord(
  val word: String,
  val languageCode: String,
  val count: Int,
  val lastUsedAt: Long,
)

data class DecryptedUserBigram(
  val prevWord: String,
  val nextWord: String,
  val languageCode: String,
  val count: Int,
  val lastUsedAt: Long,
)

data class PersonalLexiconSnapshot(
  val words: List<DecryptedUserWord>,
  val bigrams: List<DecryptedUserBigram>,
)

/**
 * Repository mediating access to the encrypted on-device personal typing dictionary.
 */
class PersonalDictionaryRepository(
  private val dao: PersonalDictionaryDao,
  private val crypto: PersonalLexiconCrypto = PersonalLexiconCrypto(),
) {

  val statsFlow: Flow<PersonalLexiconStats> =
    combine(dao.observeWordCount(), dao.observeBigramCount()) { words, bigrams ->
      PersonalLexiconStats(
        wordCount = words,
        bigramCount = bigrams,
        isEncryptedAtRest = crypto.isKeystoreBacked,
      )
    }

  suspend fun loadSnapshot(): PersonalLexiconSnapshot =
    withContext(Dispatchers.IO) {
      val words =
        dao.getAllWords().mapNotNull { entity ->
          val plain = crypto.decrypt(entity.encryptedWord) ?: return@mapNotNull null
          DecryptedUserWord(
            word = plain,
            languageCode = entity.languageCode,
            count = entity.count,
            lastUsedAt = entity.lastUsedAt,
          )
        }
      val bigrams =
        dao.getAllBigrams().mapNotNull { entity ->
          val prev = crypto.decrypt(entity.encryptedPrevWord) ?: return@mapNotNull null
          val next = crypto.decrypt(entity.encryptedNextWord) ?: return@mapNotNull null
          DecryptedUserBigram(
            prevWord = prev,
            nextWord = next,
            languageCode = entity.languageCode,
            count = entity.count,
            lastUsedAt = entity.lastUsedAt,
          )
        }
      PersonalLexiconSnapshot(words = words, bigrams = bigrams)
    }

  suspend fun recordWord(
    word: String,
    languageCode: String,
    nowMs: Long = System.currentTimeMillis(),
  ) =
    withContext(Dispatchers.IO) {
      val normalized = word.trim()
      if (normalized.isEmpty()) return@withContext
      val key = crypto.computeLookupKey("w:$languageCode", normalized.lowercase())
      val existing = dao.findWord(key)
      val entity =
        UserWordEntity(
          lookupKey = key,
          encryptedWord = crypto.encrypt(normalized),
          languageCode = languageCode,
          count = ((existing?.count ?: 0) + 1).coerceAtMost(MAX_FREQUENCY),
          lastUsedAt = nowMs,
        )
      dao.upsertWord(entity)
      val total = dao.countWords()
      if (total > MAX_WORDS) {
        dao.pruneOldestWords(total - MAX_WORDS)
      }
    }

  suspend fun recordBigram(
    prevWord: String,
    nextWord: String,
    languageCode: String,
    nowMs: Long = System.currentTimeMillis(),
  ) =
    withContext(Dispatchers.IO) {
      val prevClean = prevWord.trim().lowercase()
      val nextClean = nextWord.trim()
      if (prevClean.isEmpty() || nextClean.isEmpty()) return@withContext
      val key = crypto.computeLookupKey("b:$languageCode", "$prevClean->$nextClean")
      val existing = dao.findBigram(key)
      val entity =
        UserBigramEntity(
          lookupKey = key,
          encryptedPrevWord = crypto.encrypt(prevClean),
          encryptedNextWord = crypto.encrypt(nextClean),
          languageCode = languageCode,
          count = ((existing?.count ?: 0) + 1).coerceAtMost(MAX_FREQUENCY),
          lastUsedAt = nowMs,
        )
      dao.upsertBigram(entity)
      val total = dao.countBigrams()
      if (total > MAX_BIGRAMS) {
        dao.pruneOldestBigrams(total - MAX_BIGRAMS)
      }
    }

  suspend fun forgetWord(word: String, languageCode: String) =
    withContext(Dispatchers.IO) {
      val normalized = word.trim().lowercase()
      if (normalized.isEmpty()) return@withContext
      val key = crypto.computeLookupKey("w:$languageCode", normalized)
      dao.deleteWord(key)
      // Also remove bigrams whose target matches the forgotten word
      val allBigrams = dao.getAllBigrams()
      for (b in allBigrams) {
        val prev = crypto.decrypt(b.encryptedPrevWord)
        val next = crypto.decrypt(b.encryptedNextWord)
        if (b.languageCode == languageCode &&
          (next.equals(normalized, ignoreCase = true) || prev.equals(normalized, ignoreCase = true))
        ) {
          dao.deleteBigram(b.lookupKey)
        }
      }
    }

  suspend fun clearAll() =
    withContext(Dispatchers.IO) {
      dao.clearAllWords()
      dao.clearAllBigrams()
    }

  companion object {
    const val MAX_WORDS = 3000
    const val MAX_BIGRAMS = 3000
    private const val MAX_FREQUENCY = 50_000
  }
}
