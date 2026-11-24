package com.ai.banphim.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO managing locally persisted clipboard history and pinned clips.
 */
@Dao
interface ClipboardDao {
  /** Observes all saved clips with pinned entries ordered first, then newest. */
  @Query("SELECT * FROM clipboard_clips ORDER BY isPinned DESC, createdAt DESC")
  fun observeAllClips(): Flow<List<ClipboardClipEntity>>

  /** Finds an existing clip matching [text] to avoid duplicates. */
  @Query("SELECT * FROM clipboard_clips WHERE text = :text LIMIT 1")
  suspend fun findByText(text: String): ClipboardClipEntity?

  /** Inserts or replaces a clipboard clip entry. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(clip: ClipboardClipEntity)

  /** Updates the timestamp of an existing clip to bring it back to the top. */
  @Query("UPDATE clipboard_clips SET createdAt = :timestamp WHERE id = :id")
  suspend fun touchClip(id: Long, timestamp: Long)

  /** Explicitly sets the pinned status and refreshes the clip timestamp. */
  @Query("UPDATE clipboard_clips SET isPinned = :pinned, createdAt = :timestamp WHERE id = :id")
  suspend fun setPinned(id: Long, pinned: Boolean, timestamp: Long)

  /** Toggles whether the clip with [id] is pinned. */
  @Query("UPDATE clipboard_clips SET isPinned = NOT isPinned WHERE id = :id")
  suspend fun togglePin(id: Long)

  /** Deletes a single clip by [id]. */
  @Query("DELETE FROM clipboard_clips WHERE id = :id")
  suspend fun deleteById(id: Long)

  /** Removes all unpinned clips while preserving pinned items. */
  @Query("DELETE FROM clipboard_clips WHERE isPinned = 0")
  suspend fun clearUnpinned()

  /** Trims older unpinned clips beyond [keepCount] so storage stays bounded. */
  @Query(
    "DELETE FROM clipboard_clips WHERE isPinned = 0 AND id NOT IN " +
      "(SELECT id FROM clipboard_clips WHERE isPinned = 0 ORDER BY createdAt DESC LIMIT :keepCount)"
  )
  suspend fun trimUnpinned(keepCount: Int = 25)
}
