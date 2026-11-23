package com.otidotid.alke.ingest

import com.otidotid.alke.data.WorkoutRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectWorkoutEnrichmentTest {
    private fun workout(source: String = "health-connect"): WorkoutRow = WorkoutRow(
        deviceId = "health-connect",
        startTs = 1_792_000_000L,
        endTs = 1_792_001_500L,
        sport = "Running",
        source = source,
        distanceM = 3500.0,
        notes = "Original running session",
    )

    @Test fun reimportCanFillMissingHeartRateWithoutReplacingExistingFields() {
        val old = workout()
        val fresh = old.copy(avgHr = 152, maxHr = 170, distanceM = 3550.0)
        val (newRecords, patched) = HealthConnectWorkoutEnrichment.split(listOf(fresh), listOf(old))
        assertTrue(newRecords.isEmpty())
        assertEquals(1, patched.size)
        assertEquals(152, patched.single().avgHr)
        assertEquals(170, patched.single().maxHr)
        assertEquals(3500.0, patched.single().distanceM!!, 0.0)
        assertEquals(old.notes, patched.single().notes)
    }

    @Test fun reimportNeverErasesPreviouslyAvailableHealthConnectEvidence() {
        val prior = workout().copy(avgHr = 151, maxHr = 173)
        val fresh = workout().copy(avgHr = null, maxHr = null)
        val (added, patched) = HealthConnectWorkoutEnrichment.split(listOf(fresh), listOf(prior))
        assertTrue(added.isEmpty())
        assertTrue(patched.isEmpty())
    }

    @Test fun otherSourcesAreNotReplacedByImportedSession() {
        val nonHc = workout(source = "manual")
        val (added, patched) = HealthConnectWorkoutEnrichment.split(listOf(workout()), listOf(nonHc))
        assertEquals(1, added.size)
        assertTrue(patched.isEmpty())
    }
}
