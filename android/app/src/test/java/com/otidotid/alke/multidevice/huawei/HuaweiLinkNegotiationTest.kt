package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiLinkNegotiationTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun documentedRequestMatchesWireFixture() {
        assertArrayEquals(hex("5a 00 0b 00 01 01 01 00 02 00 03 00 04 00 f1 3b"),
            HuaweiLinkNegotiation.request())
    }

    @Test fun documentedResponseParsesWithoutClaimingAuthentication() {
        val frame = hex("5a 00 26 00 01 01 01 01 02 02 02 00 f4 03 02 00 f4 04 02 00 00 05 12 00 01 bb 7b eb d2 9c 13 03 eb 53 2c c6 9b d1 57 ed 0c 8c ea")
        val message = HuaweiLinkFrames.Decoder().feed(frame).single()
        val result = HuaweiLinkNegotiation.parseLegacyResponse(message)
        assertEquals(2, result.protocolVersion)
        assertEquals(244, result.maxFrameSize)
        assertEquals(244, result.maxLinkSize)
        assertEquals(0, result.connectionInterval)
        assertEquals(1, result.authenticationVersion)
        assertArrayEquals(hex("bb 7b eb d2 9c 13 03 eb 53 2c c6 9b d1 57 ed 0c"), result.nonce)
    }

    @Test fun malformedOrUnknownResponseFailsClosed() {
        listOf(
            HuaweiLinkFrames.Message(1, 19, byteArrayOf()),
            HuaweiLinkFrames.Message(1, 1, byteArrayOf(1, 1, 2, 5, 127)),
            HuaweiLinkFrames.Message(1, 1, byteArrayOf(5, 2, 0, 1)),
            HuaweiLinkFrames.Message(1, 1, byteArrayOf(1, 1, 2, 1, 1, 2))
        ).forEach { assertTrue(runCatching { HuaweiLinkNegotiation.parseLegacyResponse(it) }.isFailure) }
    }
}
