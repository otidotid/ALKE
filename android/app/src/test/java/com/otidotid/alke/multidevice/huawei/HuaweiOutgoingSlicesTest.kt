package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiOutgoingSlicesTest {
    @Test fun largeAuthenticationMessageRoundTripsAcrossSlices() {
        val payload = ByteArray(1100) { (it % 251).toByte() }
        val frames = HuaweiLinkFrames.encodeWithin(1, 0x28, payload, 512)
        assertEquals(3, frames.size)
        assertEquals(listOf(1, 2, 3), frames.map { it[3].toInt() })
        assertEquals(listOf(0, 1, 2), frames.map { it[4].toInt() })
        assertTrue(frames.all { it.size <= 512 })
        val decoder = HuaweiLinkFrames.Decoder()
        val result = frames.flatMap { frame -> frame.asList().chunked(13).flatMap { decoder.feed(it.toByteArray()) } }.single()
        assertEquals(1, result.service)
        assertEquals(0x28, result.command)
        assertArrayEquals(payload, result.payload)
    }

    @Test fun shortMessagesPreserveOriginalFraming() {
        val payload = byteArrayOf(1, 0, 3, 4)
        assertArrayEquals(HuaweiLinkFrames.encode(1, 0x28, payload),
            HuaweiLinkFrames.encodeWithin(1, 0x28, payload, 512).single())
    }

    @Test fun impossibleSliceBudgetFailsBeforeAnyTransmission() {
        assertTrue(runCatching {
            HuaweiLinkFrames.encodeWithin(1, 0x28, ByteArray(1000), 9)
        }.isFailure)
    }
}
