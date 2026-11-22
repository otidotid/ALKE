package com.otidotid.alke.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class VerifiedOnlineOnlyReadinessTest {
    private val now = LocalDate.parse("2026-11-22")
    private val month = YearMonth.of(2026, 10)
    private val record = GoogleDriveArchiveRecord(
        month = "2026-10", driveFileId = "remote", sha256 = "a".repeat(64),
        sizeBytes = 2048, recordCount = 500, schemaVersion = 48,
        verifiedAtMs = 1_800_000_000_000L, encrypted = true,
        coveredThrough = "2026-10-31")
    @Test fun verifiedMonthlyChecksumIsNotPermissionToDeleteLocalRawData() {
        val r = VerifiedOnlineOnlyReadiness.assess(listOf(month), now, listOf(record))
        assertFalse(r.eligibleToConsiderOffload)
        assertTrue(r.blockers.any { it.contains("digest parity") })
        assertTrue(r.blockers.any { it.contains("opt-in") })
    }
    @Test fun incompleteMonthlyCoverageAndLegacyUnencryptedFailClosed() {
        val r = VerifiedOnlineOnlyReadiness.assess(
            listOf(month), now, listOf(record.copy(encrypted = false)),
            userOptedIn = true)
        assertFalse(r.eligibleToConsiderOffload)
        assertTrue(r.blockers.any { it.contains("missing or incomplete") })
        val cutoff = VerifiedOnlineOnlyReadiness.assess(
            listOf(month), now,
            listOf(record.copy(coveredThrough = "2026-10-02")),
            userOptedIn = true)
        assertFalse(cutoff.eligibleToConsiderOffload)
    }
    @Test fun everyVerificationCategoryMustPassBeforeEvenConsideringOffload() {
        val almost = VerifiedOnlineOnlyReadiness.RestoreProof(
            true, true, true, true, true, false, 1_800_000_000_000L)
        assertFalse(VerifiedOnlineOnlyReadiness.assess(
            listOf(month), now, listOf(record), almost, true).eligibleToConsiderOffload)
        val all = almost.copy(rollbackCopyRestored = true)
        assertTrue(VerifiedOnlineOnlyReadiness.assess(
            listOf(month), now, listOf(record), all, true).eligibleToConsiderOffload)
    }
    @Test fun incompleteInventoryMustNeverReportReady() {
        val r = VerifiedOnlineOnlyReadiness.assess(emptyList(), now, listOf(record))
        assertFalse(r.eligibleToConsiderOffload)
    }
}
