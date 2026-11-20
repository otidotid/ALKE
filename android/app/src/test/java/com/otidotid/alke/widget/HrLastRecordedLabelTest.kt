package com.otidotid.alke.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class HrLastRecordedLabelTest {
    private val now = 200_000_000L

    @Test
    fun formatsRecentLastRecordedAge() {
        assertEquals(
            "Last recorded · just now",
            HrLastRecordedLabel.text(
                lastHrAtMs = now - 30_000L,
                nowMs = now,
            ),
        )

        assertEquals(
            "Last recorded · 42m ago",
            HrLastRecordedLabel.text(
                lastHrAtMs = now - 42L * 60_000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun formatsHoursAndDays() {
        assertEquals(
            "Last recorded · 3h ago",
            HrLastRecordedLabel.text(
                lastHrAtMs = now - 3L * 60L * 60_000L,
                nowMs = now,
            ),
        )

        assertEquals(
            "Last recorded · 2d ago",
            HrLastRecordedLabel.text(
                lastHrAtMs = now - 2L * 24L * 60L * 60_000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun missingTimestampStillHasHonestLabel() {
        assertEquals(
            "Last recorded",
            HrLastRecordedLabel.text(
                lastHrAtMs = 0L,
                nowMs = now,
            ),
        )
    }
}
