package org.everis.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated envelope for a database key, not a whole database. */
object KeyEnvelope {
    private const val VERSION: Byte = 1
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_ENVELOPE_BYTES = 512
    private val context = "UniDiary database key wrapping v1".toByteArray(Charsets.UTF_8)

    fun wrap(databaseKey: ByteArray, wrappingKey: SecretKey): ByteArray {
        require(databaseKey.size == 32) { "Expected a random 256-bit database key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        require(cipher.iv.size == NONCE_BYTES)
        cipher.updateAAD(context)
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(databaseKey)
    }

    fun unwrap(envelope: ByteArray, wrappingKey: SecretKey): ByteArray {
        require(envelope.size in (1 + NONCE_BYTES + TAG_BITS / 8)..MAX_ENVELOPE_BYTES)
        require(envelope[0] == VERSION) { "Unsupported key envelope version" }
        val nonce = envelope.copyOfRange(1, 1 + NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(context)
        return cipher.doFinal(envelope, 1 + NONCE_BYTES, envelope.size - 1 - NONCE_BYTES).also {
            require(it.size == 32) { "Invalid unwrapped database key" }
        }
    }
}
