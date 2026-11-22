package com.otidotid.alke.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CloudPreviewPrefetchTest {
    private fun part(start: String, end: String) = CloudDayPreviewPart(
        start, end, "remote-$start", "b".repeat(64), 500L, 12, "a".repeat(64))
    private val record = GoogleDriveArchiveRecord(
        "2026-10", "monthly", "a".repeat(64), 2024L, 100, 48, 1000L,
        encrypted = true, coveredThrough = "2026-10-31",
        dayPreviewParts = listOf(
            part("2026-10-01", "2026-10-07"),
            part("2026-10-08", "2026-10-14"),
            part("2026-10-15", "2026-10-21"),
        ))
    @Test fun nextAdjacentWeekIsOneAndNeverCrossesMonth() {
        assertEquals("2026-10-08",
            CloudPreviewPrefetch.neighbor(record, LocalDate.parse("2026-10-02"))?.fromDay)
        assertEquals("2026-10-15",
            CloudPreviewPrefetch.neighbor(record, LocalDate.parse("2026-10-10"))?.fromDay)
        assertEquals("2026-10-08",
            CloudPreviewPrefetch.neighbor(record, LocalDate.parse("2026-10-20"))?.fromDay)
        assertNull(CloudPreviewPrefetch.neighbor(record, LocalDate.parse("2026-10-31")))
    }
    @Test fun neverPrefetchOnMeteredOrBatterySaverOrDataSaver() {
        assertTrue(CloudPreviewPrefetch.eligible(true, false, false))
        assertFalse(CloudPreviewPrefetch.eligible(false, false, false))
        assertFalse(CloudPreviewPrefetch.eligible(true, true, false))
        assertFalse(CloudPreviewPrefetch.eligible(true, false, true))
    }
}
