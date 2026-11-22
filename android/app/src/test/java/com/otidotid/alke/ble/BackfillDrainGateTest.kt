package com.otidotid.alke.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fail-safe ordering, stale-generation isolation and large-backlog contracts. */
class BackfillDrainGateTest {
    private fun <T> begin(gate: BackfillDrainGate<T>, value: T) =
        (gate.offer(value) as BackfillDrainGate.Offer.Start).lease

    @Test fun producerBeforeEmptyHandoffKeepsOriginalDrainOrder() {
        val gate = BackfillDrainGate<Int>()
        val lease = begin(gate, 1)
        assertEquals(1, gate.pollOrRelease(lease))
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(2))
        assertEquals(2, gate.pollOrRelease(lease))
        assertNull(gate.pollOrRelease(lease))
    }

    @Test fun producerAfterHandoffStartsFreshOwner() {
        val gate = BackfillDrainGate<Int>()
        val first = begin(gate, 1)
        assertEquals(1, gate.pollOrRelease(first))
        assertNull(gate.pollOrRelease(first))
        val second = begin(gate, 2)
        assertEquals(2, gate.pollOrRelease(second))
        assertNull(gate.pollOrRelease(second))
    }

    @Test fun staleOwnerCannotDrainOrCloseNewGeneration() {
        val gate = BackfillDrainGate<Int>()
        val first = begin(gate, 1)
        gate.reset()
        val second = begin(gate, 2)
        assertFalse(gate.failClosed(first))
        assertNull(gate.pollOrRelease(first))
        gate.release(first)
        assertTrue(gate.isActive(second))
        assertEquals(2, gate.pollOrRelease(second))
        assertNull(gate.pollOrRelease(second))
    }

    @Test fun exceptionClosesOnlyFaultedSessionAndRetainsItsFailureIdentity() {
        val gate = BackfillDrainGate<Int>()
        val failed = begin(gate, 1)
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(2))
        assertEquals(1, gate.pollOrRelease(failed))
        assertTrue(gate.failClosed(failed))
        assertTrue(gate.isFailureFor(failed))
        assertFalse(gate.isActive(failed))
        assertNull(gate.pollOrRelease(failed))
        assertEquals(BackfillDrainGate.Offer.Closed, gate.offer(3))
        gate.reset()
        val retry = begin(gate, 1)
        assertFalse(gate.isFailureFor(failed))
        assertEquals(1, gate.pollOrRelease(retry))
        assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(2))
        assertEquals(2, gate.pollOrRelease(retry))
        assertNull(gate.pollOrRelease(retry))
    }

    @Test fun teardownClosesGateUntilNextGenuineOffload() {
        val gate = BackfillDrainGate<Int>()
        val old = begin(gate, 11)
        gate.clear()
        assertNull(gate.pollOrRelease(old))
        assertEquals(BackfillDrainGate.Offer.Closed, gate.offer(12))
        gate.reset()
        val now = begin(gate, 13)
        assertEquals(13, gate.pollOrRelease(now))
    }

    @Test fun hugeBurstIsMeasuredNotAbortedAtAnUnverifiedThreshold() {
        val gate = BackfillDrainGate<Int>()
        val lease = begin(gate, 0)
        for (i in 1..20_000) {
            assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(i))
        }
        assertEquals(20_001, gate.peakQueuedFrames())
        for (i in 0..20_000) assertEquals(i, gate.pollOrRelease(lease))
        assertNull(gate.pollOrRelease(lease))
        assertEquals(20_001, gate.peakQueuedFrames())
        gate.reset()
        assertEquals(0, gate.peakQueuedFrames())
    }

    @Test fun fiftyThousandFrameBacklogRemainsOrderedWithoutDroppingAnyFrame() {
        val gate = BackfillDrainGate<Int>()
        val lease = begin(gate, 0)
        for (i in 1..50_000) {
            assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(i))
        }
        assertEquals(50_001, gate.peakQueuedFrames())
        for (i in 0..50_000) assertEquals(i, gate.pollOrRelease(lease))
        assertNull(gate.pollOrRelease(lease))
        // A new offload cannot inherit queued frames from the previous generation.
        gate.reset()
        val next = begin(gate, 51_000)
        assertEquals(51_000, gate.pollOrRelease(next))
        assertNull(gate.pollOrRelease(next))
        assertEquals(1, gate.peakQueuedFrames())
    }

    @Test fun interleavedLargeProducerAndConsumerPreservesEveryFrameInOrder() {
        val gate = BackfillDrainGate<Int>()
        val lease = begin(gate, 0)
        assertEquals(0, gate.pollOrRelease(lease))
        for (i in 1..10_000) {
            assertEquals(BackfillDrainGate.Offer.Queued, gate.offer(i))
            assertEquals(i, gate.pollOrRelease(lease))
        }
        assertNull(gate.pollOrRelease(lease))
        assertTrue(gate.peakQueuedFrames() <= 1)
    }
}
