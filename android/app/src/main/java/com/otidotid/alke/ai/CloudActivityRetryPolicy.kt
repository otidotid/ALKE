package com.otidotid.alke.ai

/** Only window bounds and retry metadata are persisted; never sensor samples or credentials. */
internal data class ActivityRetryWindow(val start: Long, val failures: Int, val nextAt: Long, val sensorHash: String? = null)

internal object ActivityRetryPolicy {
    const val MAX_FAILURES = 6
    const val RETENTION = 6 * 86400L
    fun decode(raw: String?, now: Long): List<ActivityRetryWindow> =
        raw.orEmpty().split(',').mapNotNull { item ->
            val p = item.split(':')
            if (p.size !in 3..4) return@mapNotNull null
            val start = p[0].toLongOrNull() ?: return@mapNotNull null
            val failures = p[1].toIntOrNull() ?: return@mapNotNull null
            val nextAt = p[2].toLongOrNull() ?: return@mapNotNull null
            if (start < 0 || start % 900L != 0L || start < (now - RETENTION)/900L*900L ||
                start + 900L > now || failures !in 1..MAX_FAILURES || nextAt < 0) null
            else {
                val hash = p.getOrNull(3)
                if (hash != null && hash != "-" && !hash.matches(Regex("[a-f0-9]{64}"))) null
                else ActivityRetryWindow(start, failures, nextAt, hash)
            }
        }.distinctBy { it.start }.sortedBy { it.start }
    fun encode(items: List<ActivityRetryWindow>) = items.sortedBy { it.start }
        .joinToString(",") { "${it.start}:${it.failures}:${it.nextAt}" + (it.sensorHash?.let { h -> ":$h" } ?: "") }
    fun due(items: List<ActivityRetryWindow>, now: Long, latestEnd: Long): ActivityRetryWindow? =
        items.filter { it.failures < MAX_FAILURES && it.nextAt <= now && it.start + 900L <= latestEnd }
            .minWithOrNull(compareBy<ActivityRetryWindow> { it.nextAt }.thenBy { it.start })
    fun fail(prior: ActivityRetryWindow?, start: Long, now: Long): ActivityRetryWindow {
        val failures = ((prior?.failures ?: 0) + 1).coerceAtMost(MAX_FAILURES)
        val delay = 900L * (1L shl (failures - 1).coerceAtMost(4))
        return ActivityRetryWindow(start, failures, if (failures == MAX_FAILURES) Long.MAX_VALUE else now + delay)
    }
    // Minimum accepted by the cloud protocol; individual bins still require measured evidence.
    fun canSubmit(usable: Int) = usable >= 2
    fun awaitLateSensor(now: Long, end: Long) = now - end < RETENTION
    fun waitForSensor(start: Long, now: Long, hash: String? = null, delay: Long = 900L) =
        ActivityRetryWindow(start,1,now+delay,hash ?: "-")
    fun wakeSensor(items: List<ActivityRetryWindow>, now: Long) = items.map {
        if (it.sensorHash != null && it.nextAt > now) it.copy(nextAt = now) else it
    }
    fun sameEvidence(item: ActivityRetryWindow?, hash: String) = item?.sensorHash == hash
    fun retryParked(items: List<ActivityRetryWindow>, now: Long) =
        items.map { if (it.failures >= MAX_FAILURES) it.copy(failures=1,nextAt=now) else it }
    fun isWindowFailure(code: Int) = code == 502 || code in 200..299
    fun displayStatus(enabled: Boolean, online: Boolean, raw: String): String = when {
        !enabled -> "AI Detection Off"
        !online -> "AI Detection Offline"
        raw.startsWith("Cloud analysis complete") -> "AI Detection Active"
        raw.startsWith("Manual recording") -> "AI Detection Paused"
        raw.startsWith("Waiting for") || raw == "Insufficient sensor data" -> "AI Detection Waiting"
        else -> "AI Detection Unavailable"
    }
}
