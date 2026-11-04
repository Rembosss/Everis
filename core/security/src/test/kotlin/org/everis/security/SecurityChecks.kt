package org.everis.security

import java.security.SecureRandom
import javax.crypto.KeyGenerator

fun main() {
    var passed = 0
    fun test(name: String, action: () -> Unit) {
        try { action(); passed++; println("PASS: $name") }
        catch (error: Throwable) { throw AssertionError(name, error) }
    }
    fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    fun mustReject(action: () -> Unit) { check(runCatching(action).isFailure) }
    val wrapping = key()
    val original = ByteArray(32).also { SecureRandom().nextBytes(it) }
    test("database key survives authenticated envelope roundtrip") {
        check(KeyEnvelope.unwrap(KeyEnvelope.wrap(original, wrapping), wrapping).contentEquals(original))
    }
    test("each envelope uses a fresh nonce") {
        check(!KeyEnvelope.wrap(original, wrapping).contentEquals(KeyEnvelope.wrap(original, wrapping)))
    }
    test("modified ciphertext is rejected") {
        val envelope = KeyEnvelope.wrap(original, wrapping)
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        mustReject { KeyEnvelope.unwrap(envelope, wrapping) }
    }
    test("modified nonce is rejected") {
        val envelope = KeyEnvelope.wrap(original, wrapping)
        envelope[1] = (envelope[1].toInt() xor 1).toByte()
        mustReject { KeyEnvelope.unwrap(envelope, wrapping) }
    }
    test("wrong wrapping key is rejected") {
        mustReject { KeyEnvelope.unwrap(KeyEnvelope.wrap(original, wrapping), key()) }
    }
    test("truncated oversized or unknown-version envelopes are rejected") {
        mustReject { KeyEnvelope.unwrap(byteArrayOf(1), wrapping) }
        mustReject { KeyEnvelope.unwrap(ByteArray(600), wrapping) }
        val unsupported = KeyEnvelope.wrap(original, wrapping).also { it[0] = 9 }
        mustReject { KeyEnvelope.unwrap(unsupported, wrapping) }
    }
    test("invalid database key length is rejected") {
        mustReject { KeyEnvelope.wrap(ByteArray(8), wrapping) }
    }
    val password = "test backup passphrase".toCharArray()
    val payload = "Homework and private settings".toByteArray()
    val backup = SnapshotBackup.encrypt(payload, password)
    test("portable backup roundtrip does not require a device key") {
        check(SnapshotBackup.decrypt(backup, password).contentEquals(payload))
    }
    test("backup has fresh salt and nonce on every export") {
        check(!backup.contentEquals(SnapshotBackup.encrypt(payload, password)))
    }
    test("wrong backup passphrase never returns plaintext") {
        mustReject { SnapshotBackup.decrypt(backup, "another long password".toCharArray()) }
    }
    test("backup ciphertext authentication rejects one bit change") {
        val changed = backup.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        mustReject { SnapshotBackup.decrypt(changed, password) }
    }
    test("backup salt and nonce tampering is rejected") {
        for (index in listOf(8, 24)) {
            val changed = backup.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            mustReject { SnapshotBackup.decrypt(changed, password) }
        }
    }
    test("backup empty oversize truncated and unknown formats are rejected") {
        mustReject { SnapshotBackup.encrypt(ByteArray(0), password) }
        mustReject { SnapshotBackup.encrypt(ByteArray(SnapshotBackup.MAX_PLAINTEXT_BYTES + 1), password) }
        mustReject { SnapshotBackup.decrypt(backup.copyOf(20), password) }
        mustReject { SnapshotBackup.decrypt(ByteArray(SnapshotBackup.MAX_FILE_BYTES + 1), password) }
        mustReject { SnapshotBackup.decrypt(backup.copyOf().also { it[0] = 0 }, password) }
    }
    test("weak and unbounded backup passwords are rejected before encryption") {
        mustReject { SnapshotBackup.encrypt(payload, "short".toCharArray()) }
        mustReject { SnapshotBackup.encrypt(payload, CharArray(1025) { 'x' }) }
    }
    password.fill('\u0000')
    println("Security checks passed: $passed")
}
