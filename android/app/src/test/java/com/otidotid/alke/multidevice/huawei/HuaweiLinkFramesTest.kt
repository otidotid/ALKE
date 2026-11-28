package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiLinkFramesTest {
    @Test fun standardXmodemCheckValue() {
        assertEquals(0x31c3, HuaweiLinkFrames.crcXmodem("123456789".toByteArray()))
    }

    @Test fun splitAndConcatenatedTransportReads() {
        val a = HuaweiLinkFrames.encode(1, 2, byteArrayOf(3, 4))
        val b = HuaweiLinkFrames.encode(5, 6, byteArrayOf(7))
        val d = HuaweiLinkFrames.Decoder()
        assertTrue(d.feed(a.copyOfRange(0, 2)).isEmpty())
        val result = d.feed(a.copyOfRange(2, a.size) + b)
        assertEquals(2, result.size)
        assertEquals(1, result[0].service)
        assertEquals(2, result[0].command)
        assertArrayEquals(byteArrayOf(3, 4), result[0].payload)
        assertEquals(5, result[1].service)
        assertEquals(6, result[1].command)
        assertArrayEquals(byteArrayOf(7), result[1].payload)
    }

    @Test fun rejectsCorruptionAndResets() {
        val good = HuaweiLinkFrames.encode(1, 2, byteArrayOf(3))
        val bad = good.copyOf().apply { this[6] = 42 }
        val d = HuaweiLinkFrames.Decoder()
        assertTrue(runCatching { d.feed(bad) }.isFailure)
        assertEquals(1, d.feed(good).size)
    }

    @Test fun slicedPacketsRequireOrderedSequence() {
        fun slice(flag: Int, index: Int, bytes: ByteArray): ByteArray {
            val len = bytes.size + 2
            val content = byteArrayOf(0x5a, (len shr 8).toByte(), len.toByte(),
                flag.toByte(), index.toByte()) + bytes
            val crc = HuaweiLinkFrames.crcXmodem(content)
            return content + byteArrayOf((crc shr 8).toByte(), crc.toByte())
        }
        val start = slice(1, 0, byteArrayOf(1, 2, 3))
        val end = slice(3, 1, byteArrayOf(4, 5))
        val d = HuaweiLinkFrames.Decoder()
        assertTrue(d.feed(start).isEmpty())
        val msg = d.feed(end).single()
        assertEquals(1, msg.service)
        assertEquals(2, msg.command)
        assertArrayEquals(byteArrayOf(3, 4, 5), msg.payload)
        assertTrue(runCatching { d.feed(end) }.isFailure)
    }
}
