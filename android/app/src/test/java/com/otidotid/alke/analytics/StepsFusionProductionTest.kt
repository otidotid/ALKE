package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsFusionProductionTest {
    @Test fun learnsAutomaticCoefficientWithoutManualInput() {
        val points = (1..14).map { day ->
            val motion = day * 10.0
            StepsEstimateEngine.CalibrationPoint(motion, motion * 42.0)
        }
        val result = StepsFusionProduction.fit(points)!!
        assertEquals(42.0, result.calibration.coefficient, 1e-9)
        assertFalse(result.calibration.manual)
        assertEquals(14, result.calibration.sampleDays)
        assertTrue(result.calibration.confidence >= 0.95)
    }

    @Test fun rejectsExtremeWristMotionOutlier() {
        val normal = (1..14).map { day ->
            val motion = day * 10.0
            StepsEstimateEngine.CalibrationPoint(motion, motion * 40.0)
        }
        val outlier = StepsEstimateEngine.CalibrationPoint(2.0, 50_000.0)
        val result = StepsFusionProduction.fit(normal + outlier)!!
        assertEquals(40.0, result.calibration.coefficient, 0.5)
        assertTrue(result.diagnostics.rejectedDays >= 1)
    }

    @Test fun missingReferenceDaysCannotBecomeZeroLabels() {
        val points = listOf(
            StepsEstimateEngine.CalibrationPoint(10.0, 0.0),
            StepsEstimateEngine.CalibrationPoint(20.0, 0.0),
            StepsEstimateEngine.CalibrationPoint(30.0, 0.0),
        )
        assertNull(StepsFusionProduction.fit(points))
    }

    @Test fun threeValidTeacherDaysActivateProductionModel() {
        val points = listOf(
            StepsEstimateEngine.CalibrationPoint(100.0, 4_000.0),
            StepsEstimateEngine.CalibrationPoint(200.0, 8_000.0),
            StepsEstimateEngine.CalibrationPoint(300.0, 12_000.0),
        )
        assertNotNull(StepsFusionProduction.calibrate(points))
    }
}
