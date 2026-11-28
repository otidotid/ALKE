package com.otidotid.alke.multidevice.huawei

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.Closeable
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local-only Huawei reconnect material. File is outside backup scope, encrypted
 * by a non-exportable Android Keystore AES key. It is NOT part of ALKE's Room,
 * cloud backup, WHOOP pairing, or logs. No session key is stored here.
 *
 * The pending slot holds a candidate after the authenticated bind token arrives.
 * Only reconnect proof and encrypted readback promote it to a verified binding.
 */
internal class HuaweiCredentialStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "huawei-bindings-v1")

    class Binding(val identity: String, val credential: ByteArray) : Closeable {
        init {
            require(identity.matches(Regex("[0-9a-fA-F]{32}")))
            require(credential.size == 16 || credential.size == 32)
        }
        override fun close() { credential.fill(0) }
    }

    fun saveVerifiedBinding(deviceAddress: String, binding: Binding) = synchronized(lock) {
        save(scope(deviceAddress), binding)
        file(scope(deviceAddress, pending = true)).delete()
    }

    fun savePendingBinding(deviceAddress: String, binding: Binding) = synchronized(lock) {
        save(scope(deviceAddress, pending = true), binding)
    }

    private fun save(scope: ByteArray, binding: Binding) {
        val encoded = seal(key(create = true), scope, binding)
        try {
            check(directory.isDirectory || directory.mkdirs()) { "Huawei storage unavailable" }
            val file = file(scope)
            val stream = file.startWrite()
            try {
                stream.write(encoded)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        } finally {
            encoded.fill(0)
        }
    }

    fun load(deviceAddress: String): Binding? = synchronized(lock) {
        load(scope(deviceAddress))
    }

    fun loadPending(deviceAddress: String): Binding? = synchronized(lock) {
        load(scope(deviceAddress, pending = true))
    }

    private fun load(scope: ByteArray): Binding? {
        val file = file(scope)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
            return null
        val encoded = file.openRead().use { stream ->
            val buffer = ByteArray(128)
            var length = 0
            while (length < buffer.size) {
                val n = stream.read(buffer, length, buffer.size - length)
                if (n < 0) break
                length += n
            }
            require(length < buffer.size) { "Invalid Huawei credential file" }
            buffer.copyOf(length)
        }
        return try {
            open(key(create = false), scope, encoded)
        } finally {
            encoded.fill(0)
        }
    }

    /** Explicit forget operation, isolated to the requested Huawei address. */
    fun forget(deviceAddress: String) = synchronized(lock) {
        file(scope(deviceAddress)).delete()
        file(scope(deviceAddress, pending = true)).delete()
    }

    private fun file(scope: ByteArray): AtomicFile = AtomicFile(File(directory,
        scope.joinToString("") { "%02x".format(it.toInt() and 255) } + ".bin"))

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null)
        if (existing != null) return existing as SecretKey
        check(create) { "Huawei credential key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val ALIAS = "alke.huawei.binding.v1"
        private val lock = Any()
        private const val VERSION: Byte = 1

        internal fun scope(address: String, pending: Boolean = false): ByteArray {
            require(address.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")))
            val domain = if (pending) "alke-huawei-pending-v1:" else "alke-huawei-v1:"
            return MessageDigest.getInstance("SHA-256").digest(
                (domain + address.uppercase(Locale.ROOT)).toByteArray(Charsets.US_ASCII))
        }

        internal fun seal(key: SecretKey, scope: ByteArray, binding: Binding): ByteArray {
            require(scope.size == 32)
            val plain = binding.identity.toByteArray(Charsets.US_ASCII) + binding.credential
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                require(iv.size == 12)
                cipher.updateAAD(byteArrayOf(VERSION) + scope)
                return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
            } finally {
                plain.fill(0)
            }
        }

        internal fun open(key: SecretKey, scope: ByteArray, encoded: ByteArray): Binding {
            require(scope.size == 32 && encoded.size in setOf(77, 93) && encoded[0] == VERSION)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encoded.copyOfRange(1, 13)))
            cipher.updateAAD(byteArrayOf(VERSION) + scope)
            val plain = cipher.doFinal(encoded, 13, encoded.size - 13)
            try {
                require(plain.size == 48 || plain.size == 64)
                return Binding(plain.copyOfRange(0, 32).toString(Charsets.US_ASCII),
                    plain.copyOfRange(32, plain.size))
            } finally {
                plain.fill(0)
            }
        }
    }
}
