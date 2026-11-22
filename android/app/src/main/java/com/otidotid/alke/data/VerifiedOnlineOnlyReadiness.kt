package com.otidotid.alke.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * PR #13/#14 combined: proof-gated readiness for eventual online-only mode.
 *
 * IMPORTANT: This is a READ-ONLY audit; there is NO deletion path or local
 * retention toggle in v610. A monthly preview or encrypted file checksum is
 * NOT equivalent to a full-table recovery drill and scoring-parity audit.
 *
 * A future PR #15 may provide real evidence from verified offline/online
 * restore rehearsals and implement a reversible, user-opted-in offload.
 */
internal object VerifiedOnlineOnlyReadiness {
    data class RestoreProof(
        val fullTableCountsAndHashesMatched: Boolean,
        val allSourceDevicesPresent: Boolean,
        val scoringParityMatched: Boolean,
        val crossModuleDeepReadsPassed: Boolean,
        val foodAndHealthRestored: Boolean,
        val rollbackCopyRestored: Boolean,
        val completedAtMs: Long,
    )

    data class Report(val blockers: List<String>, val eligibleToConsiderOffload: Boolean)

    fun assess(
        requiredMonths: List<YearMonth>,
        today: LocalDate,
        archives: List<GoogleDriveArchiveRecord>,
        restoreProof: RestoreProof? = null,
        userOptedIn: Boolean = false,
    ): Report {
        val blockers = mutableListOf<String>()
        val cutoff = HybridHistoryPolicy.archiveCutoff(today)
        val indexed = archives.associateBy { it.month }
        for (month in requiredMonths.distinct().sorted()) {
            if (month.atDay(1).isAfter(cutoff)) continue
            val record = indexed[month.toString()]
            val coverage = minOf(month.atEndOfMonth(), cutoff)
            if (record == null || !record.encrypted ||
                record.sha256.length != 64 ||
                !record.sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } ||
                record.driveFileId.isBlank() || record.sizeBytes <= 0L ||
                !HybridHistoryPolicy.isCoveredThrough(record.coveredThrough, coverage)
            ) blockers += "Verified encrypted monthly archive missing or incomplete: $month."
        }
        if (requiredMonths.isEmpty()) blockers += "Full historical coverage has not been inventoried."
        if (restoreProof == null) {
            blockers += "Full table-by-table remote restore and digest parity not verified."
            blockers += "Scoring, multi-device, health/food and deep-history parity not verified."
            blockers += "Rollback copy has not passed a restore drill."
        } else {
            if (!restoreProof.fullTableCountsAndHashesMatched) blockers += "Restored table hashes do not match."
            if (!restoreProof.allSourceDevicesPresent) blockers += "All original device identities not restored."
            if (!restoreProof.scoringParityMatched) blockers += "Score-equivalence check incomplete."
            if (!restoreProof.crossModuleDeepReadsPassed) blockers += "Deep history online reads incomplete."
            if (!restoreProof.foodAndHealthRestored) blockers += "Food and health backup recovery incomplete."
            if (!restoreProof.rollbackCopyRestored) blockers += "Rollback recovery drill incomplete."
            if (restoreProof.completedAtMs <= 0L) blockers += "Recovery proof is not dated."
        }
        if (!userOptedIn) blockers += "Explicit opt-in for eventual history offload missing."
        return Report(blockers, blockers.isEmpty())
    }
}
