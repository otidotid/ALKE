package com.otidotid.alke.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class VerifiedOnlineOnlyReadinessTest {
    private val now = LocalDate.parse("2026-11-22")
    private val nowMs = Instant.parse("2026-11-22T09:00:00Z").toEpochMilli()
    private val month = YearMonth.of(2026, 10)
    private val record = GoogleDriveArchiveRecord(
        month = "2026-10", driveFileId = "remote", sha256 = "a".repeat(64),
        sizeBytes = 2048, recordCount = 500, schemaVersion = 48,
        verifiedAtMs = nowMs - 7_200_000L, encrypted = true,
        coveredThrough = "2026-10-31")

    private fun completeProof() = VerifiedOnlineOnlyReadiness.RestoreProof(
        fullTableCountsAndHashesMatched = true,
        allSourceDevicesPresent = true,
        scoringParityMatched = true,
        crossModuleDeepReadsPassed = true,
        foodAndHealthRestored = true,
        rollbackCopyRestored = true,
        completedAtMs = nowMs - 3_600_000L,
        independentEncryptedFullBackupReadBack = true,
        cleanStoreRestoreRehearsalPassed = true,
        interruptedOffloadRollbackPassed = true,
    )

    private fun assess(
        months: List<YearMonth> = listOf(month),
        records: List<GoogleDriveArchiveRecord> = listOf(record),
        proof: VerifiedOnlineOnlyReadiness.RestoreProof? = completeProof(),
        optedIn: Boolean = true,
    ) = VerifiedOnlineOnlyReadiness.assess(months, now, records, proof, optedIn, nowMs)

    @Test fun verifiedMonthlyChecksumIsNotPermissionToDeleteLocalRawData() {
        val r = assess(proof = null, optedIn = false)
        assertFalse(r.eligibleToConsiderOffload)
        assertTrue(r.blockers.any { it.contains("digest parity") })
        assertTrue(r.blockers.any { it.contains("opt-in") })
    }

    @Test fun incompleteMonthlyCoverageAndLegacyUnencryptedFailClosed() {
        assertFalse(assess(records = listOf(record.copy(encrypted = false))).eligibleToConsiderOffload)
        assertFalse(assess(records = listOf(record.copy(coveredThrough = "2026-10-02"))).eligibleToConsiderOffload)
        assertFalse(assess(records = listOf(record.copy(verifiedAtMs = nowMs + 1))).eligibleToConsiderOffload)
    }

    @Test fun independentlyVerifiedAndFreshProofMayOnlyConsiderOffload() {
        // This is eligibility to CONSIDER a separately designed offload,
        // not a delete call: the implementation contains no cleanup operation.
        assertTrue(assess().eligibleToConsiderOffload)
        assertFalse(assess(proof = completeProof().copy(rollbackCopyRestored = false)).eligibleToConsiderOffload)
        assertFalse(assess(proof = completeProof().copy(independentEncryptedFullBackupReadBack = false)).eligibleToConsiderOffload)
        assertFalse(assess(proof = completeProof().copy(cleanStoreRestoreRehearsalPassed = false)).eligibleToConsiderOffload)
        assertFalse(assess(proof = completeProof().copy(interruptedOffloadRollbackPassed = false)).eligibleToConsiderOffload)
    }

    @Test fun incompleteOrForgedMonthInventoryIsRejected() {
        assertFalse(assess(months = emptyList()).eligibleToConsiderOffload)
        assertFalse(assess(months = listOf(month, month)).eligibleToConsiderOffload)
        assertFalse(assess(months = listOf(month.plusMonths(1))).eligibleToConsiderOffload)
        assertFalse(assess(months = listOf(month.minusMonths(2), month)).eligibleToConsiderOffload)
        assertFalse(assess(records = listOf(record, record.copy(driveFileId = "other"))).eligibleToConsiderOffload)
    }

    @Test fun staleFutureAndPostArchiveProofsFailClosed() {
        assertFalse(assess(proof = completeProof().copy(completedAtMs = nowMs - 86_400_001L))
            .eligibleToConsiderOffload)
        assertFalse(assess(proof = completeProof().copy(completedAtMs = nowMs + 1))
            .eligibleToConsiderOffload)
        assertFalse(assess(records = listOf(record.copy(verifiedAtMs = nowMs - 1_000L)))
            .eligibleToConsiderOffload)
    }

    @Test fun existingV610ProofWithoutNewIndependentRestoreChecksFailsClosed() {
        val oldStyle = VerifiedOnlineOnlyReadiness.RestoreProof(
            true, true, true, true, true, true, nowMs - 3_600_000L)
        val status = assess(proof = oldStyle)
        assertFalse(status.eligibleToConsiderOffload)
        assertTrue(status.blockers.any { it.contains("clean isolated-store") })
    }
}
