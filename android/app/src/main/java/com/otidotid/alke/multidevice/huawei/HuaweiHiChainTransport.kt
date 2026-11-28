package com.otidotid.alke.multidevice.huawei

import java.io.Closeable
import java.io.IOException

/** Serial LPv2 exchanges on one socket. The owner provides cancellation and closes the link. */
internal class HuaweiHiChainTransport(
    private val link: Link,
    private val ensureActive: () -> Unit,
) {
    interface Link : Closeable {
        fun read(buffer: ByteArray): Int
        fun write(frame: ByteArray)
    }

    private val decoder = HuaweiLinkFrames.Decoder()
    private val pending = ArrayDeque<HuaweiLinkFrames.Message>()
    private val buffer = ByteArray(4096)
    private var remaining = MAX_RECEIVE_BYTES
    private var mtu = MAX_MTU

    var trace = HuaweiHandshakeTrace()
        private set

    fun resetTrace() {
        trace = HuaweiHandshakeTrace()
    }

    /** The negotiated MTU limits individual writes, while the slice size limits LPv2 frames. */
    fun setMtu(mtu: Int) {
        require(mtu in 1..MAX_MTU) { "Invalid Huawei MTU" }
        this.mtu = mtu
    }

    fun send(frames: List<ByteArray>) {
        for (frame in frames) {
            require(frame.isNotEmpty())
            var offset = 0
            while (offset < frame.size) {
                ensureActive()
                val end = minOf(frame.size, offset + mtu)
                link.write(if (offset == 0 && end == frame.size) frame else frame.copyOfRange(offset, end))
                offset = end
            }
            trace.sent(frame.size)
        }
    }

    fun receive(command: Int): HuaweiLinkFrames.Message {
        while (true) {
            ensureActive()
            while (pending.isEmpty()) readMessages()

            val message = pending.removeFirst()
            if (trace.matches(message, command)) return message

            // A watch can send media controls on the shared link. Leave them unhandled here.
            val musicControl = message.service == 0x25 && message.command == 0x03
            if ((!musicControl && command != 0x28) || trace.unmatched > MAX_UNMATCHED) {
                throw IOException("Unexpected Huawei response")
            }
        }
    }

    private fun readMessages() {
        ensureActive()
        val count = try {
            link.read(buffer)
        } catch (e: IOException) {
            trace.failure("socket read failed")
            throw e
        }
        if (count <= 0 || count > buffer.size) {
            trace.failure(if (count <= 0) "socket EOF" else "read interrupted")
            throw IOException("Huawei transport ended")
        }
        trace.received(count)
        ensureActive()
        remaining -= count
        if (remaining < 0) throw IOException("Huawei response limit")
        try {
            pending.addAll(decoder.feed(buffer.copyOf(count)))
        } catch (e: IllegalArgumentException) {
            trace.failure("invalid LPv2 frame")
            throw e
        }
    }

    private companion object {
        const val MAX_UNMATCHED = 8
        const val MAX_RECEIVE_BYTES = 256 * 1024
        const val MAX_MTU = 0xffff
    }
}
