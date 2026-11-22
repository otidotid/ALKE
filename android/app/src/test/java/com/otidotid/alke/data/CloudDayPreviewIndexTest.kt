package com.otidotid.alke.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDayPreviewIndexTest {
    private val month = YearMonth.of(2026, 10)
    private val hash = "a".repeat(64)
    private fun part(from: String, through: String, parent: String = hash) =
        CloudDayPreviewPart(from, through, "week-archive-id", "b".repeat(64),
            1752L, 24, parent)
    private fun archive(parts: List<CloudDayPreviewPart>, cutoff: String? = "2026-10-22") =
        GoogleDriveArchiveRecord("2026-10", "monthly-id", hash, 28_000, 900, 48,
            1_800_000_000_000L, encrypted = true, coveredThrough = cutoff,
            dayPreviewParts = parts)

    @Test fun sevenDayRangesCoverPartialMonthsWithoutOverlapOrGaps() {
        val windows = CloudDayPreviewIndex.windows(month, LocalDate.parse("2026-10-22"))
        assertEquals(listOf(
            "2026-10-01" to "2026-10-07",
            "2026-10-08" to "2026-10-14",
            "2026-10-15" to "2026-10-21",
            "2026-10-22" to "2026-10-22",
        ), windows.map { it.from.toString() to it.through.toString() })
        val end = CloudDayPreviewIndex.windows(month, LocalDate.parse("2026-10-31"))
        assertEquals("2026-10-31", end.last().through.toString())
    }

    @Test fun preflightRoutesOnlyVerifiedMatchingDateAndMonthlyVersion() {
        val record = archive(listOf(part("2026-10-08", "2026-10-14")))
        assertEquals("week-archive-id",
            CloudDayPreviewIndex.find(record, LocalDate.parse("2026-10-12"))?.driveFileId)
        assertNull(CloudDayPreviewIndex.find(record, LocalDate.parse("2026-10-15")))
        assertNull(CloudDayPreviewIndex.find(record, LocalDate.parse("2026-11-12")))
        assertNull(CloudDayPreviewIndex.find(record, LocalDate.parse("2026-10-25")))
        assertNull(CloudDayPreviewIndex.find(
            record.copy(sha256 = "f".repeat(64)), LocalDate.parse("2026-10-12")))
    }

    @Test fun legacyMissingManifestSidecarsAndMalformedPartsFallbackToMonthly() {
        val day = LocalDate.parse("2026-10-09")
        assertNull(CloudDayPreviewIndex.find(archive(emptyList()), day))
        assertNull(CloudDayPreviewIndex.find(
            archive(listOf(part("2026-10-08", "2026-10-14"))).copy(encrypted = false), day))
        assertNull(CloudDayPreviewIndex.find(
            archive(listOf(part("2026-10-14", "2026-10-08"))), day))
        assertNull(CloudDayPreviewIndex.find(
            archive(listOf(part("2026-10-08", "2026-10-14", "f".repeat(64)))), day))
        assertNull(CloudDayPreviewIndex.find(
            archive(listOf(part("2026-10-08", "2026-10-14").copy(sizeBytes = Long.MAX_VALUE))), day))
    }
}
