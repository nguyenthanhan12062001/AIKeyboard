package com.ai.banphim.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single saved clipboard item on the device.
 *
 * @property id Auto-generated primary key.
 * @property text Copied or manually pinned text snippet.
 * @property isPinned Whether the clip is pinned to stay at the top and survive auto-trim/clear.
 * @property createdAt Epoch timestamp in milliseconds when the clip was last touched or pinned.
 */
@Entity(tableName = "clipboard_clips")
data class ClipboardClipEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val text: String,
  val isPinned: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)
