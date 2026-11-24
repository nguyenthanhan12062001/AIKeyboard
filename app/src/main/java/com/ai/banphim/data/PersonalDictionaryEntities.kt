package com.ai.banphim.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encrypted Room record for a learned personal unigram word.
 */
@Entity(
  tableName = "personal_words",
  indices = [Index(value = ["languageCode", "lastUsedAt"])],
)
data class UserWordEntity(
  @PrimaryKey val lookupKey: String,
  val encryptedWord: String,
  val languageCode: String,
  val count: Int,
  val lastUsedAt: Long,
)

/**
 * Encrypted Room record for a learned personal two-word bigram sequence (`prev -> next`).
 */
@Entity(
  tableName = "personal_bigrams",
  indices = [Index(value = ["languageCode", "lastUsedAt"])],
)
data class UserBigramEntity(
  @PrimaryKey val lookupKey: String,
  val encryptedPrevWord: String,
  val encryptedNextWord: String,
  val languageCode: String,
  val count: Int,
  val lastUsedAt: Long,
)

/**
 * Observable privacy & capacity summary of the local personal typing dictionary.
 */
data class PersonalLexiconStats(
  val wordCount: Int = 0,
  val bigramCount: Int = 0,
  val isEncryptedAtRest: Boolean = true,
)
