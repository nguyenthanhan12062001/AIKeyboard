package com.ai.banphim.ime.suggestion

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Fast, memory-lightweight Prefix Trie + accent-folded prefix Trie + length-bucketed QWERTY fuzzy matcher.
 */
class WordTrie {

  private class Node {
    val children = HashMap<Char, Node>(4)
    var entry: Entry? = null
  }

  private class FoldedNode {
    val children = HashMap<Char, FoldedNode>(4)
    var bucket: ArrayList<Entry>? = null
  }

  private val root = Node()
  private val foldedRoot = FoldedNode()
  private val entriesByLength = HashMap<Int, ArrayList<Entry>>(24)

  data class Entry(
    val word: String,
    val lower: String,
    val folded: String,
    val baseFreq: Int,
  )

  @Synchronized
  fun insert(word: String, baseFreq: Int) {
    val clean = word.trim()
    if (clean.isEmpty()) return
    val lower = clean.lowercase()
    val folded = foldAccents(lower)
    val entry = Entry(word = clean, lower = lower, folded = folded, baseFreq = baseFreq)

    var cur = root
    for (ch in lower) {
      cur = cur.children.getOrPut(ch) { Node() }
    }
    val existing = cur.entry
    if (existing == null || baseFreq > existing.baseFreq) {
      cur.entry = entry
    }

    var fCur = foldedRoot
    for (ch in folded) {
      fCur = fCur.children.getOrPut(ch) { FoldedNode() }
    }
    val fBucket = fCur.bucket ?: ArrayList<Entry>(2).also { fCur.bucket = it }
    fBucket.add(entry)

    entriesByLength.getOrPut(folded.length) { ArrayList(32) }.add(entry)
  }

  @Synchronized
  fun containsExact(wordLower: String): Boolean {
    var cur = root
    for (ch in wordLower) {
      cur = cur.children[ch] ?: return false
    }
    return cur.entry != null
  }

  @Synchronized
  fun getFrequency(wordLower: String): Int {
    var cur = root
    for (ch in wordLower) {
      cur = cur.children[ch] ?: return 0
    }
    return cur.entry?.baseFreq ?: 0
  }

  /**
   * Finds exact and prefix matches sorted by descending base frequency.
   */
  @Synchronized
  fun searchPrefix(prefixLower: String, limit: Int = 24): List<Entry> {
    if (prefixLower.isEmpty()) return emptyList()
    var cur = root
    for (ch in prefixLower) {
      cur = cur.children[ch] ?: return emptyList()
    }
    val results = ArrayList<Entry>(limit * 2)
    collectSubtree(cur, results, maxVisit = 160)
    return results
      .sortedWith(compareByDescending<Entry> { it.lower == prefixLower }.thenByDescending { it.baseFreq })
      .take(limit)
  }

  /**
   * Matches accent-folded Vietnamese/Latin prefixes (e.g. `"khong"` -> `"không"`) in O(prefix + limit).
   */
  @Synchronized
  fun searchFolded(foldedPrefix: String, limit: Int = 16): List<Entry> {
    if (foldedPrefix.length < 2) return emptyList()
    var fCur = foldedRoot
    for (ch in foldedPrefix) {
      fCur = fCur.children[ch] ?: return emptyList()
    }
    val exact = fCur.bucket
    if (exact != null && exact.size >= limit) {
      return exact.sortedByDescending { it.baseFreq }.take(limit)
    }
    val collected = ArrayList<Entry>(limit * 3)
    collectFoldedSubtree(fCur, collected, maxVisit = limit * 4)
    return collected.distinctBy { it.lower }.sortedByDescending { it.baseFreq }.take(limit)
  }

  /**
   * Finds likely typos within weighted QWERTY key-proximity edit distance <= [maxCost].
   */
  @Synchronized
  fun searchFuzzy(inputLower: String, maxCost: Double = 1.15, limit: Int = 6): List<Pair<Entry, Double>> {
    if (inputLower.length < 2) return emptyList()
    val foldedInput = foldAccents(inputLower)
    val inputLen = foldedInput.length
    val firstChar = foldedInput[0]
    val firstNeighbors = QWERTY_NEIGHBORS[firstChar]

    val matches = ArrayList<Pair<Entry, Double>>(limit * 2)
    val minLen = max(1, inputLen - 1)
    val maxLen = inputLen + 1
    for (len in minLen..maxLen) {
      val bucket = entriesByLength[len] ?: continue
      for (entry in bucket) {
        val cand = entry.folded
        val candFirst = cand[0]
        if (candFirst != firstChar && (firstNeighbors == null || candFirst !in firstNeighbors)) continue
        if (cand == foldedInput && entry.lower == inputLower) continue

        val dist = weightedDamerauLevenshtein(foldedInput, cand)
        if (dist <= maxCost) {
          matches.add(entry to dist)
        }
      }
    }
    return matches
      .sortedWith(compareBy<Pair<Entry, Double>> { it.second }.thenByDescending { it.first.baseFreq })
      .take(limit)
  }

  private fun collectSubtree(node: Node, out: MutableList<Entry>, maxVisit: Int) {
    if (out.size >= maxVisit) return
    node.entry?.let { out.add(it) }
    for (child in node.children.values) {
      collectSubtree(child, out, maxVisit)
      if (out.size >= maxVisit) return
    }
  }

  private fun collectFoldedSubtree(node: FoldedNode, out: MutableList<Entry>, maxVisit: Int) {
    if (out.size >= maxVisit) return
    node.bucket?.let { bucket ->
      for (entry in bucket) {
        out.add(entry)
        if (out.size >= maxVisit) return
      }
    }
    for (child in node.children.values) {
      collectFoldedSubtree(child, out, maxVisit)
      if (out.size >= maxVisit) return
    }
  }

  companion object {
    private val COMBINING_MARKS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val THREAD_LOCAL_DP = ThreadLocal.withInitial { DoubleArray(64 * 64) }

    fun foldAccents(input: String): String {
      val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
      return COMBINING_MARKS_REGEX.replace(normalized, "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
    }

    /**
     * Adjacent keys on a standard QWERTY layout.
     * Substitution between neighboring keys carries a lower penalty (0.55) than distant keys (1.0).
     */
    private val QWERTY_NEIGHBORS: Map<Char, Set<Char>> =
      mapOf(
        'q' to setOf('w', 'a'),
        'w' to setOf('q', 'e', 'a', 's'),
        'e' to setOf('w', 'r', 's', 'd'),
        'r' to setOf('e', 't', 'd', 'f'),
        't' to setOf('r', 'y', 'f', 'g'),
        'y' to setOf('t', 'u', 'g', 'h'),
        'u' to setOf('y', 'i', 'h', 'j'),
        'i' to setOf('u', 'o', 'j', 'k'),
        'o' to setOf('i', 'p', 'k', 'l'),
        'p' to setOf('o', 'l'),
        'a' to setOf('q', 'w', 's', 'z'),
        's' to setOf('w', 'e', 'a', 'd', 'z', 'x'),
        'd' to setOf('e', 'r', 's', 'f', 'x', 'c'),
        'f' to setOf('r', 't', 'd', 'g', 'c', 'v'),
        'g' to setOf('t', 'y', 'f', 'h', 'v', 'b'),
        'h' to setOf('y', 'u', 'g', 'j', 'b', 'n'),
        'j' to setOf('u', 'i', 'h', 'k', 'n', 'm'),
        'k' to setOf('i', 'o', 'j', 'l', 'm'),
        'l' to setOf('o', 'p', 'k'),
        'z' to setOf('a', 's', 'x'),
        'x' to setOf('s', 'd', 'z', 'c'),
        'c' to setOf('d', 'f', 'x', 'v'),
        'v' to setOf('f', 'g', 'c', 'b'),
        'b' to setOf('g', 'h', 'v', 'n'),
        'n' to setOf('h', 'j', 'b', 'm'),
        'm' to setOf('j', 'k', 'n'),
      )

    fun weightedDamerauLevenshtein(a: String, b: String): Double {
      val n = a.length
      val m = b.length
      if (n == 0) return m.toDouble()
      if (m == 0) return n.toDouble()

      val stride = m + 1
      val required = (n + 1) * stride
      var dp = THREAD_LOCAL_DP.get()!!
      if (dp.size < required) {
        dp = DoubleArray(required)
        THREAD_LOCAL_DP.set(dp)
      }

      for (i in 0..n) dp[i * stride] = i.toDouble()
      for (j in 0..m) dp[j] = j.toDouble()

      for (i in 1..n) {
        val ca = a[i - 1]
        val rowOffset = i * stride
        val prevRowOffset = (i - 1) * stride
        val prev2RowOffset = (i - 2) * stride
        for (j in 1..m) {
          val cb = b[j - 1]
          val subCost =
            when {
              ca == cb -> 0.0
              QWERTY_NEIGHBORS[ca]?.contains(cb) == true -> 0.55
              else -> 1.0
            }
          var best =
            min(
              dp[prevRowOffset + j] + 0.9,
              min(dp[rowOffset + j - 1] + 0.9, dp[prevRowOffset + j - 1] + subCost),
            )
          if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
            best = min(best, dp[prev2RowOffset + j - 2] + 0.6)
          }
          dp[rowOffset + j] = best
        }
      }
      return dp[n * stride + m]
    }
  }
}
