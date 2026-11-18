package com.otidotid.alke.analytics

import com.otidotid.alke.data.MetricSeriesRow
import com.otidotid.alke.data.ScoreInputProvenanceRow

/** Read-only pairing: an old AI factor must not outlive the score calculation that produced it. */
internal fun currentFitnessAgeMetabolicFactor(
    scoreSource: String,
    scoreDay: String,
    scoreProvenance: ScoreInputProvenanceRow?,
    factor: MetricSeriesRow?,
    factorProvenance: ScoreInputProvenanceRow?,
): MetricSeriesRow? {
    if (factor == null || factor.deviceId != scoreSource || factor.day != scoreDay ||
        factor.key != "fitness_factor_metabolic_delta") return null
    val legacyAi = MetabolicFitnessAge.ALGORITHM_VERSION
    val newAi = AlkeWeightedFitnessAge.AI_ALGORITHM_VERSION
    // Composite headlines and their metabolic factors may use different version IDs.
    val scoreAi = scoreProvenance?.algorithmVersion in setOf(legacyAi, newAi)
    val factorAi = factorProvenance?.algorithmVersion == legacyAi
    if (!scoreAi && !factorAi) return factor
    if (!scoreAi || !factorAi) return null
    fun matches(trace: ScoreInputProvenanceRow?, key: String, versions: Set<String>) = trace != null &&
        trace.deviceId == scoreSource && trace.sourceId == scoreSource && trace.day == scoreDay &&
        trace.key == key && trace.algorithmVersion in versions
    if (!matches(scoreProvenance, "fitness_age", setOf(legacyAi, newAi)) ||
        !matches(factorProvenance, factor.key, setOf(legacyAi))) return null
    // Both rows are written with the same captured weekly context, including the immutable request ID.
    val captured = scoreProvenance?.observedInputs?.takeIf { it.isNotBlank() } ?: return null
    return factor.takeIf { factorProvenance?.observedInputs == captured }
}
