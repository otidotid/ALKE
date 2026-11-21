package com.otidotid.alke.ble

import java.util.ArrayDeque

/**
 * Serial ownership gate and queue for historical frames.
 *
 * Enqueueing and the empty-queue ownership handoff share one monitor. A producer therefore either
 * leaves work for the current drain or receives a lease and starts the replacement drain; it can
 * never enqueue into the gap between an empty poll and ownership release.
 *
 * [reset] advances the generation. A coroutine from the previous connection can then neither poll
 * frames from the new connection nor release its owner, even if its `finally` block runs late.
 */
internal class BackfillDrainGate<T>(
    private val maxPendingFrames: Int = DEFAULT_MAX_PENDING_FRAMES,
) {
    init { require(maxPendingFrames > 0) { "maxPendingFrames must be positive" } }

    companion object {
        // ~one to a few MiB for ordinary historical frames. The cap is on QUEUED
        // frames, not already-polled frames; an overwhelmed decoder fails the
        // current offload safely instead of growing RAM without bound.
        const val DEFAULT_MAX_PENDING_FRAMES = 8_192
    }

    sealed interface Offer {
        data class Start(val lease: Lease) : Offer
        data object Queued : Offer
        data object Overflow : Offer
        data object Closed : Offer
    }

    internal data class Lease internal constructor(
        internal val generation: Long,
        internal val ownerId: Long,
    )

    private val lock = Any()
    private val pending = ArrayDeque<T>()
    private var generation = 0L
    private var nextOwnerId = 0L
    private var owner: Lease? = null
    private var closed = false

    /**
     * Enqueue a frame without blocking the BLE callback. On overload, close the
     * session, discard UNPROCESSED frames and invalidate the drain lease. The
     * client must end this offload WITHOUT claiming HISTORY_COMPLETE, so unacked
     * records can be re-offloaded on the next attempt.
     */
    fun offer(item: T): Offer = synchronized(lock) {
        if (closed) return@synchronized Offer.Closed
        if (pending.size >= maxPendingFrames) {
            closeLocked()
            return@synchronized Offer.Overflow
        }
        pending.addLast(item)
        if (owner != null) return@synchronized Offer.Queued
        val lease = Lease(generation, ++nextOwnerId).also { owner = it }
        Offer.Start(lease)
    }

    /** Poison the current offload after a decoder/persistence exception. */
    fun failClosed() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        closed = true
        generation += 1
        owner = null
        pending.clear()
    }

    /**
     * Return the next item for [lease]. An empty queue releases ownership atomically; a stale lease
     * returns null without touching the current connection's owner or queue.
     */
    fun pollOrRelease(lease: Lease): T? = synchronized(lock) {
        if (owner != lease) return@synchronized null
        if (pending.isEmpty()) {
            owner = null
            null
        } else {
            pending.removeFirst()
        }
    }

    /** Release [lease] after an exceptional drain exit. Stale releases are intentionally no-ops. */
    fun release(lease: Lease) = synchronized(lock) {
        if (owner == lease) owner = null
    }

    /** Drop queued frames without invalidating the current connection's drain owner. */
    fun clear() = synchronized(lock) {
        pending.clear()
    }

    /** Begin a new connection generation and invalidate every lease and frame from the old one. */
    fun reset() = synchronized(lock) {
        closed = false
        generation += 1
        owner = null
        pending.clear()
    }
}
