package com.otidotid.alke.multidevice.huawei

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HuaweiHiChainSessionTest {
    private val identity = "00112233445566778899aabbccddeeff"
    private val group = "AB".repeat(32)
    private val key = ByteArray(32) { 42 }
    private val peer = "watch".toByteArray()
    private val peerSalt = ByteArray(16) { 22 }
    private fun session(op: HuaweiIsoExchange.Operation) =
        HuaweiHiChainSession(op, key, identity, group, 1234, 512)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun body(frames: List<ByteArray>): JSONObject {
        val decoder = HuaweiLinkFrames.Decoder()
        val message = frames.flatMap(decoder::feed).single()
        val json = requireNotNull(HuaweiTlv.one(HuaweiTlv.decode(message.payload), 1))
        return JSONObject(json.toString(Charsets.UTF_8)).getJSONObject("payload")
    }
    private fun reply(payload: JSONObject): HuaweiLinkFrames.Message {
        val json = JSONObject().put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        return HuaweiLinkFrames.Message(1, 0x28, HuaweiTlv.encode(
            HuaweiTlv.Field(1, json), HuaweiTlv.Field(4, byteArrayOf(0))))
    }

    private fun throughPeer(s: HuaweiHiChainSession): ByteArray {
        val start = body(s.begin())
        val salt = bytes(start.getString("isoSalt"))
        val psk = HuaweiIsoExchange.hmac(key, bytes(start.getString("seed")))
        val self = identity.toByteArray(Charsets.US_ASCII)
        val token = HuaweiIsoExchange.hmac(psk, peerSalt + salt + self + peer)
        val response = body(s.receive(reply(JSONObject().put("isoSalt", hex(peerSalt))
            .put("peerAuthId", hex(peer)).put("token", hex(token)))))
        assertEquals(hex(HuaweiIsoExchange.hmac(psk, salt + peerSalt + peer + self)), response.getString("token"))
        return psk
    }

    @Test fun reconnectRequiresProofAndMacBeforeTerminal() {
        session(HuaweiIsoExchange.Operation.RECONNECT).use { s ->
            val psk = throughPeer(s)
            assertEquals(HuaweiHiChainSession.Phase.WAIT_MAC, s.phase)
            val result = body(s.receive(reply(JSONObject()
                .put("returnCodeMac", hex(HuaweiIsoExchange.hmac(psk, ByteArray(4)))))))
            assertEquals(40, result.getString("encResult").length)
            assertEquals(HuaweiHiChainSession.Phase.WAIT_TERMINAL, s.phase)
            assertTrue(s.receive(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 2))).isEmpty())
            assertEquals(HuaweiHiChainSession.Phase.TERMINAL_UNVERIFIED, s.phase)
            assertTrue(runCatching { s.begin() }.isFailure)
            assertEquals(HuaweiHiChainSession.Phase.CLOSED, s.phase)
        }
    }

    @Test fun bindRequiresEncryptedTokenBeforeTerminal() {
        session(HuaweiIsoExchange.Operation.BIND).use { s ->
            val psk = throughPeer(s)
            val challenge = body(s.receive(reply(JSONObject()
                .put("returnCodeMac", hex(HuaweiIsoExchange.hmac(psk, ByteArray(4)))))))
            assertEquals(64, challenge.getString("encData").length)
            assertEquals(HuaweiHiChainSession.Phase.WAIT_TOKEN, s.phase)
            assertTrue(runCatching {
                s.receive(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 2)))
            }.isFailure)
            assertEquals(HuaweiHiChainSession.Phase.CLOSED, s.phase)
        }
    }

    @Test fun wrongMacFailsClosed() {
        session(HuaweiIsoExchange.Operation.RECONNECT).use { s ->
            throughPeer(s)
            assertTrue(runCatching {
                s.receive(reply(JSONObject().put("returnCodeMac", "00".repeat(32))))
            }.isFailure)
            assertEquals(HuaweiHiChainSession.Phase.CLOSED, s.phase)
        }
    }

    @Test fun terminalBeforePeerFailsClosed() {
        session(HuaweiIsoExchange.Operation.BIND).use { s ->
            s.begin()
            assertTrue(runCatching {
                s.receive(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 2)))
            }.isFailure)
            assertEquals(HuaweiHiChainSession.Phase.CLOSED, s.phase)
        }
    }
}
