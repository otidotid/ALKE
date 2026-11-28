package com.otidotid.alke.multidevice.huawei

import java.io.Closeable
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic portion of the Huawei ISO exchange, independent of transport
 * and JSON. Protocol field ordering and derivation labels are interoperability
 * facts described by upstream GetHiChainRequest and DeviceConfig.HiChain.
 *
 * One instance per operation, never reused. A bind credential is provisional
 * until a separate reconnect succeeds. No persistence or logging here.
 */
internal class HuaweiIsoExchange(
    private val operation: Operation,
    credential: ByteArray,
    clientIdentity: ByteArray,
    private val random: SecureRandom = SecureRandom(),
) : Closeable {
    enum class Operation { BIND, RECONNECT }
    private enum class Phase { INITIAL, PEER_VERIFIED, MAC_VERIFIED, CHALLENGE_SENT, TOKEN_RECEIVED, RESULT_SENT, FINISHED, CLOSED }

    private val base = credential.copyOf()
    private val selfId = clientIdentity.copyOf()
    private val selfSalt = ByteArray(16).also(random::nextBytes)
    private val seed = ByteArray(32).also(random::nextBytes)
    private var phase = Phase.INITIAL
    private var peerSalt = ByteArray(0)
    private var psk = ByteArray(0)
    private var session = ByteArray(0)
    private var challenge = ByteArray(0)
    private var provisional = ByteArray(0)

    init {
        require(base.size in setOf(16, 32))
        require(selfId.size in 1..128)
    }

    class Start(val salt: ByteArray, val seed: ByteArray)
    class Encrypted(val nonce: ByteArray, val bytes: ByteArray)

    fun start(): Start {
        check(phase == Phase.INITIAL)
        return Start(selfSalt.copyOf(), seed.copyOf())
    }

    /** Validate peer proof before returning the client proof. */
    fun acceptPeer(salt: ByteArray, identity: ByteArray, token: ByteArray): ByteArray {
        check(phase == Phase.INITIAL)
        try {
            require(salt.size == 16 && identity.size in 1..128 && token.size == 32)
            psk = hmac(base, seed)
            val expected = hmac(psk, salt + selfSalt + selfId + identity)
            require(MessageDigest.isEqual(token, expected)) { "Peer proof mismatch" }
            peerSalt = salt.copyOf()
            val proof = hmac(psk, selfSalt + peerSalt + identity + selfId)
            phase = Phase.PEER_VERIFIED
            return proof
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun acceptResultMac(mac: ByteArray) {
        check(phase == Phase.PEER_VERIFIED)
        try {
            require(MessageDigest.isEqual(mac, hmac(psk, ByteArray(4)))) { "Result proof mismatch" }
            session = hkdf(psk, selfSalt + peerSalt, "hichain_iso_session_key".toByteArray(Charsets.US_ASCII), 32)
            phase = Phase.MAC_VERIFIED
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun bindChallenge(): Encrypted {
        check(operation == Operation.BIND && phase == Phase.MAC_VERIFIED)
        challenge = ByteArray(16).also(random::nextBytes)
        val result = encrypt(challenge, "hichain_iso_exchange".toByteArray(Charsets.US_ASCII))
        phase = Phase.CHALLENGE_SENT
        return result
    }

    fun acceptBindToken(nonce: ByteArray, ciphertext: ByteArray) {
        check(operation == Operation.BIND && phase == Phase.CHALLENGE_SENT)
        try {
            require(nonce.size == 12 && ciphertext.size in setOf(32, 48))
            provisional = aes(Cipher.DECRYPT_MODE, session, nonce, challenge, ciphertext)
            require(provisional.size in setOf(16, 32))
            phase = Phase.TOKEN_RECEIVED
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun copyPendingBindCredential(): ByteArray {
        check(operation == Operation.BIND && phase == Phase.TOKEN_RECEIVED)
        return provisional.copyOf()
    }

    fun result(): Encrypted {
        check((operation == Operation.BIND && phase == Phase.TOKEN_RECEIVED) ||
            (operation == Operation.RECONNECT && phase == Phase.MAC_VERIFIED))
        val packet = encrypt(ByteArray(4), "hichain_iso_result".toByteArray(Charsets.US_ASCII))
        phase = Phase.RESULT_SENT
        return packet
    }

    /**
     * Call only after the wire state machine validates the matching final
     * acknowledgement (operation/request id/status). In BIND this is a
     * provisional long-term credential, not an authenticated session key.
     */
    fun finish(): ByteArray {
        check(phase == Phase.RESULT_SENT)
        val key = if (operation == Operation.BIND) provisional.copyOf() else
            hkdf(session, selfSalt + peerSalt, "hichain_return_key".toByteArray(Charsets.US_ASCII), 32)
        phase = Phase.FINISHED
        close()
        return key
    }

    private fun encrypt(input: ByteArray, aad: ByteArray): Encrypted {
        val nonce = ByteArray(12).also(random::nextBytes)
        return Encrypted(nonce, aes(Cipher.ENCRYPT_MODE, session, nonce, aad, input))
    }

    override fun close() {
        listOf(base, selfId, selfSalt, seed, peerSalt, psk, session, challenge, provisional).forEach { it.fill(0) }
        phase = Phase.CLOSED
    }

    companion object {
        fun hmac(key: ByteArray, message: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

        /** RFC 5869 extract and expand (SHA-256). */
        fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            require(length in 1..(255 * 32))
            val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
            var block = ByteArray(0)
            val output = ByteArray(length)
            var offset = 0
            var index = 1
            try {
                while (offset < length) {
                    val next = hmac(prk, block + info + byteArrayOf(index++.toByte()))
                    block.fill(0)
                    block = next
                    val n = minOf(32, length - offset)
                    block.copyInto(output, offset, 0, n)
                    offset += n
                }
                return output
            } finally {
                prk.fill(0)
                block.fill(0)
            }
        }

        private fun aes(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, bytes: ByteArray): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(bytes)
            }
    }
}
