package com.otidotid.alke.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetScoreDayPolicyTest {
    @Test fun upgradeMissingKeyPreservesFirstSameDayNullPush() {
        assertTrue(WidgetScoreDayPolicy.sameDayForWrite(WidgetScoreDayPolicy.MISSING_DAY, 20L))
    }

    @Test fun writeTreatsRealDayChangeAsDifferentDay() {
        assertFalse(WidgetScoreDayPolicy.sameDayForWrite(19L, 20L))
    }

    @Test fun readNeverShowsMissingOrPreviousDayScoresAsToday() {
        assertFalse(WidgetScoreDayPolicy.visibleOnRead(WidgetScoreDayPolicy.MISSING_DAY, 20L))
        assertFalse(WidgetScoreDayPolicy.visibleOnRead(19L, 20L))
        assertTrue(WidgetScoreDayPolicy.visibleOnRead(20L, 20L))
    }
}
