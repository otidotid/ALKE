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
    private fun allowed(
        mode: PrefetchConnectionMode,
        wifi: Boolean = false,
        cellular: Boolean = false,
        unmetered: Boolean = false,
        validated: Boolean = true,
        batterySaver: Boolean = false,
        dataSaver: Boolean = false,
    ) = CloudPreviewPrefetch.eligible(
        mode, validated, wifi, cellular, unmetered, batterySaver, dataSaver,
    )

    @Test fun unmeteredOnlyDefaultBlocksMeteredWifiAndMobileData() {
        val mode = PrefetchConnectionMode.fromRaw(null)
        assertEquals(PrefetchConnectionMode.UNMETERED_WIFI, mode)
        assertTrue(allowed(mode, wifi = true, unmetered = true))
        assertFalse(allowed(mode, wifi = true, unmetered = false))
        assertFalse(allowed(mode, cellular = true))
    }

    @Test fun tetheringRemainsWifiEvenWhenAndroidMarksItMetered() {
        val mode = PrefetchConnectionMode.ALL_WIFI
        assertTrue(allowed(mode, wifi = true, unmetered = false))
        assertTrue(allowed(mode, wifi = true, unmetered = true))
        assertFalse(allowed(mode, cellular = true, unmetered = false))
    }

    @Test fun explicitMobileDataChoiceAllowsMeteredCellularAndAllWifi() {
        val mode = PrefetchConnectionMode.WIFI_AND_MOBILE
        assertTrue(allowed(mode, wifi = true, unmetered = false))
        assertTrue(allowed(mode, cellular = true, unmetered = false))
        assertFalse(allowed(mode, wifi = false, cellular = false))
        assertFalse(allowed(mode, cellular = true, validated = false))
    }

    @Test fun powerSaverAndAndroidDataSaverStillProtectEveryUserMode() {
        for (mode in PrefetchConnectionMode.entries) {
            assertFalse(allowed(mode, wifi = true, unmetered = true, batterySaver = true))
            assertFalse(allowed(mode, wifi = true, unmetered = true, dataSaver = true))
        }
    }

    @Test fun unknownSavedOptionFallsBackToConservativeDefault() {
        assertEquals(PrefetchConnectionMode.UNMETERED_WIFI,
            PrefetchConnectionMode.fromRaw("unrecognized_value"))
        assertEquals(PrefetchConnectionMode.ALL_WIFI,
            PrefetchConnectionMode.fromRaw("all_wifi"))
        assertEquals(PrefetchConnectionMode.WIFI_AND_MOBILE,
            PrefetchConnectionMode.fromRaw("wifi_and_mobile"))
    }
}
