package com.otidotid.alke.analytics

/**
 * ALKE's single Fitness Age headline (product wellness estimate, not clinical age).
 *
 * All inputs are ages in YEARS produced by existing, versioned ALKE engines.
 * Different measurement scales must never be averaged directly (percent recovery,
 * raw HRV, steps, BMI or an AI's descriptive text are not ages).
 *
 * Fixed target weights: 60% cardiorespiratory FitnessAgeEngine,
 * 25% VitalityEngine (RHR, sleep, HRV, steps), 15% eligible metabolic/labs.
 * Absent OPTIONAL components are skipped and remaining weights renormalized;
 * the core must pass the original RHR/age/sex readiness gate.
 *
 * These are explicitly product-configured heuristic weights, NOT clinically
 * validated coefficients. Any future recalibration requires a new version.
 */
object AlkeWeightedFitnessAge {
    const val ALGORITHM_VERSION = "alke-fitness-age-weighted-v1"
    const val CORE_WEIGHT = 0.60
    const val VITALITY_WEIGHT = 0.25
    const val METABOLIC_WEIGHT = 0.15

    data class Component(val name: String, val ageYears: Double, val weight: Double)
    data class Result(
        val fitnessAge: Double,
        val components: List<Component>,
        val availableWeight: Double,
        val componentCount: Int,
    ) {
        fun effectiveWeight(name: String): Double =
            components.firstOrNull { it.name == name }?.weight?.div(availableWeight) ?: 0.0
    }

    fun combine(
        coreAge: Double,
        vitalityAge: Double?,
        metabolicAge: Double?,
    ): Result {
        require(coreAge.isFinite() && coreAge > 0.0) { "A computed fitness core is mandatory" }
        fun valid(age: Double?): Double? = age?.takeIf { it.isFinite() && it > 0.0 }
        val parts = buildList {
            add(Component("fitness", coreAge, CORE_WEIGHT))
            valid(vitalityAge)?.let { add(Component("vitality", it, VITALITY_WEIGHT)) }
            valid(metabolicAge)?.let { add(Component("metabolic", it, METABOLIC_WEIGHT)) }
        }
        val total = parts.sumOf { it.weight }
        val years = parts.sumOf { it.ageYears * it.weight } / total
        return Result(
            fitnessAge = years.coerceIn(FitnessAgeEngine.minAge, FitnessAgeEngine.maxAge),
            components = parts,
            availableWeight = total,
            componentCount = parts.size,
        )
    }

    /** Not an AI-produced age: only validated/local policy deltas may enter. */
    fun metabolicAge(chronologicalAge: Double, deltaYears: Double?): Double? =
        deltaYears?.takeIf { it.isFinite() }?.let { chronologicalAge + it }
}
