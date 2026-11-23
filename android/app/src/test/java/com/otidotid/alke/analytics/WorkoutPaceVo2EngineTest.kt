package com.otidotid.alke.analytics

import com.otidotid.alke.data.WorkoutRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pace calibration must never alter a previously matching Nes/Uth number without credible GPS runs. */
class WorkoutPaceVo2EngineTest {
    private val baseStart = 1_792_000_000L

    private fun run(index: Int, withRoute: Boolean = true, avgHr: Int = 150): WorkoutRow {
        val points = (0..50).map { j ->
            RouteMath.LatLng(-6.20, 106.80 + j * 0.00056)
        }
        val start = baseStart + index * 86400L
        return WorkoutRow(
            deviceId = "my-whoop",
            startTs = start,
            endTs = start + 1320,
            durationS = 1320.0,
            sport = "Running",
            source = "manual",
            avgHr = avgHr,
            maxHr = 170,
            distanceM = RouteMath.totalMeters(points),
            routePolyline = if (withRoute) RouteMath.encode(points) else null,
        )
    }

    @Test fun averagePaceComesFromRecordedActiveDurationAndGPSDistance() {
        val r = run(0)
        val pace = WorkoutPaceVo2Engine.paceSecPerKm(r)
        assertNotNull(pace)
        assertEquals(1320.0 / (r.distanceM!! / 1000.0), pace!!, 1e-8)
    }

    @Test fun noPaceReturnsNoAdjustmentAndPreservesExistingBaseline() {
        assertNull(WorkoutPaceVo2Engine.estimate(
            emptyList(), 52.5, 60.0, 180.0, baseStart + 10 * 86400L
        ))
        assertNull(WorkoutPaceVo2Engine.estimate(
            (0..2).map { run(it, withRoute = false) },
            52.5, 60.0, 180.0, baseStart + 10 * 86400L
        ))
    }

    @Test fun oneOrTwoRunsAreNotEvidenceEnough() {
        assertNull(WorkoutPaceVo2Engine.estimate(
            listOf(run(0), run(1)), 52.5, 60.0, 180.0, baseStart + 10 * 86400L
        ))
    }

    @Test fun multipleQualifiedRunsApplyOnlySmallCorrectionToExistingEstimate() {
        val actual = WorkoutPaceVo2Engine.estimate(
            listOf(run(0), run(1), run(2), run(3)),
            baseline = 52.5,
            restingHr = 60.0,
            hrMax = 180.0,
            asOfEpochSeconds = baseStart + 5 * 86400L,
        )
        assertNotNull(actual)
        assertEquals(52.5, actual!!.baseline, 0.0)
        assertTrue(actual.runsUsed >= 3)
        assertTrue(abs(actual.value - 52.5) <= 0.500000001)
        assertTrue(actual.medianPaceSecPerKm in 200.0..700.0)
    }

    @Test fun implausibleHRAndFutureOrStaleSessionsDoNotCount() {
        val sessions = listOf(run(0), run(1), run(2))
        assertNull(WorkoutPaceVo2Engine.estimate(
            sessions, 52.5, 60.0, 180.0, baseStart + 1 * 86400L
        ))
        assertNull(WorkoutPaceVo2Engine.estimate(
            sessions, 52.5, 60.0, 180.0, baseStart + 80 * 86400L
        ))
        assertNull(WorkoutPaceVo2Engine.estimate(
            listOf(run(0, avgHr = 90), run(1, avgHr = 90), run(2, avgHr = 90)),
            52.5, 60.0, 180.0, baseStart + 5 * 86400L
        ))
    }

    @Test fun importedZeppHealthConnectDistanceAndHrQualifyWithoutRoute() {
        val imported = listOf(0, 1, 2).map {
            run(it, withRoute = false).copy(deviceId = "health-connect", source = "health-connect")
        }
        assertNotNull(WorkoutPaceVo2Engine.qualifiedRun(imported.first(), 60.0, 180.0))
        val est = WorkoutPaceVo2Engine.estimate(
            imported, 52.5, 60.0, 180.0, baseStart + 5 * 86400L,
        )
        assertNotNull(est)
        assertTrue(abs(est!!.value - 52.5) <= 0.500000001)
    }

    @Test fun otherImportSourcesAreNotRestrictedToZepp() {
        val apple = run(0, withRoute = false).copy(deviceId = "apple-health", source = "apple-health")
        assertNotNull(WorkoutPaceVo2Engine.qualifiedRun(apple, 60.0, 180.0))
        assertNull(WorkoutPaceVo2Engine.qualifiedRun(apple.copy(avgHr = null), 60.0, 180.0))
    }

    @Test fun untrustedManualDistanceCannotBeUsedAsGPSRunningEvidence() {
        val fake = run(0, withRoute = false)
        assertFalse(WorkoutPaceVo2Engine.qualifiedRun(fake, 60.0, 180.0) != null)
        val treadmill = run(1).copy(sport = "Treadmill run")
        assertNull(WorkoutPaceVo2Engine.qualifiedRun(treadmill, 60.0, 180.0))
    }
}
