package com.otidotid.alke.ui

import com.otidotid.alke.analytics.AutoWorkoutDetector
import com.otidotid.alke.data.WorkoutRow

// Historical parity helpers retained for old records and regression tests only. No runtime detector or UI.
internal fun mergeAutoDetectSavedRows(
    whoopRows: List<WorkoutRow>,
    computedRows: List<WorkoutRow>,
    appleRows: List<WorkoutRow>,
    healthConnectRows: List<WorkoutRow>,
    liftingRows: List<WorkoutRow>,
    activityFileRows: List<WorkoutRow>,
): List<WorkoutRow> = WorkoutEditing.dedupCrossSource(
    whoopRows + computedRows + appleRows + healthConnectRows + liftingRows + activityFileRows,
)

/** Shadow ground truth is the already-deduped saved set, excluding legacy detector output. */
internal fun autoDetectLabelledSpans(savedRows: List<WorkoutRow>): List<Pair<Long, Long>> =
    savedRows
        .filter { WorkoutEditing.classify(it.source) != WorkoutSource.DETECTED }
        .map { it.startTs to it.endTs }

/** Compare the published baseline with every proposed shadow alternative in stable order. */
internal fun autoDetectShadowPolicies(): List<Double> =
    (AutoWorkoutDetector.shadowSustainedMinutes + AutoWorkoutDetector.minSustainedMin)
        .distinct()
        .sorted()

