package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StepsPersistedCalibrationTest {
    @Test fun restoresLastValidAutomaticCalibration() {
        val calibration = StepsPersistedCalibration.validOrNull(41.5, 12, 0.86)!!
        assertEquals(41.5, calibration.coefficient, 0.0)
        assertEquals(12, calibration.sampleDays)
        assertEquals(0.86, calibration.confidence, 0.0)
        assertFalse(calibration.manual)
    }

    @Test fun rejectsMissingOrInvalidCalibration() {
        assertNull(StepsPersistedCalibration.validOrNull(null, 12, 0.8))
        assertNull(StepsPersistedCalibration.validOrNull(0.0, 12, 0.8))
        assertNull(StepsPersistedCalibration.validOrNull(40.0, 0, 0.8))
        assertNull(StepsPersistedCalibration.validOrNull(40.0, 12, 1.1))
    }
}
