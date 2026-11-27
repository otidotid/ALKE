package com.otidotid.alke.analytics

/**
 * The local detector is independent of cloud availability. Device-provided
 * hypnograms remain authoritative; cloud classifications cannot erase
 * an overlapping locally staged episode. Non-overlapping cloud episodes
 * supplement the local record.
 */
internal fun reconcileSleepEpisodes(
    local: List<DetectedSleep>,
    provided: List<DetectedSleep>,
): List<DetectedSleep> {
    fun overlaps(a: DetectedSleep, b: DetectedSleep): Boolean =
        a.start < b.end && b.start < a.end
    val authoritative = provided.filter { it.cloudEpisodeType == null }
    val retained = local.filter { candidate -> authoritative.none { overlaps(it, candidate) } }
    val accepted = (authoritative + retained).toMutableList()
    for (candidate in provided.filter { it.cloudEpisodeType != null }.sortedBy { it.start }) {
        if (accepted.none { overlaps(it, candidate) }) accepted.add(candidate)
    }
    return accepted.sortedBy { it.start }
}
