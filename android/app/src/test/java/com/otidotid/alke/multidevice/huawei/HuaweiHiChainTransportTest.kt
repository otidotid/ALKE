package com.otidotid.alke.multidevice.huawei

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiHiChainTransportTest {
    @Test fun ignoresMusicControlWhilePreservingFragmentedAndPendingReplies() {
        val music = HuaweiLinkFrames.encode(0x25, 0x03, ByteArray(42))
        val linkParams = HuaweiLinkFrames.encode(1, 1, byteArrayOf(1, 1, 2))
        val battery = HuaweiLinkFrames.encode(1, 0x08, byteArrayOf(1, 1, 77))
        val link = ScriptedLink(
            music.copyOfRange(0, 8),
            music.copyOfRange(8, music.size) + linkParams + music + battery,
        )
        val channel = HuaweiHiChainTransport(link) {}

        assertArrayEquals(byteArrayOf(1, 1, 2), channel.receive(1).payload)
        assertEquals(1, channel.trace.unmatched)
        assertTrue(channel.trace.summary().contains("25/03 (42 bytes)"))
        channel.resetTrace()
        assertArrayEquals(byteArrayOf(1, 1, 77), channel.receive(0x08).payload)
        assertEquals(1, channel.trace.unmatched)
        assertTrue(link.writes.isEmpty())
    }

    @Test fun rejectsUnrelatedRepliesOutsideHiChainAndBoundsThemDuringHiChain() {
        val unrelated = HuaweiLinkFrames.encode(1, 0x16, byteArrayOf(1, 1, 1))
        val invalid = HuaweiHiChainTransport(ScriptedLink(unrelated)) {}
        assertThrows(IOException::class.java) { invalid.receive(0x08) }

        val reply = HuaweiLinkFrames.encode(1, 0x28, byteArrayOf(4, 1, 2))
        val tolerated = HuaweiHiChainTransport(ScriptedLink(unrelated + reply)) {}
        assertEquals(0x28, tolerated.receive(0x28).command)
        assertEquals(1, tolerated.trace.unmatched)

        val saturated = HuaweiHiChainTransport(ScriptedLink(*Array(9) { unrelated }, reply)) {}
        assertThrows(IOException::class.java) { saturated.receive(0x28) }
        assertEquals(9, saturated.trace.unmatched)
    }

    @Test fun recordsSocketFailureAndSentFramesWithoutRecordingPayload() {
        val frame = HuaweiLinkFrames.encode(1, 0x28, ByteArray(42))
        val link = ScriptedLink(readFailure = IOException("PRIVATE DETAIL"))
        val channel = HuaweiHiChainTransport(link) {}

        channel.send(listOf(frame))
        assertArrayEquals(frame, link.writes.single())
        assertThrows(IOException::class.java) { channel.receive(0x28) }
        assertEquals(
            "Transport: sent=1 frames/50 bytes; received=0 bytes; unmatched=0; other=none; failure=socket read failed",
            channel.trace.summary(),
        )
    }

    @Test fun splitsSocketWritesAtNegotiatedMtuWithoutChangingTheFrames() {
        val frame = HuaweiLinkFrames.encode(1, 0x28, ByteArray(687))
        val link = ScriptedLink()
        val channel = HuaweiHiChainTransport(link) {}

        assertThrows(IllegalArgumentException::class.java) { channel.setMtu(0) }
        channel.setMtu(514)
        channel.send(listOf(frame))
        channel.setMtu(990)
        channel.send(listOf(frame))

        assertEquals(listOf(514, 181, 695), link.writes.map { it.size })
        assertArrayEquals(frame, link.writes[0] + link.writes[1])
        assertArrayEquals(frame, link.writes[2])
        assertTrue(channel.trace.summary().contains("sent=2 frames/1390 bytes"))
    }

    private class ScriptedLink(
        vararg chunks: ByteArray,
        private val readFailure: IOException? = null,
    ) : HuaweiHiChainTransport.Link {
        private val incoming = ArrayDeque(chunks.asList())
        val writes = mutableListOf<ByteArray>()

        override fun read(buffer: ByteArray): Int {
            val chunk = incoming.removeFirstOrNull() ?: return readFailure?.let { throw it } ?: -1
            chunk.copyInto(buffer)
            return chunk.size
        }

        override fun write(frame: ByteArray) {
            writes += frame.copyOf()
        }

        override fun close() = Unit
    }
}
