package com.otidotid.alke.ingest

import com.otidotid.alke.data.WorkoutRow

/**
 * On repeated Health Connect imports, fill missing evidence in the already-stored
 * exact workout rather than discarding it as an interval overlap. All other sources,
 * manually modified content, and existing non-null measurements are untouched.
 */
internal object HealthConnectWorkoutEnrichment {
    fun split(
        imported: List<WorkoutRow>, existing: List<WorkoutRow>,
    ): Pair<List<WorkoutRow>, List<WorkoutRow>> {
        val byKey = existing
            .filter { it.source.equals("health-connect", ignoreCase = true) }
            .associateBy { Triple(it.deviceId, it.startTs, it.sport) }
        val additions = ArrayList<WorkoutRow>()
        val updates = ArrayList<WorkoutRow>()
        for (record in imported) {
            val prior = byKey[Triple(record.deviceId, record.startTs, record.sport)]
            if (prior == null) {
                additions.add(record)
            } else {
                val enriched = prior.copy(
                    avgHr = prior.avgHr ?: record.avgHr,
                    maxHr = prior.maxHr ?: record.maxHr,
                    distanceM = prior.distanceM ?: record.distanceM,
                    routePolyline = prior.routePolyline ?: record.routePolyline,
                    energyKcal = prior.energyKcal ?: record.energyKcal,
                )
                if (enriched != prior) updates.add(enriched)
            }
        }
        return additions to updates
    }
}
