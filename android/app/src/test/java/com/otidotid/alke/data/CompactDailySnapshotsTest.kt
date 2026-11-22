package com.otidotid.alke.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CompactDailySnapshotsTest {
    @Test fun tier2SnapshotDaysAre15Through21Inclusive() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals("2026-10-23" to "2026-10-30", CompactDailySnapshots.window(today))
    }

    @Test fun rawSnapshotUsesIndexedEpochColumnInsteadOfDateFunction() {
        val filter = SnapshotCountBounds.forTable(
            RetentionPlan.requireTable(RetentionPlan.Database.HEALTH, "hrSample"),
            "2026-11-01", "2026-11-08", ZoneId.of("Asia/Jakarta"),
        )
        assertEquals("t.\"ts\" >= ? AND t.\"ts\" < ?", filter.predicate)
        assertEquals(
            LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.of("Asia/Jakarta")).toEpochSecond(),
            filter.arguments[0],
        )
        assertEquals(
            LocalDate.of(2026, 11, 8).atStartOfDay(ZoneId.of("Asia/Jakarta")).toEpochSecond(),
            filter.arguments[1],
        )
    }

    @Test fun springDstDayIsTwentyThreeHoursAndLiftSetUsesSessionIndex() {
        val filter = SnapshotCountBounds.forTable(
            RetentionPlan.requireTable(RetentionPlan.Database.HEALTH, "liftSet"),
            "2026-03-29", "2026-03-30", ZoneId.of("Europe/Berlin"),
        )
        assertEquals("s.startTs >= ? AND s.startTs < ?", filter.predicate)
        assertEquals(23L * 3600L,
            (filter.arguments[1] as Long) - (filter.arguments[0] as Long))
    }

    @Test fun autumnDstDayIsTwentyFiveHoursAndTextDayRemainsLexical() {
        val date = SnapshotCountBounds.forTable(
            RetentionPlan.requireTable(RetentionPlan.Database.HEALTH, "sleepSession"),
            "2026-10-25", "2026-10-26", ZoneId.of("Europe/Berlin"),
        )
        assertEquals(25L * 3600L,
            (date.arguments[1] as Long) - (date.arguments[0] as Long))
        val textDay = SnapshotCountBounds.forTable(
            RetentionPlan.requireTable(RetentionPlan.Database.HEALTH, "dailyMetric"),
            "2026-10-25", "2026-10-26",
        )
        assertEquals("t.\"day\" >= ? AND t.\"day\" < ?", textDay.predicate)
        assertEquals("2026-10-25", textDay.arguments[0])
        assertEquals("2026-10-26", textDay.arguments[1])
    }

    @Test fun userKeptLocalBackupIsNotScheduledForDeletion() {
        val table = RetentionPlan.requireTable(RetentionPlan.Database.HEALTH, "dailyMetric")
        // 'ONLINE_ONLY' is a presentation/caching band; no delete operation is provided.
        assertEquals(RetentionPlan.Band.ONLINE_ONLY,
            RetentionPlan.bandFor(table, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 13)))
    }
}
