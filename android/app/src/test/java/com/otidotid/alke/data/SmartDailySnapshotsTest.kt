package com.otidotid.alke.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SmartDailySnapshotsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = DailyMetric(
        deviceId = "whoop-strap-alke", day = "2026-11-20",
        totalSleepMin = 414.25, efficiency = 0.987654321098765,
        deepMin = null, remMin = 91.25, lightMin = 210.5,
        disturbances = 3, restingHr = 48, avgHrv = 76.123456789,
        recovery = 80.1234567, strain = 13.7, exerciseCount = 2,
        spo2Pct = 97.0, skinTempDevC = -0.32, respRateBpm = 14.12,
        steps = 9_123, activeKcalEst = 724.55, spo2Red = 810,
        spo2Ir = 730, avgSdnn = 72.31, skinTempC = 33.12,
        sleepHrOnly = false,
    )

    @Test fun roundTripPreservesEveryScalarNullAndOriginalDoubleBits() {
        val encoded = ByteArrayOutputStream()
        SmartDailySnapshotCodec.encode(encoded, SmartDailySnapshotCodec.Snapshot(
            "whoop-strap", 1_800_000_000_000L, listOf(day, day.copy(
                deviceId = "my-whoop", day = "2020-02-29", sleepHrOnly = null,
                steps = null, avgHrv = Double.fromBits(0x7ff8000000000001L),
            )),
        ))
        val result = SmartDailySnapshotCodec.decode(ByteArrayInputStream(encoded.toByteArray()))
        assertEquals("whoop-strap", result.activeSource)
        assertEquals(day, result.rows.first())
        assertEquals(null, result.rows[1].sleepHrOnly)
        assertEquals(null, result.rows[1].steps)
        assertEquals(0x7ff8000000000001L, result.rows[1].avgHrv!!.toRawBits())
    }

    @Test fun sourceIsolationExpiryAndCorruptBytesFailClosedToDatabase() {
        val store = SmartDailySnapshotStore(tmp.root)
        val now = 1_800_000_000_000L
        assertNull(store.read("strap-A", now))
        assertTrue(store.publish("strap-A", listOf(day), now))
        assertEquals(listOf(day), store.read("strap-A", now))
        assertNull(store.read("strap-B", now))
        assertNull(store.read("strap-A", now + 13L * 60L * 60L * 1000L))
        assertTrue(store.publish("strap-A", listOf(day.copy(steps = 456)), now))
        assertEquals(456, store.read("strap-A", now)!!.single().steps)
        java.io.File(tmp.root, "smart-daily-history-v1.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.read("strap-A", now))
    }

    @Test fun entireThreeThousandDayHistoryIsKeptWithoutSilentTruncation() {
        val rows = (0 until 3000).map { idx ->
            day.copy(day = java.time.LocalDate.of(2010, 1, 1)
                .plusDays(idx.toLong()).toString(), steps = idx)
        }
        val store = SmartDailySnapshotStore(tmp.root)
        val now = 1_800_000_000_000L
        assertTrue(store.publish("my-whoop", rows, now))
        assertEquals(rows, store.read("my-whoop", now))
        assertEquals(3000, store.read("my-whoop", now)!!.size)
    }
}
