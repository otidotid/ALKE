package com.otidotid.alke.multidevice.huawei

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
class HuaweiHiChainWireTest {
    private val identity = "00112233445566778899aabbccddeeff"
    private val group = "AB".repeat(32)
    private val requestId = 1234L
    private fun wire(op: HuaweiIsoExchange.Operation) = HuaweiHiChainWire(op, requestId, identity, group, 512)

    private fun response(payload: JSONObject, id: Long = requestId, op: Int = 1): HuaweiLinkFrames.Message {
        val bytes = JSONObject().put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        return HuaweiLinkFrames.Message(1, 0x28, HuaweiTlv.encode(
            HuaweiTlv.Field(1, bytes),
            HuaweiTlv.Field(2, byteArrayOf(op.toByte())),
            HuaweiTlv.Field(3, ByteBuffer.allocate(8).putLong(id).array()),
            HuaweiTlv.Field(4, byteArrayOf(0)),
        ))
    }

    @Test fun classicBindStartFitsOneFrameWithHuaweiWireLength() {
        val frame = HuaweiHiChainWire(HuaweiIsoExchange.Operation.BIND, 1_700_000_000_000L,
            identity, group, 1022).start(HuaweiIsoExchange.Start(ByteArray(16), ByteArray(32))).single()
        assertEquals(695, frame.size)
        assertArrayEquals(byteArrayOf(0x5a, 0x02, 0xb2.toByte(), 0, 1, 0x28, 1, 0x85.toByte(), 0x1f),
            frame.copyOfRange(0, 9))
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
        val result = wire(HuaweiIsoExchange.Operation.RECONNECT)
            .result(HuaweiIsoExchange.Encrypted(ByteArray(12), ByteArray(20)))
            .flatMap(decoder::feed).single()
        val resultFields = HuaweiTlv.decode(result.payload)
        val resultJson = JSONObject(requireNotNull(HuaweiTlv.one(resultFields, 1)).toString(Charsets.UTF_8))
        assertEquals(0x13, resultJson.getInt("message"))
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
        assertTrue(runCatching { codec.decode(response(payload, id = 0)) }.isFailure)
        assertTrue(runCatching { codec.decode(response(payload, op = 2)) }.isFailure)
        assertTrue(runCatching { codec.decode(response(payload.put("errorCode", -1))) }.isFailure)
    }

    @Test fun terminalResponseNeverClaimsAuthentication() {
        val codec = wire(HuaweiIsoExchange.Operation.BIND)
        assertSame(HuaweiHiChainWire.Reply.TerminalUnverified,
            codec.decode(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 2))))
        fun terminal(id: Long) = HuaweiLinkFrames.Message(1, 0x28,
            byteArrayOf(2, 1, 1, 3, 8) + ByteBuffer.allocate(8).putLong(id).array() + byteArrayOf(4, 1, 2))
        assertSame(HuaweiHiChainWire.Reply.TerminalUnverified, codec.decode(terminal(0)))
        assertTrue(runCatching { codec.decode(terminal(requestId + 1)) }.isFailure)
        assertTrue(runCatching {
            codec.decode(HuaweiLinkFrames.Message(1, 0x28, byteArrayOf(4, 1, 3)))
        }.isFailure)
    }
}
