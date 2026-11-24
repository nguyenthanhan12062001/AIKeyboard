package com.ai.banphim.ime.suggestion

import android.content.Context
import com.ai.banphim.data.DecryptedUserBigram
import com.ai.banphim.data.DecryptedUserWord
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Offline prediction output returned for the current typing word.
 *
 * @property candidates Up to 3 ranked word completions or next-word bigram suggestions.
 * @property autoCorrectTarget Replacement word to commit on Space when auto-correction applies.
 */
data class SuggestionResult(
  val candidates: List<String>,
  val autoCorrectTarget: String? = null,
)

/**
 * Personalized, frequency-ranked offline word and next-word predictor.
 *
 * Combines bundled Trie dictionaries, QWERTY key-proximity fuzzy typo matching,
 * accent-folded Vietnamese matching, and encrypted user habit unigrams/bigrams
 * scored with Frequency x Recency Decay. Operates 100% locally on-device.
 */
class WordSuggestionEngine {

  data class UserHabitEntry(
    val word: String,
    val count: Int,
    val lastUsedAt: Long,
    val lower: String = word.lowercase(),
    val folded: String = WordTrie.foldAccents(lower),
  )

  private class StaticBigramBucket {
    val entries = ArrayList<Pair<String, Int>>(4)
    val byLower = HashMap<String, Int>(4)

    fun addIfAbsent(nextWord: String, freq: Int) {
      val nextLower = nextWord.lowercase()
      if (!byLower.containsKey(nextLower)) {
        entries.add(nextWord to freq)
        byLower[nextLower] = freq
      }
    }
  }

  private val userUnigramsByLang = ConcurrentHashMap<String, ConcurrentHashMap<String, UserHabitEntry>>()
  private val userBigramsByLang =
    ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, UserHabitEntry>>>()

  private val triesByLang = ConcurrentHashMap<String, WordTrie>()
  private val staticBigramsByLang = ConcurrentHashMap<String, Map<String, StaticBigramBucket>>()
  private val assetsLoaded = AtomicBoolean(false)

  init {
    seedEmbeddedDictionaries()
  }

  /**
   * Loads expanded frequency dictionaries from `assets/dicts/` in parallel on [Dispatchers.IO]
   * without blocking UI suggestion reads, then atomically swaps each finished dictionary in.
   */
  suspend fun loadBundledAssetsIfNeeded(context: Context) {
    if (assetsLoaded.get()) return
    withContext(Dispatchers.IO) {
      if (assetsLoaded.getAndSet(true)) return@withContext
      coroutineScope {
        val wordJobs =
          listOf(
            Triple("dicts/vi_words.txt", "vi", COMMON_VIET_WORDS),
            Triple("dicts/en_words.txt", "en", COMMON_ENG_WORDS),
            Triple("dicts/id_words.txt", "id", COMMON_ID_WORDS),
          ).map { (path, lang, seeded) ->
            async {
              val trie = buildTrieWithSeedAndAsset(context, path, seeded)
              triesByLang[lang] = trie
            }
          }
        val bigramJobs =
          listOf(
            Triple("dicts/vi_bigrams.txt", "vi", VIET_BIGRAMS),
            Triple("dicts/en_bigrams.txt", "en", ENG_BIGRAMS),
          ).map { (path, lang, seeded) ->
            async {
              val map = buildBigramsWithSeedAndAsset(context, path, seeded)
              staticBigramsByLang[lang] = map
            }
          }
        wordJobs.awaitAll()
        bigramJobs.awaitAll()
      }
    }
  }

  /**
   * Replaces in-memory personal habit caches with decrypted entries loaded from Room.
   */
  fun hydrateUserLexicon(
    words: List<DecryptedUserWord>,
    bigrams: List<DecryptedUserBigram>,
  ) {
    userUnigramsByLang.clear()
    userBigramsByLang.clear()
    for (item in words) {
      val langMap = userUnigramsByLang.getOrPut(item.languageCode) { ConcurrentHashMap() }
      val lower = item.word.lowercase()
      langMap[lower] =
        UserHabitEntry(
          word = item.word,
          count = item.count,
          lastUsedAt = item.lastUsedAt,
          lower = lower,
        )
    }
    for (bg in bigrams) {
      val langBigrams = userBigramsByLang.getOrPut(bg.languageCode) { ConcurrentHashMap() }
      val nextMap = langBigrams.getOrPut(bg.prevWord.lowercase()) { ConcurrentHashMap() }
      val nextLower = bg.nextWord.lowercase()
      nextMap[nextLower] =
        UserHabitEntry(
          word = bg.nextWord,
          count = bg.count,
          lastUsedAt = bg.lastUsedAt,
          lower = nextLower,
        )
    }
  }

  /**
   * Clears all in-memory user habit unigrams and bigrams (e.g., when user wipes personal data).
   */
  fun clearUserLexicon() {
    userUnigramsByLang.clear()
    userBigramsByLang.clear()
  }

  /**
   * Records a committed word (and optional [previousWord] bigram) in the active session cache
   * after applying strict privacy sanitization via [isSafeToLearn].
   */
  fun learnWord(
    word: String,
    previousWord: String = "",
    languageCode: String = "vi",
    nowMs: Long = System.currentTimeMillis(),
  ) {
    val clean = word.trim()
    if (!isSafeToLearn(clean)) return

    val langMap = userUnigramsByLang.getOrPut(languageCode) { ConcurrentHashMap() }
    val key = clean.lowercase()
    val prevEntry = langMap[key]
    langMap[key] =
      UserHabitEntry(
        word = clean,
        count = ((prevEntry?.count ?: 0) + 1).coerceAtMost(50_000),
        lastUsedAt = nowMs,
      )

    val prevClean = previousWord.trim()
    if (isSafeToLearn(prevClean)) {
      val langBigrams = userBigramsByLang.getOrPut(languageCode) { ConcurrentHashMap() }
      val nextMap = langBigrams.getOrPut(prevClean.lowercase()) { ConcurrentHashMap() }
      val prevBg = nextMap[key]
      nextMap[key] =
        UserHabitEntry(
          word = clean,
          count = ((prevBg?.count ?: 0) + 1).coerceAtMost(50_000),
          lastUsedAt = nowMs,
        )
    }
  }

  /**
   * Removes [word] from both unigram and bigram user habit caches immediately.
   */
  fun forgetWord(word: String, languageCode: String = "vi") {
    val key = word.trim().lowercase()
    if (key.isEmpty()) return
    userUnigramsByLang[languageCode]?.remove(key)
    val langBigrams = userBigramsByLang[languageCode]
    if (langBigrams != null) {
      langBigrams.remove(key)
      for (nextMap in langBigrams.values) {
        nextMap.remove(key)
      }
    }
  }

  /**
   * Produces up to 3 ranked offline candidates and an optional auto-correction target.
   */
  fun suggest(
    currentWord: String,
    previousWord: String,
    vietnameseEnabled: Boolean,
    autoCorrectEnabled: Boolean,
    languageCode: String = if (vietnameseEnabled) "vi" else "en",
    nowMs: Long = System.currentTimeMillis(),
  ): SuggestionResult {
    val prevLower = previousWord.trim().lowercase()

    // 1. Next-word prediction when active word is blank (after Space)
    if (currentWord.isBlank()) {
      val scoredNext = LinkedHashMap<String, Double>()
      val userNextMap = userBigramsByLang[languageCode]?.get(prevLower)
      if (userNextMap != null) {
        for (entry in userNextMap.values) {
          val score = userScore(entry.count, entry.lastUsedAt, nowMs) * 2.2
          scoredNext[entry.word] = maxOf(scoredNext[entry.word] ?: 0.0, score)
        }
      }
      val staticNext = staticBigramsByLang[languageCode]?.get(prevLower)?.entries
      if (staticNext != null) {
        for ((nextW, freq) in staticNext) {
          val prevScore = scoredNext[nextW] ?: 0.0
          scoredNext[nextW] = prevScore + freq.toDouble()
        }
      }
      val defaults =
        when (languageCode) {
          "vi" -> DEFAULT_START_VIET
          "id" -> DEFAULT_START_ID
          "fr" -> listOf("Bonjour", "Merci", "Oui")
          "de" -> listOf("Hallo", "Danke", "Ich")
          "es" -> listOf("Hola", "Gracias", "Por")
          "ru" -> listOf("Привет", "Спасибо", "Да")
          else -> DEFAULT_START_ENG
        }
      val rankedNext =
        scoredNext.entries
          .sortedByDescending { it.value }
          .map { it.key }
          .ifEmpty { defaults }
      return SuggestionResult(candidates = rankedNext.take(3), autoCorrectTarget = null)
    }

    val lower = currentWord.lowercase()
    val foldedInput = WordTrie.foldAccents(lower)
    val isCapitalized = currentWord.firstOrNull()?.isUpperCase() == true
    val isAllCaps = currentWord.length > 1 && currentWord.all { it.isUpperCase() }

    val primaryTrie = triesByLang[languageCode] ?: triesByLang["en"]!!
    val englishTrie = triesByLang["en"]
    val userWords = userUnigramsByLang[languageCode].orEmpty()
    val userNextFromPrev = userBigramsByLang[languageCode]?.get(prevLower).orEmpty()
    val staticNextBoost = staticBigramsByLang[languageCode]?.get(prevLower)?.byLower.orEmpty()

    val candidateScores = LinkedHashMap<String, Double>()

    fun addScore(rawWord: String, delta: Double) {
      val formatted = matchCase(rawWord, isCapitalized, isAllCaps)
      candidateScores[formatted] = (candidateScores[formatted] ?: 0.0) + delta
    }

    // 2. Learned personal habit unigrams (highest personalization priority)
    for (entry in userWords.values) {
      val entryLower = entry.lower
      val entryFolded = entry.folded
      val habitBoost = userScore(entry.count, entry.lastUsedAt, nowMs)
      val bigramBoost =
        userNextFromPrev[entryLower]?.let { userScore(it.count, it.lastUsedAt, nowMs) * 1.4 } ?: 0.0

      when {
        entryLower == lower -> addScore(entry.word, 1600.0 + habitBoost + bigramBoost)
        entryLower.startsWith(lower) -> addScore(entry.word, 1100.0 + habitBoost + bigramBoost)
        vietnameseEnabled && currentWord.length >= 2 && (entryFolded == foldedInput || entryFolded.startsWith(foldedInput)) ->
          addScore(entry.word, 950.0 + habitBoost + bigramBoost)
      }
    }

    // 3. Bundled frequency Trie prefix matches
    for (entry in primaryTrie.searchPrefix(lower, limit = 18)) {
      val exactBonus = if (entry.lower == lower) 900.0 else 450.0
      val bgBoost = (staticNextBoost[entry.lower] ?: 0) * 0.5
      addScore(entry.word, exactBonus + entry.baseFreq * 0.6 + bgBoost)
    }

    // Also include common English prefixes when typing in non-English Latin mode
    if (languageCode != "en" && languageCode != "ru" && englishTrie != null) {
      for (entry in englishTrie.searchPrefix(lower, limit = 8)) {
        val exactBonus = if (entry.lower == lower) 680.0 else 280.0
        addScore(entry.word, exactBonus + entry.baseFreq * 0.35)
      }
    }

    // 4. Accent-folded Vietnamese matches (e.g., typing "khong" suggests "không")
    if (vietnameseEnabled && currentWord.length >= 2) {
      for (entry in primaryTrie.searchFolded(foldedInput, limit = 12)) {
        val exactFoldedBonus = if (entry.folded == foldedInput) 720.0 else 360.0
        val bgBoost = (staticNextBoost[entry.lower] ?: 0) * 0.45
        addScore(entry.word, exactFoldedBonus + entry.baseFreq * 0.5 + bgBoost)
      }
    }

    // 5. Personal shortcut / hardcoded typo auto-correct + QWERTY proximity fuzzy matching
    var autoCorrect: String? = null
    val knownValidWord =
      primaryTrie.containsExact(lower) ||
        (englishTrie?.containsExact(lower) == true) ||
        userWords.containsKey(lower)

    val directTypo = COMMON_TYPOS[lower]
    if (directTypo != null) {
      val formatted = matchCase(directTypo, isCapitalized, isAllCaps)
      addScore(formatted, 1800.0)
      if (autoCorrectEnabled && currentWord.length >= 2) {
        autoCorrect = formatted
      }
    } else if (!knownValidWord && currentWord.length >= 3) {
      val fuzzyMatches = primaryTrie.searchFuzzy(lower, maxCost = 1.0, limit = 4)
      for ((entry, dist) in fuzzyMatches) {
        val penalty = dist * 420.0
        addScore(entry.word, 900.0 + entry.baseFreq * 0.4 - penalty)
      }
      val bestFuzzy = fuzzyMatches.firstOrNull()
      if (autoCorrectEnabled &&
        bestFuzzy != null &&
        bestFuzzy.second <= 0.65 &&
        bestFuzzy.first.baseFreq >= 300
      ) {
        autoCorrect = matchCase(bestFuzzy.first.word, isCapitalized, isAllCaps)
      }
    }

    val ranked =
      candidateScores.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .distinct()
        .take(3)

    val finalCandidates =
      when {
        ranked.isEmpty() -> listOf(currentWord)
        ranked.size == 1 && !ranked[0].equals(currentWord, ignoreCase = false) ->
          listOf(currentWord, ranked[0])
        else -> ranked
      }

    return SuggestionResult(candidates = finalCandidates.take(3), autoCorrectTarget = autoCorrect)
  }

  private fun userScore(count: Int, lastUsedAt: Long, nowMs: Long): Double {
    val ageDays = ((nowMs - lastUsedAt).coerceAtLeast(0L) / 86_400_000.0)
    val recencyDecay = 1.0 / (1.0 + ageDays / 14.0)
    return (count.coerceAtMost(500) * 140.0) * recencyDecay
  }

  private fun buildTrieWithSeedAndAsset(
    context: Context,
    assetPath: String,
    seededWords: List<String>,
  ): WordTrie {
    val trie = WordTrie()
    seededWords.forEachIndexed { idx, w ->
      val freq = (900 - idx * 4).coerceAtLeast(120)
      trie.insert(w, freq)
    }
    runCatching {
      context.assets.open(assetPath).use { stream ->
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
          lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val tab = line.indexOf('\t')
            if (tab > 0) {
              val word = line.substring(0, tab).trim()
              val freq = line.substring(tab + 1).trim().toIntOrNull() ?: 50
              if (word.isNotEmpty()) trie.insert(word, freq)
            } else {
              trie.insert(line, 50)
            }
          }
        }
      }
    }
    return trie
  }

  private fun buildBigramsWithSeedAndAsset(
    context: Context,
    assetPath: String,
    seededPairs: Map<String, List<String>>,
  ): Map<String, StaticBigramBucket> {
    val map = HashMap<String, StaticBigramBucket>(512)
    seededPairs.forEach { (prev, nextList) ->
      val bucket = map.getOrPut(prev) { StaticBigramBucket() }
      nextList.forEachIndexed { idx, next ->
        bucket.addIfAbsent(next, 900 - idx * 80)
      }
    }
    runCatching {
      context.assets.open(assetPath).use { stream ->
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
          lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split('\t')
            if (parts.size >= 2) {
              val prev = parts[0].trim().lowercase()
              val next = parts[1].trim()
              val freq = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 100
              if (prev.isNotEmpty() && next.isNotEmpty()) {
                map.getOrPut(prev) { StaticBigramBucket() }.addIfAbsent(next, freq)
              }
            }
          }
        }
      }
    }
    return map
  }

  private fun seedEmbeddedDictionaries() {
    fun seedWords(lang: String, words: List<String>) {
      val trie = WordTrie()
      words.forEachIndexed { idx, w ->
        val freq = (900 - idx * 4).coerceAtLeast(120)
        trie.insert(w, freq)
      }
      triesByLang[lang] = trie
    }

    fun seedBigrams(lang: String, pairs: Map<String, List<String>>) {
      val map = HashMap<String, StaticBigramBucket>(pairs.size)
      pairs.forEach { (prev, nextList) ->
        val bucket = map.getOrPut(prev) { StaticBigramBucket() }
        nextList.forEachIndexed { idx, next ->
          bucket.addIfAbsent(next, 900 - idx * 80)
        }
      }
      staticBigramsByLang[lang] = map
    }

    seedWords("vi", COMMON_VIET_WORDS)
    seedWords("en", COMMON_ENG_WORDS)
    seedWords("id", COMMON_ID_WORDS)
    seedWords("fr", COMMON_FR_WORDS)
    seedWords("de", COMMON_DE_WORDS)
    seedWords("es", COMMON_ES_WORDS)
    seedWords("ru", COMMON_RU_WORDS)

    seedBigrams("vi", VIET_BIGRAMS)
    seedBigrams("en", ENG_BIGRAMS)
    seedBigrams("id", ID_BIGRAMS)
  }

  private fun matchCase(word: String, capitalized: Boolean, allCaps: Boolean): String {
    return when {
      allCaps -> word.uppercase()
      capitalized -> word.replaceFirstChar { it.titlecase() }
      else -> word
    }
  }

  companion object {
    /**
     * Strict privacy & hygiene check: only pure alphabetic words (2..24 chars) may be learned.
     * Rejects digits (OTP/PIN/card numbers), emails, URLs, symbols, and long all-caps tokens.
     */
    fun isSafeToLearn(word: String): Boolean {
      val clean = word.trim()
      if (clean.length !in 2..24) return false
      if (clean.any { it.isDigit() || !it.isLetter() }) return false
      // Avoid memorizing potential secret/API tokens in ALL CAPS longer than 5 chars
      if (clean.length > 5 && clean.all { it.isUpperCase() }) return false
      return true
    }

    private val DEFAULT_START_VIET = listOf("Xin", "Tôi", "Bạn")
    private val DEFAULT_START_ENG = listOf("I", "The", "Hello")
    private val DEFAULT_START_ID = listOf("Halo", "Saya", "Terima")

    private val VIET_BIGRAMS =
      mapOf(
        "xin" to listOf("chào", "lỗi", "cảm ơn"),
        "cảm" to listOf("ơn", "thấy", "giác"),
        "tôi" to listOf("là", "đang", "muốn"),
        "bạn" to listOf("có", "đang", "khỏe"),
        "chúc" to listOf("mừng", "bạn", "ngủ"),
        "không" to listOf("có", "sao", "biết"),
        "đang" to listOf("làm", "ở", "đi"),
        "hôm" to listOf("nay", "qua", "kia"),
        "ngày" to listOf("mai", "nay", "kia"),
        "tiếng" to listOf("Việt", "Anh", "nói"),
        "bàn" to listOf("phím", "làm", "ăn"),
        "rất" to listOf("vui", "tốt", "đẹp"),
      )

    private val ENG_BIGRAMS =
      mapOf(
        "i" to listOf("am", "have", "will"),
        "thank" to listOf("you", "god", "heavens"),
        "good" to listOf("morning", "night", "luck"),
        "how" to listOf("are", "do", "about"),
        "see" to listOf("you", "soon", "later"),
      )

    private val ID_BIGRAMS =
      mapOf(
        "selamat" to listOf("pagi", "siang", "malam"),
        "terima" to listOf("kasih", "banyak", "sudah"),
        "apa" to listOf("kabar", "itu", "ini"),
        "sama" to listOf("sama", "dengan", "saja"),
        "sampai" to listOf("jumpa", "besok", "nanti"),
      )

    private val COMMON_TYPOS =
      mapOf(
        "teh" to "the",
        "adn" to "and",
        "taht" to "that",
        "wiht" to "with",
        "recieve" to "receive",
        "becuase" to "because",
        "definately" to "definitely",
        "seperate" to "separate",
        "helo" to "hello",
        "thansk" to "thanks",
        "khong" to "không",
        "duoc" to "được",
        "nguoi" to "người",
        "tieng" to "tiếng",
        "truong" to "trường",
        "chao" to "chào",
        "cam" to "cảm",
      )

    private val COMMON_VIET_WORDS =
      listOf(
        "anh", "em", "bạn", "tôi", "mình", "chúng", "ta", "họ", "này", "đó", "kia",
        "là", "và", "của", "cho", "với", "trong", "ngoài", "trên", "dưới", "khi",
        "không", "có", "được", "làm", "đi", "về", "đến", "ở", "nói", "biết",
        "muốn", "cần", "phải", "nên", "thích", "yêu", "thương", "nhớ", "vui", "buồn",
        "xin", "chào", "cảm", "ơn", "lỗi", "chúc", "mừng", "tạm", "biệt", "hẹn",
        "hôm", "nay", "mai", "qua", "ngày", "tháng", "năm", "giờ", "phút", "sáng",
        "trưa", "chiều", "tối", "đêm", "ngủ", "ngon", "khỏe", "đẹp", "tốt", "hay",
        "rất", "quá", "lắm", "nhiều", "ít", "hơn", "nhất", "cùng", "nhau", "mọi",
        "người", "nhà", "cửa", "trường", "học", "lớp", "việc", "công", "ty", "tiền",
        "ăn", "uống", "cơm", "nước", "trà", "cà", "phê", "bánh", "mì", "phở",
        "tiếng", "Việt", "Anh", "bàn", "phím", "điện", "thoại", "máy", "tính", "dịch",
        "văn", "bản", "tin", "nhắn", "gửi", "nhận", "xem", "đọc", "viết", "nghe",
        "hiểu", "nghĩ", "hỏi", "trả", "lời", "giúp", "đỡ", "chờ", "đợi", "xong",
        "rồi", "chưa", "đang", "sẽ", "đã", "vẫn", "còn", "lại", "thêm", "nữa",
        "nhưng", "mà", "vì", "nếu", "thì", "tuy", "nhiên", "hoặc", "hay", "như",
      )

    private val COMMON_ENG_WORDS =
      listOf(
        "the", "and", "you", "that", "was", "for", "are", "with", "his", "they",
        "this", "have", "from", "one", "had", "word", "but", "not", "what", "all",
        "were", "when", "your", "can", "said", "there", "use", "each", "which", "she",
        "how", "their", "will", "other", "about", "out", "many", "then", "them", "these",
        "some", "her", "would", "make", "like", "him", "into", "time", "has", "look",
        "hello", "thanks", "thank", "please", "sorry", "good", "morning", "night", "great",
        "awesome", "keyboard", "translate", "translation", "message", "android", "work", "today",
        "tomorrow", "meeting", "schedule", "check", "update", "email", "phone", "password",
        "windows", "screen", "google", "github", "project", "build", "version",
      )

    private val COMMON_ID_WORDS =
      listOf(
        "halo", "selamat", "pagi", "siang", "sore", "malam", "terima", "kasih", "sama", "maaf",
        "permisi", "tolong", "apa", "kabar", "baik", "saya", "aku", "kamu", "anda", "kita",
        "kami", "mereka", "ini", "itu", "yang", "dan", "atau", "untuk", "dengan", "dari",
        "tidak", "bisa", "sudah", "belum", "sedang", "akan", "mau", "ingin", "hari", "besok",
        "kemarin", "sekarang", "rumah", "kerja", "makan", "minum", "pesan", "kirim", "bantu", "terjemah",
      )

    private val COMMON_FR_WORDS =
      listOf(
        "bonjour", "merci", "salut", "oui", "non", "avec", "pour", "dans", "vous", "nous",
        "être", "avoir", "faire", "comment", "ça", "va", "bien", "très", "français", "aujourd'hui",
      )

    private val COMMON_DE_WORDS =
      listOf(
        "hallo", "danke", "bitte", "ja", "nein", "und", "mit", "für", "ich", "sie",
        "wie", "geht", "gut", "sehr", "deutsch", "morgen", "heute", "arbeiten", "schön",
      )

    private val COMMON_ES_WORDS =
      listOf(
        "hola", "gracias", "por", "favor", "sí", "no", "con", "para", "como", "estás",
        "buenos", "días", "noches", "español", "bien", "muy", "amigo", "trabajo", "hoy",
      )

    private val COMMON_RU_WORDS =
      listOf(
        "привет", "спасибо", "пожалуйста", "да", "нет", "как", "дела", "хорошо", "очень", "русский",
        "доброе", "утро", "вечер", "сегодня", "завтра", "работа", "друг", "время",
      )
  }
}
