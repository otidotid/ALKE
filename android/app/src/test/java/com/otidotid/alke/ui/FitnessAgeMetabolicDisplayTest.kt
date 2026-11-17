package com.otidotid.alke.ui

import com.otidotid.alke.data.ScoreInputProvenanceRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessAgeMetabolicDisplayTest {
    private val trace = ScoreInputProvenanceRow(
        deviceId = "strap-alke", day = "2026-11-14", key = "fitness_age", sourceId = "strap-alke",
        algorithmVersion = "fitness-age-ai-metabolic-v1",
    )

    @Test fun aiOriginRequiresTheActualDisplayedPoint() {
        assertTrue(hasAiMetabolicProvenance(trace, "strap-alke", "2026-11-14"))
        assertFalse(hasAiMetabolicProvenance(trace, "other-strap-alke", "2026-11-14"))
        assertFalse(hasAiMetabolicProvenance(trace, "strap-alke", "2026-11-07"))
        assertFalse(hasAiMetabolicProvenance(trace.copy(key = "vo2max_est"), "strap-alke", "2026-11-14"))
    }

    @Test fun localLegacyAndMissingProvenanceDoNotClaimAiOrigin() {
        assertFalse(hasAiMetabolicProvenance(null, "strap-alke", "2026-11-14"))
        assertFalse(hasAiMetabolicProvenance(trace.copy(algorithmVersion = null), "strap-alke", "2026-11-14"))
        assertFalse(hasAiMetabolicProvenance(
            trace.copy(algorithmVersion = "fitness-age-nes-waist-metabolic-v1"), "strap-alke", "2026-11-14",
        ))
    }
}
