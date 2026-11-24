package com.ai.banphim.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local Room database storing clipboard history exclusively on the user's device.
 */
@Database(entities = [ClipboardClipEntity::class], version = 1, exportSchema = false)
abstract class ClipboardDatabase : RoomDatabase() {
  /** Provides access to [ClipboardDao]. */
  abstract fun clipboardDao(): ClipboardDao

  companion object {
    @Volatile private var instance: ClipboardDatabase? = null

    /** Returns the thread-safe singleton [ClipboardDatabase] instance. */
    fun getInstance(context: Context): ClipboardDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                ClipboardDatabase::class.java,
                "banphim_clipboard.db",
              )
              .build()
              .also { instance = it }
        }
    }
  }
}
