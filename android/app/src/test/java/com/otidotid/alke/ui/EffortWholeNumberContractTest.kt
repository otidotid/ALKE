package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keep WHOOP-axis Effort as whole points in all owner-visible readouts. */
class EffortWholeNumberContractTest {
    private fun source(name: String): String {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(6) {
            val path = File(current, "app/src/main/java/com/otidotid/alke/ui/$name")
            if (path.isFile) return path.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Missing ALKE UI source: $name")
    }

    @Test fun effortHeroAndWorkoutReadoutsAreWholePoints() {
        assertTrue(source("TodayScreen.kt").contains(
            "format = { it.roundToInt().toString() }"))
        assertTrue(source("LiveWorkoutScreen.kt").contains(
            "format = { v -> v.roundToInt().toString() }"))
        assertTrue(source("WorkoutsScreen.kt").contains(
            "format = { it.roundToInt().toString() }"))
    }

    @Test fun trendsAndExploreShowNoEffortDecimals() {
        val explore = source("TrendsExploreScreen.kt")
        assertTrue(explore.contains("if (key == \"strain\" || decimals == 0)"))
        assertFalse(explore.contains("if (!v.isFinite()) return \",\""))
        assertTrue(source("TrendsScreen.kt").contains(
            "format = { it.roundToInt().toString() }"))
    }
}
