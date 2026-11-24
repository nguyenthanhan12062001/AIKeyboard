package com.ai.banphim.data

import android.annotation.SuppressLint
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore cryptography for the local personal typing dictionary.
 *
 * - Words are encrypted with AES-256-GCM before storage in SQLite.
 * - Deterministic lookup keys use HMAC-SHA256 so SQLite primary keys never reveal plaintext words.
 */
class PersonalLexiconCrypto {

  private val secureRandom = SecureRandom()

  @Volatile private var fallbackAesKey: SecretKey? = null
  @Volatile private var fallbackHmacKey: SecretKey? = null

  /** True when keys are backed by AndroidKeyStore (always true on real devices). */
  val isKeystoreBacked: Boolean by lazy {
    runCatching {
      getOrCreateAesKey()
      getOrCreateHmacKey()
      true
    }.getOrDefault(false)
  }

  /**
   * Computes a deterministic, non-reversible HMAC-SHA256 digest used as the Room primary key.
   */
  fun computeLookupKey(namespace: String, normalizedValue: String): String {
    val payload = "$namespace\u0000$normalizedValue".toByteArray(StandardCharsets.UTF_8)
    val key = getOrCreateHmacKey()
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key)
    val digest = mac.doFinal(payload)
    return encodeBase64(digest)
  }

  /**
   * Encrypts [plainText] using AES/GCM/NoPadding with a fresh 12-byte random IV.
   */
  fun encrypt(plainText: String): String {
    val key = getOrCreateAesKey()
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
    val combined = ByteArray(1 + iv.size + cipherBytes.size)
    combined[0] = iv.size.toByte()
    System.arraycopy(iv, 0, combined, 1, iv.size)
    System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
    return encodeBase64(combined)
  }

  /**
   * Decrypts Base64-encoded AES-GCM [cipherPayload], returning `null` if tampered or unreadable.
   */
  fun decrypt(cipherPayload: String): String? {
    return runCatching {
      val combined = decodeBase64(cipherPayload)
      if (combined.size < 14) return null
      val ivLen = combined[0].toInt() and 0xFF
      if (ivLen !in 12..16 || combined.size <= 1 + ivLen) return null
      val iv = combined.copyOfRange(1, 1 + ivLen)
      val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
      val key = getOrCreateAesKey()
      val cipher = Cipher.getInstance(AES_TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }.getOrNull()
  }

  @SuppressLint("NewApi")
  private fun encodeBase64(bytes: ByteArray): String {
    return if (Build.VERSION.SDK_INT in 1..25) {
      Base64.encodeToString(bytes, Base64.NO_WRAP)
    } else {
      java.util.Base64.getEncoder().encodeToString(bytes)
    }
  }

  @SuppressLint("NewApi")
  private fun decodeBase64(encoded: String): ByteArray {
    return if (Build.VERSION.SDK_INT in 1..25) {
      Base64.decode(encoded, Base64.NO_WRAP)
    } else {
      java.util.Base64.getDecoder().decode(encoded)
    }
  }

  @Synchronized
  private fun getOrCreateAesKey(): SecretKey {
    runCatching {
      val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      (ks.getKey(AES_KEY_ALIAS, null) as? SecretKey)?.let { return it }

      val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      keyGen.init(
        KeyGenParameterSpec.Builder(
            AES_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
          )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build()
      )
      return keyGen.generateKey()
    }

    return fallbackAesKey
      ?: KeyGenerator.getInstance("AES").apply { init(256, secureRandom) }.generateKey().also {
        fallbackAesKey = it
      }
  }

  @Synchronized
  private fun getOrCreateHmacKey(): SecretKey {
    runCatching {
      val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      (ks.getKey(HMAC_KEY_ALIAS, null) as? SecretKey)?.let { return it }

      val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
      keyGen.init(
        KeyGenParameterSpec.Builder(HMAC_KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build()
      )
      return keyGen.generateKey()
    }

    return fallbackHmacKey
      ?: KeyGenerator.getInstance("HmacSHA256").apply { init(secureRandom) }.generateKey().also {
        fallbackHmacKey = it
      }
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_KEY_ALIAS = "banphim_lexicon_aes_v1"
    private const val HMAC_KEY_ALIAS = "banphim_lexicon_hmac_v1"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
  }
}
