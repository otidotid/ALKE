package com.otidotid.alke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumV626PerformanceTest {
    @Test fun normalizedSparklinePreservesExistingGeometryAndFiltersNonFinite() {
        assertEquals(listOf(0f,0.5f,1f), premiumSparklineFractions(listOf(10.0,Double.NaN,15.0,20.0)))
        assertEquals(listOf(0f,0f), premiumSparklineFractions(listOf(7.0,7.0)))
        assertEquals(emptyList<Float>(), premiumSparklineFractions(listOf(Double.POSITIVE_INFINITY,7.0)))
    }
    @Test fun wearableStatusPreservesAllExistingLabels() {
        assertEquals("Connected",premiumWearableStatus(true,true))
        assertEquals("Pairing",premiumWearableStatus(true,false))
        assertEquals("Not connected",premiumWearableStatus(false,true))
        assertEquals("Not connected",premiumWearableStatus(false,false))
    }
}
