package com.otidotid.alke.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StepsSourceSeparationContractTest {
    private fun source(relative: String): String {
        var root = File(
            System.getProperty("user.dir") ?: "."
        ).canonicalFile

        repeat(6) {
            val candidate = File(root, relative)

            if (candidate.isFile) {
                return candidate.readText()
            }

            root = root.parentFile ?: return@repeat
        }

        error("Source not found: $relative")
    }

    @Test
    fun localPublisherWritesRawKeyOnly() {
        val src = source(
            "app/src/main/java/com/otidotid/alke/" +
                "analytics/StepsProductionPublisher.kt"
        )

        assertTrue(
            src.contains("""key = "steps_ai_raw"""")
        )

        assertFalse(
            src.contains("""key = "steps_est"""")
        )
    }

    @Test
    fun aiReadsRawAndOwnsFinalKey() {
        val src = source(
            "app/src/main/java/com/otidotid/alke/" +
                "ui/AppViewModel.kt"
        )

        assertTrue(
            src.contains(
                """latestMetricComputedUnion(activeId, "steps_ai_raw")"""
            )
        )

        assertTrue(
            src.contains(
                """MetricSeriesRow(computedId, rawRow.day, "steps_est""""
            )
        )

        assertTrue(
            src.contains(
                """MetricSeriesRow(computedId, raw.day, "steps_est", raw.value)"""
            )
        )
    }
}
