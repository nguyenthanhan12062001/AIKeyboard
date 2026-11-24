package com.ai.banphim.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the device-only encrypted personal typing lexicon.
 */
@Dao
interface PersonalDictionaryDao {

  @Query("SELECT COUNT(*) FROM personal_words")
  fun observeWordCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM personal_bigrams")
  fun observeBigramCount(): Flow<Int>

  @Query("SELECT * FROM personal_words")
  suspend fun getAllWords(): List<UserWordEntity>

  @Query("SELECT * FROM personal_bigrams")
  suspend fun getAllBigrams(): List<UserBigramEntity>

  @Query("SELECT * FROM personal_words WHERE lookupKey = :lookupKey LIMIT 1")
  suspend fun findWord(lookupKey: String): UserWordEntity?

  @Query("SELECT * FROM personal_bigrams WHERE lookupKey = :lookupKey LIMIT 1")
  suspend fun findBigram(lookupKey: String): UserBigramEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertWord(entity: UserWordEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBigram(entity: UserBigramEntity)

  @Query("DELETE FROM personal_words WHERE lookupKey = :lookupKey")
  suspend fun deleteWord(lookupKey: String)

  @Query("DELETE FROM personal_bigrams WHERE lookupKey = :lookupKey")
  suspend fun deleteBigram(lookupKey: String)

  @Query(
    """
    DELETE FROM personal_words WHERE lookupKey IN (
      SELECT lookupKey FROM personal_words ORDER BY count ASC, lastUsedAt ASC LIMIT :limit
    )
    """
  )
  suspend fun pruneOldestWords(limit: Int)

  @Query(
    """
    DELETE FROM personal_bigrams WHERE lookupKey IN (
      SELECT lookupKey FROM personal_bigrams ORDER BY count ASC, lastUsedAt ASC LIMIT :limit
    )
    """
  )
  suspend fun pruneOldestBigrams(limit: Int)

  @Query("SELECT COUNT(*) FROM personal_words")
  suspend fun countWords(): Int

  @Query("SELECT COUNT(*) FROM personal_bigrams")
  suspend fun countBigrams(): Int

  @Query("DELETE FROM personal_words")
  suspend fun clearAllWords()

  @Query("DELETE FROM personal_bigrams")
  suspend fun clearAllBigrams()
}
