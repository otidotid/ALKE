package com.otidotid.alke.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedHistoryReaderTest {
    private val today = LocalDate.parse("2026-11-22")
    private val old = LocalDate.parse("2026-10-11")
    private fun row(day: LocalDate, steps: Int = 101) =
        DailyMetric(deviceId = "my-whoop-alke", day = day.toString(),
            steps = steps, recovery = 71.25)

    private fun archive(coveredThrough: String? = null) = GoogleDriveArchiveRecord(
        month = "2026-10", driveFileId = "remote-file-1", sha256 = "f".repeat(64),
        sizeBytes = 1_000, recordCount = 100, schemaVersion = 43,
        verifiedAtMs = 1_800_000_000_000L, encrypted = true,
        coveredThrough = coveredThrough,
    )

    @Test fun existingLocalWinsEvenForOldCloudEligibleDays() {
        val decision = UnifiedHistoryRouting.decide(old, today, row(old, 101),
            row(old, 99), listOf(archive()))
        assertTrue(decision is UnifiedHistoryRead.Day)
        val day = decision as UnifiedHistoryRead.Day
        assertEquals(UnifiedHistoryRead.Source.LOCAL_DATABASE, day.source)
        assertEquals(101, day.daily.steps)
    }

    @Test fun optionalSnapshotIsUsedOnlyWhenCanonicalDayIsMissing() {
        val choice = UnifiedHistoryRouting.decide(old, today, null, row(old, 73),
            listOf(archive()))
        assertEquals(UnifiedHistoryRead.Source.CACHED_DAILY_SNAPSHOT,
            (choice as UnifiedHistoryRead.Day).source)
        assertEquals(73, choice.daily.steps)
    }

    @Test fun noCloudRequestForRecentOrSnapshotWindow() {
        for (offset in 0L..21L) {
            val day = today.minusDays(offset)
            val choice = UnifiedHistoryRouting.decide(day, today,
                null, null, listOf(archive()))
            assertTrue("offset=$offset", choice is UnifiedHistoryRead.Unavailable)
        }
        assertTrue(UnifiedHistoryRouting.decide(today.plusDays(1), today,
            row(today.plusDays(1)), null, emptyList()) is UnifiedHistoryRead.Unavailable)
    }

    @Test fun missingOldLocalDataRequestsVerifiedCloudOnlyWhenCovered() {
        val allowed = UnifiedHistoryRouting.decide(old, today,
            null, null, listOf(archive("2026-10-20")))
        assertTrue(allowed is UnifiedHistoryRead.CloudRequired)
        assertTrue((allowed as UnifiedHistoryRead.CloudRequired).record.encrypted)

        val notYet = UnifiedHistoryRouting.decide(old, today,
            null, null, listOf(archive("2026-10-10")))
        assertTrue(notYet is UnifiedHistoryRead.Unavailable)
        val bad = UnifiedHistoryRouting.decide(old, today,
            null, null, listOf(archive("not-a-date")))
        assertTrue(bad is UnifiedHistoryRead.Unavailable)
        val absent = UnifiedHistoryRouting.decide(old, today,
            null, null, emptyList())
        assertTrue(absent is UnifiedHistoryRead.Unavailable)
    }

    @Test fun dailyRowsFromDifferentDatesAreNotReturnedAsThisDay() {
        val rowOfYesterday = row(old.minusDays(1))
        val choice = UnifiedHistoryRouting.decide(old, today,
            rowOfYesterday, null, listOf(archive()))
        assertTrue(choice is UnifiedHistoryRead.CloudRequired)
    }

    @Test fun exactTwentyTwoDayBoundaryUsesCloudOnlyIfNoLocalOrSnapshot() {
        val day = today.minusDays(22)
        val month = day.toString().substring(0, 7)
        val rec = archive().copy(month = month)
        val choice = UnifiedHistoryRouting.decide(day, today, null, null, listOf(rec))
        assertTrue(choice is UnifiedHistoryRead.CloudRequired)
    }
}
