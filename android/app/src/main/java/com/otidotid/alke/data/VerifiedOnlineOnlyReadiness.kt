package com.otidotid.alke.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Patch 15: independent, fail-closed eligibility checks for a future user-initiated offload.
 *
 * A verified monthly ciphertext, or a successful day preview, is NOT enough
 * to prove recovery of the separate food database, scoring, all device/source
 * IDs, or every feature which still reads old records from Room.
 *
 * This class is PURE and NEVER deletes or migrates records. Its RestoreProof
 * values must come from independently executed, on-device restore and
 * rollback tests; never infer a passing boolean from an archive manifest.
 */
internal object VerifiedOnlineOnlyReadiness {
    private val MAX_EVIDENCE_AGE_MS = TimeUnit.HOURS.toMillis(24)

    data class RestoreProof(
        val fullTableCountsAndHashesMatched: Boolean,
        val allSourceDevicesPresent: Boolean,
        val scoringParityMatched: Boolean,
        val crossModuleDeepReadsPassed: Boolean,
        val foodAndHealthRestored: Boolean,
        val rollbackCopyRestored: Boolean,
        val completedAtMs: Long,
        /** Full, encrypted recovery backup containing BOTH database files. */
        val independentEncryptedFullBackupReadBack: Boolean = false,
        /** Rehydration into clean isolated stores, NOT a read-only preview. */
        val cleanStoreRestoreRehearsalPassed: Boolean = false,
        /** Original local state can be recovered after an interrupted offload. */
        val interruptedOffloadRollbackPassed: Boolean = false,
    )

    data class Report(val blockers: List<String>, val eligibleToConsiderOffload: Boolean)

    fun assess(
        requiredMonths: List<YearMonth>,
        today: LocalDate,
        archives: List<GoogleDriveArchiveRecord>,
        restoreProof: RestoreProof? = null,
        userOptedIn: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): Report {
        val blockers = mutableListOf<String>()
        val cutoff = HybridHistoryPolicy.archiveCutoff(today)
        val expectedLastMonth = YearMonth.from(cutoff)
        val sorted = requiredMonths.sorted()

        // The caller must inventory the ENTIRE local history. A hand-selected
        // month or a future-only list must not make missing months invisible.
        if (sorted.isEmpty()) {
            blockers += "Full historical coverage has not been inventoried."
        } else {
            if (sorted.size != sorted.distinct().size) {
                blockers += "Historical month inventory contains duplicates."
            }
            if (sorted.last() != expectedLastMonth ||
                sorted.first().atDay(1).isAfter(cutoff)
            ) {
                blockers += "Historical month inventory does not reach the rolling cutoff."
            }
            if (sorted.zipWithNext().any { (left, right) -> left.plusMonths(1) != right }) {
                blockers += "Historical month inventory has missing months."
            }
        }

        val duplicateArchiveMonths = archives.groupBy { it.month }.filterValues { it.size > 1 }
        if (duplicateArchiveMonths.isNotEmpty()) {
            blockers += "Archive manifest has duplicate or conflicting month entries."
        }
        val indexed = archives.associateBy { it.month }
        for (month in sorted.distinct()) {
            if (month.atDay(1).isAfter(cutoff)) {
                blockers += "Future month cannot establish recovery coverage: $month."
                continue
            }
            val record = indexed[month.toString()]
            val coverage = minOf(month.atEndOfMonth(), cutoff)
            if (record == null || !record.encrypted ||
                record.sha256.length != 64 ||
                !record.sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } ||
                record.driveFileId.isBlank() || record.sizeBytes <= 0L ||
                record.verifiedAtMs <= 0L || record.verifiedAtMs > nowMs ||
                !HybridHistoryPolicy.isCoveredThrough(record.coveredThrough, coverage)
            ) {
                blockers += "Verified encrypted monthly archive missing or incomplete: $month."
            }
        }

        if (restoreProof == null) {
            blockers += "Full table-by-table remote restore and digest parity not verified."
            blockers += "Scoring, multi-device, health/food and deep-history parity not verified."
            blockers += "Independent encrypted full-backup restore not verified."
            blockers += "Rollback and interrupted-offload recovery drills not verified."
        } else {
            if (!restoreProof.fullTableCountsAndHashesMatched) blockers += "Restored table hashes do not match."
            if (!restoreProof.allSourceDevicesPresent) blockers += "All original device identities not restored."
            if (!restoreProof.scoringParityMatched) blockers += "Score-equivalence check incomplete."
            if (!restoreProof.crossModuleDeepReadsPassed) blockers += "Deep history online reads incomplete."
            if (!restoreProof.foodAndHealthRestored) blockers += "Food and health backup recovery incomplete."
            if (!restoreProof.rollbackCopyRestored) blockers += "Rollback recovery drill incomplete."
            if (!restoreProof.independentEncryptedFullBackupReadBack) {
                blockers += "Independent encrypted full backup with both databases was not read back."
            }
            if (!restoreProof.cleanStoreRestoreRehearsalPassed) {
                blockers += "Clean isolated-store restore rehearsal incomplete."
            }
            if (!restoreProof.interruptedOffloadRollbackPassed) {
                blockers += "Interrupted-offload rollback rehearsal incomplete."
            }
            if (restoreProof.completedAtMs <= 0L ||
                restoreProof.completedAtMs > nowMs ||
                nowMs - restoreProof.completedAtMs > MAX_EVIDENCE_AGE_MS
            ) {
                blockers += "Recovery proof is missing, future-dated or more than 24 hours old."
            }
            if (archives.any { archive ->
                archive.month in sorted.map(YearMonth::toString) &&
                    archive.verifiedAtMs > restoreProof.completedAtMs
            }) {
                blockers += "Archive changed after the recovery drill; proof is stale."
            }
        }
        if (!userOptedIn) blockers += "Explicit opt-in for eventual history offload missing."
        return Report(blockers, blockers.isEmpty())
    }
}
