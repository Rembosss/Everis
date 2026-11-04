package org.everis.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import org.everis.app.security.DatabaseKeyStore
import java.io.File
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small prototype snapshot, encrypted at rest; replace with transactional Room as data grows. */
class LocalDiary(context: Context) {
    private val base = File(context.noBackupFilesDir, "diary-v1.bin")
    private val file = AtomicFile(base)
    private val keyStore = DatabaseKeyStore(context)
    private fun key() = keyStore.loadOrCreate(base.exists() || File(base.path + ".bak").exists())
    private val aad = "UniDiary/local-snapshot/v1".toByteArray()

    @Synchronized fun load(): JSONObject {
        if (!base.exists() && !File(base.path + ".bak").exists()) return JSONObject()
        val bytes = file.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 8 * 1024 * 1024)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size > 29 && bytes[0] == 1.toByte())
        val secret = key()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(aad)
            return JSONObject(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
        } finally { secret.fill(0) }
    }

    @Synchronized fun save(state: JSONObject) {
        val plaintext = state.toString().toByteArray()
        require(plaintext.size < 8 * 1024 * 1024 - 64)
        val secret = key()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"))
            cipher.updateAAD(aad)
            val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
            val stream = file.startWrite()
            try { stream.write(encrypted); file.finishWrite(stream) }
            catch (failure: Exception) { file.failWrite(stream); throw failure }
        } finally { secret.fill(0); plaintext.fill(0) }
    }
}
