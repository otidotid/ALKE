package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitnessAgeAuditContractTest {
    @Test fun missingMetabolicIsExcludedRatherThanConvertedToZero() {
        val result = AlkeWeightedFitnessAge.combine(
            coreAge = 42.0,
            vitalityAge = 40.0,
            metabolicAge = AlkeWeightedFitnessAge.metabolicAge(45.0, null),
            coreConfidence = FitnessAgeConfidence.READY,
        )
        assertEquals(2, result.componentCount)
        assertEquals(0.0, result.effectiveWeight("metabolic"), 0.0)
    }

    @Test fun invalidMetabolicCannotEnterWeightedHeadline() {
        assertNull(AlkeWeightedFitnessAge.metabolicAge(45.0, Double.NaN))
        assertNull(AlkeWeightedFitnessAge.metabolicAge(45.0, -2.1))
        assertNull(AlkeWeightedFitnessAge.metabolicAge(45.0, 4.1))
    }
}
