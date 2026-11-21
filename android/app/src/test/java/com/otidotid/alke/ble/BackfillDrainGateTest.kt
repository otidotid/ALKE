package com.otidotid.alke.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM concurrency/overload contract for the historical-frame drain. */
class BackfillDrainGateTest {
    private fun <T> start(gate: BackfillDrainGate<T>, value: T): BackfillDrainGate.Lease =
        (gate.offer(value) as BackfillDrainGate.Offer.Start).lease

    @Test fun producerBeforeEmptyHandoffLeavesWorkForCurrentOwner() {
        val gate = BackfillDrainGate<Int>()
        val lease = start(gate, 1)
        assertEquals(1, gate.pollOrRelease(lease))
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(2))
        assertEquals(2, gate.pollOrRelease(lease))
        assertNull(gate.pollOrRelease(lease))
    }

    @Test fun producerAfterEmptyHandoffStartsReplacementDrain() {
        val gate = BackfillDrainGate<Int>()
        val firstLease = start(gate, 1)
        assertEquals(1, gate.pollOrRelease(firstLease))
        assertNull(gate.pollOrRelease(firstLease))
        val replacementLease = start(gate, 2)
        assertEquals(2, gate.pollOrRelease(replacementLease))
        assertNull(gate.pollOrRelease(replacementLease))
    }

    @Test fun resetMakesLateOldDrainHarmlessToNewConnection() {
        val gate = BackfillDrainGate<Int>()
        val oldLease = start(gate, 1)
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(99))
        gate.reset()
        val newLease = start(gate, 2)
        assertNull(gate.pollOrRelease(oldLease))
        gate.release(oldLease)
        assertEquals(2, gate.pollOrRelease(newLease))
        assertNull(gate.pollOrRelease(newLease))
    }

    @Test fun exceptionalReleaseLetsNextProducerOwnDrain() {
        val gate = BackfillDrainGate<Int>()
        val failedLease = start(gate, 1)
        assertEquals(1, gate.pollOrRelease(failedLease))
        gate.release(failedLease)
        val replacementLease = start(gate, 2)
        assertEquals(2, gate.pollOrRelease(replacementLease))
        assertNull(gate.pollOrRelease(replacementLease))
    }

    @Test fun largeNormalBacklogDrainsInArrivalOrderWithoutDroppingAnything() {
        val gate = BackfillDrainGate<Int>(maxPendingFrames = 8_192)
        val lease = start(gate, 0)
        for (i in 1..8_192) {
            // Keep a deterministic headroom by draining while the producer fills
            // to model the real BLE callback + single-consumer arrangement.
            if (i % 2 == 0) assertEquals(i - 2, gate.pollOrRelease(lease))
            assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(i))
        }
        // 4,096 values remain queued (plus the most recently offered frame).
        val rest = mutableListOf<Int>()
        while (true) {
            val next = gate.pollOrRelease(lease) ?: break
            rest += next
        }
        assertEquals(8_192, rest.last())
        assertEquals(rest.sorted(), rest)
    }

    @Test fun overloadPoisonsCurrentOffloadWithoutAllowingAnUnprocessedTail() {
        val gate = BackfillDrainGate<Int>(maxPendingFrames = 2)
        val first = start(gate, 1)
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(2))
        assertEquals(BackfillDrainGate.Offer.Overflow, gate.offer(3))
        assertEquals(BackfillDrainGate.Offer.Closed, gate.offer(4))
        assertNull(gate.pollOrRelease(first))
        gate.release(first)
        assertEquals(BackfillDrainGate.Offer.Closed, gate.offer(5))
        gate.reset()
        val retry = start(gate, 1)
        assertEquals(1, gate.pollOrRelease(retry))
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(2))
        assertEquals(2, gate.pollOrRelease(retry))
        assertNull(gate.pollOrRelease(retry))
    }

    @Test fun decoderFailureCannotLetLaterFramesOrStaleLeaseDrain() {
        val gate = BackfillDrainGate<Int>()
        val oldLease = start(gate, 11)
        gate.offer(12)
        assertEquals(11, gate.pollOrRelease(oldLease))
        gate.failClosed()
        assertNull(gate.pollOrRelease(oldLease))
        assertEquals(BackfillDrainGate.Offer.Closed, gate.offer(13))
        gate.release(oldLease)
        gate.reset()
        val fresh = start(gate, 21)
        assertEquals(21, gate.pollOrRelease(fresh))
        assertNull(gate.pollOrRelease(fresh))
    }

    @Test fun invalidQueueLimitIsRejected() {
        val rejected = runCatching { BackfillDrainGate<Int>(maxPendingFrames = 0) }
        assertTrue(rejected.isFailure)
    }
}
