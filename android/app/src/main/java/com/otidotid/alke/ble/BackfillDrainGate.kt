package com.otidotid.alke.ble

import java.util.ArrayDeque

/**
 * Ordered offload frame drain, with one owner per connection generation.
 *
 * BLE notifications cannot be paused by Android's GATT callback alone. The WHOOP
 * wire protocol's supported sender-side flow control is not established here:
 * never turn an arbitrary queue threshold into dropped records or a routine
 * disconnect. The high-water counter gives us field measurements before a
 * capacity policy is chosen.
 *
 * On a processing exception, close the current generation and discard only
 * UNPROCESSED local frames. The strap must keep every unacknowledged record for
 * a later offload; WhoopBleClient must end this offload without a success stamp.
 */
internal class BackfillDrainGate<T> {
    internal data class Lease internal constructor(
        internal val generation: Long,
        internal val ownerId: Long,
    )

    sealed interface Offer {
        data class Start(val lease: Lease) : Offer
        data object Queued : Offer
        data object Closed : Offer
    }

    private val lock = Any()
    private val pending = ArrayDeque<T>()
    private var generation = 0L
    private var nextOwnerId = 0L
    private var owner: Lease? = null
    private var closed = false
    private var peakQueuedFrames = 0

    /** Nonblocking for BLE callbacks. A current owner consumes the queued frame. */
    fun offer(item: T): Offer = synchronized(lock) {
        if (closed) return@synchronized Offer.Closed
        pending.addLast(item)
        if (pending.size > peakQueuedFrames) peakQueuedFrames = pending.size
        if (owner != null) return@synchronized Offer.Queued
        Offer.Start(Lease(generation, ++nextOwnerId).also { owner = it })
    }

    /** Consume frames in their original arrival order. Stale leases cannot drain. */
    fun pollOrRelease(lease: Lease): T? = synchronized(lock) {
        if (closed || owner != lease) return@synchronized null
        if (pending.isEmpty()) {
            owner = null
            null
        } else {
            pending.removeFirst()
        }
    }

    /** Close ONLY the generation where the exception happened. */
    fun failClosed(lease: Lease): Boolean = synchronized(lock) {
        if (closed || generation != lease.generation || owner != lease) {
            return@synchronized false
        }
        closeLocked()
        true
    }

    /** Confirm that a scheduled completion belongs to this healthy generation. */
    fun isActive(lease: Lease): Boolean = synchronized(lock) {
        generation == lease.generation && !closed
    }

    /** Only the exact generation that faulted may trigger its deferred teardown. */
    fun isFailureFor(lease: Lease): Boolean = synchronized(lock) {
        closed && generation == lease.generation + 1L
    }

    /** Diagnostic snapshot, with no per-frame logging or scoring side effects. */
    fun peakQueuedFrames(): Int = synchronized(lock) { peakQueuedFrames }

    /** Retire the current offload and invalidate pending frames/leases. */
    fun clear() = synchronized(lock) { closeLocked() }

    /** Called on reconnect and at the beginning of a genuinely new offload. */
    fun reset() = synchronized(lock) {
        closed = false
        generation += 1
        owner = null
        pending.clear()
        peakQueuedFrames = 0
    }

    /** A stale release can never steal a replacement drain's ownership. */
    fun release(lease: Lease) = synchronized(lock) {
        if (owner == lease) owner = null
    }

    private fun closeLocked() {
        closed = true
        generation += 1
        owner = null
        pending.clear()
    }
}
