package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class HuaweiIsoExchangeTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val key = ByteArray(32) { 0x42 }
    private val client = "client".toByteArray()
    private val peer = "watch".toByteArray()
    private val peerSalt = ByteArray(16) { 0x22 }

    @Test fun rfc5869CaseOne() {
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            HuaweiIsoExchange.hkdf(ByteArray(22) { 11 },
                hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42))
    }

    private fun establish(exchange: HuaweiIsoExchange): Pair<ByteArray, ByteArray> {
        val start = exchange.start()
        val psk = HuaweiIsoExchange.hmac(key, start.seed)
        val proof = HuaweiIsoExchange.hmac(psk, peerSalt + start.salt + client + peer)
        val returned = exchange.acceptPeer(peerSalt, peer, proof)
        assertArrayEquals(HuaweiIsoExchange.hmac(psk, start.salt + peerSalt + peer + client), returned)
        exchange.acceptResultMac(HuaweiIsoExchange.hmac(psk, ByteArray(4)))
        val salt = start.salt + peerSalt
        return HuaweiIsoExchange.hkdf(psk, salt, "hichain_iso_session_key".toByteArray(), 32) to salt
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, bytes: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(bytes)
        }

    @Test fun reconnectRequiresBothProofsAndResult() {
        HuaweiIsoExchange(HuaweiIsoExchange.Operation.RECONNECT, key, client).use { exchange ->
            assertTrue(runCatching { exchange.finish() }.isFailure)
            val (session, salt) = establish(exchange)
            val result = exchange.result()
            assertArrayEquals(ByteArray(4), gcm(Cipher.DECRYPT_MODE, session,
                result.nonce, "hichain_iso_result".toByteArray(), result.bytes))
            assertArrayEquals(HuaweiIsoExchange.hkdf(session, salt,
                "hichain_return_key".toByteArray(), 32), exchange.finish())
            assertTrue(runCatching { exchange.finish() }.isFailure)
        }
    }

    @Test fun firstBindReturnsOnlyProvisionalCredential() {
        HuaweiIsoExchange(HuaweiIsoExchange.Operation.BIND, key, client).use { exchange ->
            val (session, _) = establish(exchange)
            val request = exchange.bindChallenge()
            val challenge = gcm(Cipher.DECRYPT_MODE, session, request.nonce,
                "hichain_iso_exchange".toByteArray(), request.bytes)
            val token = ByteArray(32) { 0x34 }
            val nonce = ByteArray(12) { 0x71 }
            exchange.acceptBindToken(nonce, gcm(Cipher.ENCRYPT_MODE, session, nonce, challenge, token))
            exchange.result()
            assertArrayEquals(token, exchange.finish())
        }
    }

    @Test fun wrongPeerProofPermanentlyClosesExchange() {
        HuaweiIsoExchange(HuaweiIsoExchange.Operation.RECONNECT, key, client).use { exchange ->
            exchange.start()
            assertTrue(runCatching { exchange.acceptPeer(peerSalt, peer, ByteArray(32)) }.isFailure)
            assertTrue(runCatching { exchange.start() }.isFailure)
            assertTrue(runCatching { exchange.result() }.isFailure)
        }
    }

    @Test fun incorrectResultMacClosesExchange() {
        HuaweiIsoExchange(HuaweiIsoExchange.Operation.RECONNECT, key, client).use { exchange ->
            val start = exchange.start()
            val psk = HuaweiIsoExchange.hmac(key, start.seed)
            exchange.acceptPeer(peerSalt, peer,
                HuaweiIsoExchange.hmac(psk, peerSalt + start.salt + client + peer))
            assertTrue(runCatching { exchange.acceptResultMac(ByteArray(32)) }.isFailure)
            assertTrue(runCatching { exchange.result() }.isFailure)
        }
    }
}
