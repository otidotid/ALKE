package com.otidotid.alke.widget

/** Single presentation contract consumed by every ALKE home-screen widget. */
internal data class PremiumWidgetState(
    val heartRate: Int?,
    val heartRateAtMs: Long,
    val heartRateStatus: PremiumHrStatus,
    val recovery: Int?,
    val rest: Int?,
    val effort: Int?,
    val scoreSourceDay: Long?,
    val scoreStatus: String,
    val battery: Int?,
    val connected: Boolean,
)

internal enum class PremiumHrStatus { LIVE, LAST_RECORDED, DISCONNECTED, UNAVAILABLE }

internal object PremiumWidgetStateEngine {
    fun read(snapshot: WidgetSnapshot, nowMs: Long = System.currentTimeMillis()): PremiumWidgetState {
        val age = if (snapshot.heartRateAtMs > 0L) nowMs - snapshot.heartRateAtMs else Long.MAX_VALUE
        val status = when {
            snapshot.heartRate == null -> PremiumHrStatus.UNAVAILABLE
            !snapshot.connected -> PremiumHrStatus.DISCONNECTED
            !snapshot.heartRateStale && age in 0..HrDisplay.LIVE_MS -> PremiumHrStatus.LIVE
            else -> PremiumHrStatus.LAST_RECORDED
        }
        return PremiumWidgetState(
            heartRate = snapshot.heartRate,
            heartRateAtMs = snapshot.heartRateAtMs,
            heartRateStatus = status,
            recovery = snapshot.recoveryPct,
            rest = snapshot.restPct,
            effort = snapshot.effortPct,
            scoreSourceDay = snapshot.scoreSourceDay,
            scoreStatus = snapshot.scoreStatus,
            battery = snapshot.batteryPct,
            connected = snapshot.connected,
        )
    }
}
