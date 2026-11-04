package org.everis.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, authenticated backup. No Android Keystore key ever leaves the device. */
object SnapshotBackup {
    private val magic = "EVRSBKP1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG = 16
    private const val ITERATIONS = 600_000
    const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024 - 64
    const val MAX_FILE_BYTES = MAX_PLAINTEXT_BYTES + 8 + SALT + NONCE + TAG

    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size in 1..MAX_PLAINTEXT_BYTES)
        require(password.size in 12..1024)
        val random = SecureRandom()
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = magic + salt + nonce
        val secret = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plaintext)
        } finally { secret.fill(0) }
    }

    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        val headerSize = magic.size + SALT + NONCE
        require(envelope.size in (headerSize + TAG + 1)..MAX_FILE_BYTES)
        require(password.size in 1..1024)
        require(envelope.copyOfRange(0, magic.size).contentEquals(magic)) { "Unsupported backup format" }
        val salt = envelope.copyOfRange(magic.size, magic.size + SALT)
        val nonce = envelope.copyOfRange(magic.size + SALT, headerSize)
        val secret = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(envelope, 0, headerSize)
            cipher.doFinal(envelope, headerSize, envelope.size - headerSize)
        } finally { secret.fill(0) }
    }
}
