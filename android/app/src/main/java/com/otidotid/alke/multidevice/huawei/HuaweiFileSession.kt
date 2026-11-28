package com.otidotid.alke.multidevice.huawei

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Experimental, opt-in native file exchange on the existing serialized GCM session.
 * It is intentionally not called by automatic sync before physical validation.
 */
internal class HuaweiFileUnavailable : IOException("Authenticated file unavailable")

internal class HuaweiFileSession(
    private val reads: HuaweiSessionReads,
    private val transport: HuaweiHiChainTransport,
    private val advertised: Set<Int>,
) {
    fun supported() = setOf(HuaweiFileRead.INIT, HuaweiFileRead.INFO,
        HuaweiFileRead.REQUEST, HuaweiFileRead.COMPLETE).all { it in advertised }

    fun read(kind: HuaweiFileRead.Kind, start: Long, end: Long, workout: Int? = null): ByteArray {
        require(supported()) { "Huawei file commands not advertised" }
        val request = HuaweiFileRead.initRequest(kind,start,end,workout)
        val service = HuaweiFileRead.SERVICE
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        fun timeLeft(): Long {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw SocketTimeoutException("Huawei file transfer deadline")
            return minOf(8_000L, maxOf(1L, TimeUnit.NANOSECONDS.toMillis(remaining)))
        }
        fun status(body: ByteArray, initial: Boolean = false) {
            val fields = HuaweiTlv.decode(body)
            HuaweiTlv.one(fields, 0x7f)?.let {
                require(it.size == 4)
                if (!it.contentEquals(byteArrayOf(0,1,0x86.toByte(),0xa0.toByte()))) {
                    if (initial) throw HuaweiFileUnavailable()
                    throw IOException("Huawei file request rejected")
                }
            }
        }
        fun <T> query(command: Int, body: ByteArray, parse: (ByteArray) -> T): T {
            transport.resetTrace()
            transport.send(reads.requestInitialization(service,command,body))
            return reads.receiveInitialization(transport.receive(command,service,timeLeft())).use {
                if (!it.encrypted) throw IOException("Unauthenticated Huawei file metadata")
                status(it.payload,command == HuaweiFileRead.INIT)
                parse(it.payload)
            }
        }
        try {
            val manifest = query(HuaweiFileRead.INIT,request) { HuaweiFileRead.manifest(it,kind,workout) }
            HuaweiFileRead.Assembly(manifest).use { assembly ->
                if (manifest.size > 0) {
                    val params = query(HuaweiFileRead.INFO,HuaweiFileRead.infoRequest(manifest.id)) {
                        HuaweiFileRead.parameters(it,manifest.id)
                    }
                    var packets = 0
                    while (assembly.nextOffset < manifest.size) {
                        // Bound each burst below the transport's per-exchange byte budget.
                        val count = minOf(manifest.size - assembly.nextOffset, params.maxBlock, 32 * 1024)
                        val target = assembly.nextOffset + count
                        transport.resetTrace()
                        transport.send(reads.requestInitialization(service,HuaweiFileRead.REQUEST,
                            HuaweiFileRead.blockRequest(manifest.id,assembly.nextOffset,count)))
                        var acknowledged = false
                        while (!acknowledged || assembly.nextOffset < target) {
                            require(++packets <= 4096) { "Huawei file packet limit" }
                            val msg = transport.receiveAny(setOf(HuaweiFileRead.REQUEST,HuaweiFileRead.BLOCK),
                                service,timeLeft())
                            reads.receiveFileBurst(msg).use { reply ->
                                if (msg.command == HuaweiFileRead.REQUEST) {
                                    require(!acknowledged)
                                    status(reply.payload)
                                    acknowledged = true
                                } else {
                                    val block = HuaweiFileRead.block(reply.payload)
                                    try {
                                        require(block.bytes.size <= params.burst &&
                                            block.bytes.size <= target - assembly.nextOffset)
                                        assembly.append(block)
                                    } finally { block.bytes.fill(0) }
                                }
                            }
                        }
                        reads.finishInitializationWithoutReply(service,HuaweiFileRead.REQUEST)
                    }
                }
                // Transfer-complete notification; no file deletion or settings command.
                timeLeft()
                transport.resetTrace()
                transport.send(reads.requestInitialization(service,HuaweiFileRead.COMPLETE,
                    HuaweiFileRead.completeRequest(manifest.id)))
                reads.finishInitializationWithoutReply(service,HuaweiFileRead.COMPLETE)
                transport.ignoreLateReply(service,HuaweiFileRead.COMPLETE)
                return assembly.finish()
            }
        } catch (e: HuaweiFileUnavailable) {
            // A GCM-authenticated rejection of INIT leaves no transfer in flight.
            throw e
        } catch (e: Exception) {
            // Do not reuse a session with an interrupted or ambiguous burst.
            reads.close()
            throw e
        }
    }
}
