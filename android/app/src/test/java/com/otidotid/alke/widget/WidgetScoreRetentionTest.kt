package com.otidotid.alke.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetScoreRetentionTest {
    @Test fun nullResumePushKeepsLastValidScoresOnSameDay() {
        assertEquals(73, WidgetScoreRetention.merge(previous = 73, incoming = null, sameDay = true))
    }

    @Test fun validIncomingScoreReplacesPreviousValue() {
        assertEquals(81, WidgetScoreRetention.merge(previous = 73, incoming = 81, sameDay = true))
    }

    @Test fun dayBoundaryClearsPreviousScoreWhenTodayHasNoValueYet() {
        assertNull(WidgetScoreRetention.merge(previous = 73, incoming = null, sameDay = false))
    }

    @Test fun scoresRemainBounded() {
        assertEquals(100, WidgetScoreRetention.merge(previous = null, incoming = 140, sameDay = true))
        assertEquals(0, WidgetScoreRetention.merge(previous = null, incoming = -10, sameDay = true))
    }
}
