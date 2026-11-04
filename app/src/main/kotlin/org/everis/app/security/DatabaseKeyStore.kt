package org.everis.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.everis.security.KeyEnvelope

/** Background-compatible key access after first device unlock. Never auto-reset on failure. */
class DatabaseKeyStore(context: Context) {
    private val baseFile = File(context.noBackupFilesDir, "database-key-v1.bin")
    private val file = AtomicFile(baseFile)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "${context.packageName}.database-wrapping-key.v1"

    fun loadOrCreate(databaseExists: Boolean): ByteArray = synchronized(processLock) {
        loadOrCreateLocked(databaseExists)
    }

    private fun loadOrCreateLocked(databaseExists: Boolean): ByteArray {
        if (baseFile.exists() || File(baseFile.path + ".bak").exists()) {
            val key = existingWrappingKey()
                ?: error("Database wrapping key is missing; recovery is required")
            val envelope = file.openRead().use { input ->
                val buffer = ByteArray(513)
                var size = 0
                while (size < buffer.size) {
                    val count = input.read(buffer, size, buffer.size - size)
                    if (count < 0) break
                    size += count
                }
                require(size <= 512) { "Invalid database key envelope" }
                buffer.copyOf(size)
            }
            return KeyEnvelope.unwrap(envelope, key)
        }
        check(!databaseExists) { "Database key file is missing; existing data must not be reset" }
        val wrapping = existingWrappingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build())
            }.generateKey()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val envelope = KeyEnvelope.wrap(key, wrapping)
        val output = file.startWrite()
        try {
            output.write(envelope)
            file.finishWrite(output)
            check(baseFile.readBytes().contentEquals(envelope)) { "Database key envelope was not persisted" }
        } catch (failure: Exception) {
            file.failWrite(output)
            key.fill(0)
            throw failure
        }
        return key
    }

    private fun existingWrappingKey(): SecretKey? = keyStore.getKey(alias, null) as? SecretKey

    private companion object {
        // Protect against accidental creation of two store instances in one process.
        val processLock = Any()
    }
}
