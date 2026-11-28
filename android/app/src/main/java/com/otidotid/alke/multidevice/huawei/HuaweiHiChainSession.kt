package com.otidotid.alke.multidevice.huawei

import java.io.Closeable

/**
 * Single-use ISO transaction. The caller supplies a legitimate first-bind
 * credential or a previously verified reconnect credential. No credentials
 * are read from storage, and no connection status is published here.
 *
 * A terminal packet is not a cryptographic ACK. A separate authenticated
 * product read is required before publishing connected or storing a bind
 * credential. An unverified terminal never releases a key.
 */
internal class HuaweiHiChainSession(
    private val operation: HuaweiIsoExchange.Operation,
    credential: ByteArray,
    identity: String,
    group: String,
    requestId: Long,
    frameBudget: Int,
) : Closeable {
    private val wire = HuaweiHiChainWire(operation, requestId, identity, group, frameBudget)
    private val exchange = HuaweiIsoExchange(operation, credential, identity.toByteArray(Charsets.US_ASCII))
    enum class Phase { NEW, WAIT_PEER, WAIT_MAC, WAIT_TOKEN, WAIT_TERMINAL, TERMINAL_UNVERIFIED, CLOSED }
    var phase = Phase.NEW
        private set

    fun begin(): List<ByteArray> = guarded {
        check(phase == Phase.NEW)
        wire.start(exchange.start()).also { phase = Phase.WAIT_PEER }
    }

    /** Returns only the next outbound packets; no response bytes or secrets are logged. */
    fun receive(message: HuaweiLinkFrames.Message): List<ByteArray> = guarded {
        val reply = wire.decode(message)
        when (phase) {
            Phase.WAIT_PEER -> {
                require(reply is HuaweiHiChainWire.Reply.Peer) { "Unexpected peer response" }
                val proof = exchange.acceptPeer(reply.salt, reply.identity, reply.proof)
                try {
                    wire.proof(proof).also { phase = Phase.WAIT_MAC }
                } finally {
                    proof.fill(0)
                }
            }
            Phase.WAIT_MAC -> {
                require(reply is HuaweiHiChainWire.Reply.Mac) { "Unexpected MAC response" }
                exchange.acceptResultMac(reply.bytes)
                if (operation == HuaweiIsoExchange.Operation.BIND) {
                    wire.challenge(exchange.bindChallenge()).also { phase = Phase.WAIT_TOKEN }
                } else {
                    wire.result(exchange.result()).also { phase = Phase.WAIT_TERMINAL }
                }
            }
            Phase.WAIT_TOKEN -> {
                require(reply is HuaweiHiChainWire.Reply.Token) { "Unexpected token response" }
                exchange.acceptBindToken(reply.nonce, reply.bytes)
                wire.result(exchange.result()).also { phase = Phase.WAIT_TERMINAL }
            }
            Phase.WAIT_TERMINAL -> {
                require(reply === HuaweiHiChainWire.Reply.TerminalUnverified) { "Unexpected terminal response" }
                // Until the real GT5 ACK shape is verified, discard all candidate keys.
                exchange.close()
                phase = Phase.TERMINAL_UNVERIFIED
                emptyList()
            }
            else -> error("HiChain transaction is not awaiting a response")
        }
    }

    private inline fun <T> guarded(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    override fun close() {
        exchange.close()
        phase = Phase.CLOSED
    }
}
