package com.otidotid.alke.ui

import com.otidotid.alke.analytics.SleepHypopneaRiskEngine

/**
 * Presentation state only: never use the previous morning's classified verdict
 * for a DIFFERENT recorded wake-day. A brief empty DB read during BLE offload
 * is NOT evidence that the already displayed night disappeared.
 */
internal object HypopneaTodaySelection {
    data class State(
        val wakeDay: String?,
        val assessment: SleepHypopneaRiskEngine.Assessment,
    )

    fun choose(previous: State, incoming: State): State = when {
        // An empty result during a day-time reload has no proven new night.
        incoming.wakeDay == null && previous.wakeDay != null -> previous
        // New wake-day replaces previous LOW even if new evidence is insufficient.
        incoming.wakeDay != previous.wakeDay -> incoming
        // On SAME morning, a pinned LOW/MEDIUM/HIGH never flashes PENDING.
        previous.assessment.isReady && !incoming.assessment.isReady -> previous
        else -> incoming
    }
}
