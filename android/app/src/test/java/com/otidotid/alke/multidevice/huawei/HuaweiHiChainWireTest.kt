package com.otidotid.alke.multidevice.huawei

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
class HuaweiHiChainWireTest {
    private val identity = "00112233445566778899aabbccddeeff"
    private val group = "AB".repeat(32)
    private val requestId = 1234L
    private fun wire(op: HuaweiIsoExchange.Operation) = HuaweiHiChainWire(op, requestId, identity, group, 512)

    private fun response(payload: JSONObject, id: Long = requestId, op: Int = 1): HuaweiLinkFrames.Message {
        val bytes = JSONObject().put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(1)
        var length = bytes.size
        do {
            val low = length and 127
            length = length ushr 7
            out.write(low or if (length == 0) 0 else 128)
        } while (length != 0)
        out.write(bytes)
        out.write(byteArrayOf(2, 1, op.toByte(), 3, 8))
        out.write(ByteBuffer.allocate(8).putLong(id).array())
        out.write(byteArrayOf(4, 1, 0))
        return HuaweiLinkFrames.Message(1, 0x28, out.toByteArray())
    }

    @Test fun bindStartCarriesWireIdentityAndBoundedFrames() {
        val frames = wire(HuaweiIsoExchange.Operation.BIND).start(
            HuaweiIsoExchange.Start(ByteArray(16), ByteArray(32)))
        assertTrue(frames.all { it.size <= 512 })
        val decoder = HuaweiLinkFrames.Decoder()
        val msg = frames.flatMap(decoder::feed).single()
        assertEquals(0x28, msg.command)
        val fields = HuaweiTlv.decode(msg.payload)
        assertEquals(1, HuaweiTlv.unsigned(fields, 2, 1))
        val json = JSONObject(requireNotNull(HuaweiTlv.one(fields, 1)).toString(Charsets.UTF_8))
        assertEquals(1, json.getInt("message"))
        assertEquals(requestId.toString(), json.getString("requestId"))
        assertEquals(identity, json.getString("peerDeviceId"))
        assertEquals(64, json.getJSONObject("payload").getString("seed").length)
    }

    @Test fun reconnectUsesDifferentOperationAndMessageNamespace() {
        val decoder = HuaweiLinkFrames.Decoder()
        val message = wire(HuaweiIsoExchange.Operation.RECONNECT)
            .start(HuaweiIsoExchange.Start(ByteArray(16), ByteArray(32))).flatMap(decoder::feed).single()
        val fields = HuaweiTlv.decode(message.payload)
        assertEquals(2, HuaweiTlv.unsigned(fields, 2, 1))
        val json = JSONObject(requireNotNull(HuaweiTlv.one(fields, 1)).toString(Charsets.UTF_8))
        assertEquals(17, json.getInt("message"))
        assertFalse(json.has("peerDeviceId"))
        assertEquals(group, json.getJSONObject("payload").getString("serviceType"))
    }

    @Test fun peerProofParsingRejectsMismatchesAndErrors() {
        val codec = wire(HuaweiIsoExchange.Operation.BIND)
        val payload = JSONObject().put("isoSalt", "00".repeat(16))
            .put("peerAuthId", "AABB").put("token", "FF".repeat(32))
        val parsed = codec.decode(response(payload)) as HuaweiHiChainWire.Reply.Peer
        assertEquals(16, parsed.salt.size)
        assertEquals(2, parsed.identity.size)
        assertEquals(32, parsed.proof.size)
        assertTrue(runCatching { codec.decode(response(payload, id = requestId + 1)) }.isFailure)
        assertTrue(runCatching { codec.decode(response(payload, op = 2)) }.isFailure)
        assertTrue(runCatching { codec.decode(response(payload.put("errorCode", -1))) }.isFailure)
    }

    @Test fun terminalResponseNeverClaimsAuthentication() {
        val codec = wire(HuaweiIsoExchange.Operation.BIND)
        assertSame(HuaweiHiChainWire.Reply.TerminalUnverified,
            codec.decode(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 2))))
        assertTrue(runCatching {
            codec.decode(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 3)))
        }.isFailure)
    }
}
