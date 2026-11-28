package com.otidotid.alke.multidevice.huawei

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs a prepared ISO transaction on an exclusively owned, already connected
 * transport. Never called from the main thread. The caller must close its
 * transport on cancellation. No concurrent commands are allowed during auth.
 */
internal object HuaweiHiChainTransport {
    interface Link : Closeable {
        fun read(buffer: ByteArray): Int
        fun write(frame: ByteArray)
    }

    enum class Outcome { TERMINAL_UNVERIFIED }

    fun run(link: Link, session: HuaweiHiChainSession, timeoutSeconds: Long = 30): Outcome {
        require(timeoutSeconds in 1..60)
        val expired = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val timeout = watchdog.schedule({
            expired.set(true)
            runCatching { link.close() }
        }, timeoutSeconds, TimeUnit.SECONDS)
        try {
            fun send(frames: List<ByteArray>) {
                for (frame in frames) {
                    if (expired.get()) throw IOException("Huawei authentication timeout")
                    link.write(frame)
                }
            }
            send(session.begin())
            val decoder = HuaweiLinkFrames.Decoder()
            val buffer = ByteArray(4096)
            var budget = 131072
            while (!expired.get() && budget > 0) {
                val n = link.read(buffer)
                if (expired.get()) throw IOException("Huawei authentication timeout")
                if (n <= 0 || n > buffer.size) throw IOException("Huawei authentication transport ended")
                budget -= n
                if (budget < 0) throw IOException("Huawei authentication byte limit")
                for (message in decoder.feed(buffer.copyOf(n))) {
                    // Do not dispatch health/settings traffic before authentication.
                    if (message.service != 1 || message.command != 0x28)
                        throw IOException("Unexpected message during Huawei authentication")
                    send(session.receive(message))
                    if (session.phase == HuaweiHiChainSession.Phase.TERMINAL_UNVERIFIED)
                        return Outcome.TERMINAL_UNVERIFIED
                }
            }
            throw IOException("Huawei authentication timeout or byte limit")
        } finally {
            timeout.cancel(false)
            watchdog.shutdownNow()
            session.close()
            link.close()
        }
    }
}
