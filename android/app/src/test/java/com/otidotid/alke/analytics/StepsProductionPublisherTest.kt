package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsProductionPublisherTest {
    private val calibration = StepsEstimateEngine.Calibration(
        coefficient = 40.0,
        sampleDays = 14,
        confidence = 0.9,
        manual = false,
    )

    @Test fun publishesCurrentMotionDayWithoutDailyMetricDependency() {
        val rows = StepsProductionPublisher.rows(
            computedDeviceId = "whoop-test-alke",
            motionByDay = linkedMapOf("2026-11-19" to 200.0, "2026-11-20" to 25.0),
            referenceStepsByDay = emptyMap(),
            calibration = calibration,
        )
        assertEquals(listOf("2026-11-19", "2026-11-20"), rows.map { it.day })
        assertEquals(1_000.0, rows.last().value, 0.0)
    }

    @Test fun refreshedMotionReplacesFrozenDailyEstimate() {
        val morning = StepsProductionPublisher.rows(
            "whoop-test-alke", mapOf("2026-11-20" to 25.0), emptyMap(), calibration,
        ).single()
        val afternoon = StepsProductionPublisher.rows(
            "whoop-test-alke", mapOf("2026-11-20" to 100.0), emptyMap(), calibration,
        ).single()
        assertTrue(afternoon.value > morning.value)
        assertEquals(4_000.0, afternoon.value, 0.0)
    }

    @Test fun realReferenceStepsRemainAuthoritative() {
        val rows = StepsProductionPublisher.rows(
            "whoop-test-alke",
            mapOf("2026-11-20" to 100.0),
            mapOf("2026-11-20" to 3_800.0),
            calibration,
        )
        assertTrue(rows.isEmpty())
    }
}
