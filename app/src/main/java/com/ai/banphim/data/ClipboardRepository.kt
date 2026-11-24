package com.ai.banphim.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository coordinating local Room clipboard persistence, deduplication, and clip pinning.
 */
class ClipboardRepository(private val dao: ClipboardDao) {
  /** Stream of all clipboard clips sorted with pinned clips first, then most recent. */
  val clipsFlow: Flow<List<ClipboardClipEntity>> = dao.observeAllClips()

  /**
   * Saves a newly copied clip, deduplicating existing text and trimming old unpinned items.
   */
  suspend fun recordClip(rawText: String) {
    val text = rawText.trim()
    if (text.isEmpty() || text.length > 4000) return
    val existing = dao.findByText(text)
    val now = System.currentTimeMillis()
    if (existing != null) {
      dao.touchClip(existing.id, now)
    } else {
      dao.insert(ClipboardClipEntity(text = text, createdAt = now))
      dao.trimUnpinned(keepCount = 25)
    }
  }

  /**
   * Inserts [rawText] directly as a pinned clip, or promotes and pins a matching existing clip.
   */
  suspend fun pinOrInsert(rawText: String) {
    val text = rawText.trim()
    if (text.isEmpty() || text.length > 4000) return
    val existing = dao.findByText(text)
    val now = System.currentTimeMillis()
    if (existing != null) {
      dao.setPinned(existing.id, pinned = true, timestamp = now)
    } else {
      dao.insert(ClipboardClipEntity(text = text, isPinned = true, createdAt = now))
    }
  }

  /** Toggles the pinned state of the clip identified by [id]. */
  suspend fun togglePin(id: Long) = dao.togglePin(id)

  /** Deletes the clip identified by [id]. */
  suspend fun deleteById(id: Long) = dao.deleteById(id)

  /** Deletes all unpinned clips while leaving pinned clips intact. */
  suspend fun clearUnpinned() = dao.clearUnpinned()
}
