package com.otidotid.alke.analytics

object StepsPersistedCalibration {
    fun validOrNull(
        coefficient: Double?,
        sampleDays: Int,
        confidence: Double,
    ): StepsEstimateEngine.Calibration? {
        val value = coefficient ?: return null
        if (!value.isFinite() || value <= 0.0 || sampleDays <= 0) return null
        if (!confidence.isFinite() || confidence !in 0.0..1.0) return null
        return StepsEstimateEngine.Calibration(
            coefficient = value,
            sampleDays = sampleDays,
            confidence = confidence,
            manual = false,
        )
    }
}
