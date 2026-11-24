package com.ai.banphim

import com.ai.banphim.data.PersonalLexiconCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalLexiconCryptoTest {

  @Test
  fun encryptDecryptRoundtripAndTamperRejection() {
    val crypto = PersonalLexiconCrypto()
    val plain = "tiếng Việt"
    val c1 = crypto.encrypt(plain)
    val c2 = crypto.encrypt(plain)

    // Random IV guarantees distinct ciphertexts for identical plaintexts
    assertNotEquals(c1, c2)
    assertEquals(plain, crypto.decrypt(c1))
    assertEquals(plain, crypto.decrypt(c2))

    // Modifying ciphertext must fail GCM tag verification
    val tampered = c1.dropLast(2) + if (c1.endsWith("AA")) "BB" else "AA"
    assertNull(crypto.decrypt(tampered))
  }

  @Test
  fun lookupKeysAreDeterministicAndNamespaceScoped() {
    val crypto = PersonalLexiconCrypto()
    val k1 = crypto.computeLookupKey("unigram|vi", "không")
    val k2 = crypto.computeLookupKey("unigram|vi", "không")
    val k3 = crypto.computeLookupKey("unigram|en", "không")

    assertEquals(k1, k2)
    assertNotEquals(k1, k3)
  }
}
