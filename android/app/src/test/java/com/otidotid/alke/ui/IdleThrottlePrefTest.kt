package com.otidotid.alke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #477/#1005 — the clamp on the idle-throttle threshold.
 *
 * The throttle drops an IDLE link to LOW_POWER, which lengthens the connection interval and can drop the
 * link outright. It has no Settings control on purpose, so the only way to set it is out-of-band on a
 * debug build — which is exactly the situation where a typo'd 95 would arm it at essentially all times
 * rather than at the low-battery edge it is meant for.
 *
 * So the clamp fails CLOSED: anything outside 0 or 10..30 reads as OFF. Failing open would mean a
 * mistyped value quietly degrades the link for as long as it is set.
 */
class IdleThrottlePrefTest {

    /** The offered range passes through unchanged — these are the values every sibling threshold uses. */
    @Test fun inRangeValuesSurvive() {
        for (pct in listOf(10, 15, 20, 25, 30)) {
            assertEquals(pct, AlkePrefs.clampIdleThrottlePct(pct))
        }
        assertEquals(11, AlkePrefs.clampIdleThrottlePct(11))
        assertEquals(29, AlkePrefs.clampIdleThrottlePct(29))
    }

    /** 0 is OFF and must stay 0 rather than being clamped up into the armed range. */
    @Test fun zeroStaysOff() {
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(0))
    }

    /** The foot-gun: a high value would engage the throttle whenever the strap is discharging at all. */
    @Test fun aHighValueReadsAsOffNotAsAlwaysOn() {
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(95))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(100))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(31))
    }

    /** Below the range is off too — 5% of strap battery is close enough to flat that throttling then
     *  buys nothing, and negative values are simply nonsense. */
    @Test fun belowRangeAndNegativeReadAsOff() {
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(9))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(5))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(-1))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(Int.MIN_VALUE))
    }

    /** The boundaries themselves, stated once so a future range change has to touch this line. */
    @Test fun boundariesAreInclusive() {
        assertEquals(10, AlkePrefs.clampIdleThrottlePct(10))
        assertEquals(30, AlkePrefs.clampIdleThrottlePct(30))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(9))
        assertEquals(0, AlkePrefs.clampIdleThrottlePct(31))
    }
}
