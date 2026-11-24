package com.ai.banphim.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Device-local Room database storing encrypted personal typing habits.
 * Excluded from Android Auto Backup and device-to-device transfers.
 */
@Database(
  entities = [UserWordEntity::class, UserBigramEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class PersonalDictionaryDatabase : RoomDatabase() {
  abstract fun personalDictionaryDao(): PersonalDictionaryDao

  companion object {
    const val DB_NAME = "banphim_personal_dict.db"

    @Volatile private var instance: PersonalDictionaryDatabase? = null

    fun getInstance(context: Context): PersonalDictionaryDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                PersonalDictionaryDatabase::class.java,
                DB_NAME,
              )
              .build()
              .also { instance = it }
        }
    }
  }
}
