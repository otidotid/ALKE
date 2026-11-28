package com.otidotid.alke.multidevice.huawei

import java.io.Closeable

/**
 * Single-use ISO transaction. The caller supplies a legitimate first-bind
 * credential or a previously verified reconnect credential. No credentials
 * are read from storage, and no connection status is published here.
 *
 * A terminal packet is not a cryptographic ACK. A separate authenticated
 * product read is required before publishing connected or storing a bind
 * credential. A terminal may release a provisional key only to the opt-in
 * verification coordinator, not to the registry or persistent storage.
 */
internal class HuaweiHiChainSession(
    private val operation: HuaweiIsoExchange.Operation,
    credential: ByteArray,
    identity: String,
    group: String,
    requestId: Long,
    frameBudget: Int,
    private val allowProvisionalReadback: Boolean = false,
) : Closeable {
    private val wire = HuaweiHiChainWire(operation, requestId, identity, group, frameBudget)
    private val exchange = HuaweiIsoExchange(operation, credential, identity.toByteArray(Charsets.US_ASCII))
    enum class Phase { NEW, WAIT_PEER, WAIT_MAC, WAIT_TOKEN, WAIT_TERMINAL, TERMINAL_UNVERIFIED, CLOSED }
    private var candidate: ByteArray? = null
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
                // This is NOT authenticated. Only the experimental coordinator may
                // take the candidate for a subsequent proof/readback on this transport.
                if (allowProvisionalReadback) candidate = exchange.finish()
                else exchange.close()
                phase = Phase.TERMINAL_UNVERIFIED
                emptyList()
            }
            else -> error("HiChain transaction is not awaiting a response")
        }
    }

    /** One-time transfer for the coordinator, never a connected-state assertion. */
    fun takeProvisionalForVerification(): ByteArray = guarded {
        check(phase == Phase.TERMINAL_UNVERIFIED && allowProvisionalReadback)
        checkNotNull(candidate).also { candidate = null }
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
        candidate?.fill(0)
        candidate = null
        exchange.close()
        phase = Phase.CLOSED
    }
}
