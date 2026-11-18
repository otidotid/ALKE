package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlkeWeightedFitnessAgeTest {
    @Test fun completeAgeEquivalentDomainsUseExplicitWeights() {
        val result = AlkeWeightedFitnessAge.combine(40.0, 30.0, 50.0)
        assertEquals(39.0, result.fitnessAge, 1e-12)
        assertEquals(3, result.componentCount)
        assertEquals(1.0, result.availableWeight, 1e-12)
        assertEquals(0.60, result.effectiveWeight("fitness"), 1e-12)
        assertEquals(0.25, result.effectiveWeight("vitality"), 1e-12)
        assertEquals(0.15, result.effectiveWeight("metabolic"), 1e-12)
    }

    @Test fun missingOptionalInputsRenormalizeWithoutInventedReadings() {
        val onlyCore = AlkeWeightedFitnessAge.combine(40.0, null, null)
        assertEquals(40.0, onlyCore.fitnessAge, 1e-12)
        assertEquals(1.0, onlyCore.effectiveWeight("fitness"), 1e-12)
        val coreAndVitality = AlkeWeightedFitnessAge.combine(40.0, 30.0, null)
        assertEquals((40.0 * .60 + 30.0 * .25) / .85,
            coreAndVitality.fitnessAge, 1e-12)
        assertEquals(0.0, coreAndVitality.effectiveWeight("metabolic"), 0.0)
        assertEquals(40.0, AlkeWeightedFitnessAge.combine(
            40.0, Double.NaN, Double.POSITIVE_INFINITY).fitnessAge, 1e-12)
    }

    @Test fun existingEnginesProduceIndependentAgeEquivalentInputs() {
        val age = 40.0
        val core = FitnessAgeEngine.fitnessAge(age, "male", 65.0, 5.0)
        val vitality = VitalityEngine.compute(VitalityEngine.Inputs(
            chronoAge = age, restingHR = 65.0, sleepHours = 7.5,
            sleepConsistency = 0.75, rmssd = 33.0, rmssdNorm = 33.0,
            steps = 7000.0))!!
        val original = vitality.copy()
        val result = AlkeWeightedFitnessAge.combine(
            core, vitality.bodyAge,
            AlkeWeightedFitnessAge.metabolicAge(age, null))
        assertEquals(40.0, result.fitnessAge, 1e-12)
        assertEquals(original, vitality)
    }

    @Test fun onlyFiniteMetabolicDeltasCanBeIncluded() {
        assertEquals(null, AlkeWeightedFitnessAge.metabolicAge(40.0, null))
        assertEquals(null, AlkeWeightedFitnessAge.metabolicAge(40.0, Double.NaN))
        assertEquals(42.0, AlkeWeightedFitnessAge.metabolicAge(40.0, 2.0)!!, 0.0)
        assertFalse(AlkeWeightedFitnessAge.ALGORITHM_VERSION.isEmpty())
        assertTrue(AlkeWeightedFitnessAge.AI_ALGORITHM_VERSION !=
            AlkeWeightedFitnessAge.ALGORITHM_VERSION)
    }
}
