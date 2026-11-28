package com.otidotid.alke.multidevice.huawei

import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Serial LPv2 exchanges on one socket. The owner provides cancellation and closes the link. */
internal class HuaweiHiChainTransport(
    private val link: Link,
    private val ensureActive: () -> Unit,
) {
    interface Link : Closeable {
        fun read(buffer: ByteArray): Int
        fun read(buffer: ByteArray, timeoutMillis: Long): Int = read(buffer)
        fun write(frame: ByteArray)
    }

    private val decoder = HuaweiLinkFrames.Decoder()
    private val pending = ArrayDeque<HuaweiLinkFrames.Message>()
    private val buffer = ByteArray(4096)
    private var remaining = MAX_RECEIVE_BYTES
    private var mtu = MAX_MTU
    private val ignoredLate = mutableSetOf<Pair<Int, Int>>()

    var trace = HuaweiHandshakeTrace()
        private set

    fun resetTrace() {
        trace = HuaweiHandshakeTrace()
        remaining = MAX_RECEIVE_BYTES
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
                try {
                    link.write(if (offset == 0 && end == frame.size) frame else frame.copyOfRange(offset, end))
                } catch (e: IOException) {
                    trace.failure("socket write failed")
                    throw e
                }
                offset = end
            }
            trace.sent(frame.size)
        }
    }

    /** A late optional or one-way response cannot become the next command's reply. */
    fun ignoreLateReply(service: Int, command: Int) {
        require(ignoredLate.size < 16)
        ignoredLate.add(service to command)
    }

    fun receive(command: Int, service: Int = 1, timeoutMillis: Long = 30_000): HuaweiLinkFrames.Message =
        receiveAny(setOf(command), service, timeoutMillis)

    fun receiveAny(commands: Set<Int>, service: Int, timeoutMillis: Long): HuaweiLinkFrames.Message {
        require(commands.isNotEmpty() && timeoutMillis in 1..60_000)
        commands.forEach { ignoredLate.remove(service to it) }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (true) {
            ensureActive()
            while (pending.isEmpty()) {
                val left = deadline - System.nanoTime()
                if (left <= 0) {
                    trace.failure("response timeout")
                    throw SocketTimeoutException("Huawei response deadline")
                }
                readMessages(maxOf(1, TimeUnit.NANOSECONDS.toMillis(left)))
            }

            val message = pending.removeFirst()
            if (message.service == service && message.command in commands) return message
            trace.matches(message, commands.first(), service)
            if (message.service == 0x1a && message.command == 0x06)
                throw HuaweiAccountSelectionRequired()

            val media = message.service == 0x25 && message.command in setOf(1, 3)
            val batteryPush = message.service == 1 && message.command == 0x27
            val late = (message.service to message.command) in ignoredLate
            if ((!media && !batteryPush && !late && commands != setOf(0x28)) || trace.unmatched > MAX_UNMATCHED) {
                throw IOException("Unexpected Huawei response")
            }
        }
    }

    private fun readMessages(timeoutMillis: Long) {
        ensureActive()
        val count = try {
            link.read(buffer, timeoutMillis)
        } catch (e: SocketTimeoutException) {
            trace.failure("response timeout")
            throw e
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
