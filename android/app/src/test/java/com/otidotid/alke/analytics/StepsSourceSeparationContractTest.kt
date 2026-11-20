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

    private fun compact(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    @Test
    fun localPublisherWritesRawAndImmediateLocalFinal() {
        val src = compact(
            source(
                "app/src/main/java/com/otidotid/alke/" +
                    "analytics/StepsProductionPublisher.kt"
            )
        )

        assertTrue(
            src.contains(
                "MetricSeriesRow(computedDeviceId, day, " +
                    "\"steps_ai_raw\", estimate.toDouble())"
            )
        )

        assertTrue(
            src.contains(
                "MetricSeriesRow(computedDeviceId, day, " +
                    "\"steps_est\", estimate.toDouble())"
            )
        )

        assertTrue(
            src.contains("Local-first final")
        )
    }

    @Test
    fun localPublisherDoesNotWriteAiMetadata() {
        val src = source(
            "app/src/main/java/com/otidotid/alke/" +
                "analytics/StepsProductionPublisher.kt"
        )

        assertFalse(
            src.contains("\"steps_ai_factor\"")
        )

        assertFalse(
            src.contains("\"steps_ai_confidence\"")
        )
    }

    @Test
    fun aiReadsRawAndChecksGenerationBeforeFinalCommit() {
        val src = compact(
            source(
                "app/src/main/java/com/otidotid/alke/" +
                    "ui/AppViewModel.kt"
            )
        )

        assertTrue(
            src.contains(
                "latestMetricComputedUnion(" +
                    "activeId, \"steps_ai_raw\")"
            )
        )

        assertTrue(src.contains("rawGeneration"))
        assertTrue(src.contains("latestGeneration"))

        assertTrue(
            src.contains(
                "latestGeneration != rawGeneration"
            )
        )

        assertTrue(
            src.contains("\"steps_est\"")
        )
    }

    @Test
    fun failedRequestCannotBlindlyOverwriteExistingFinal() {
        val src = compact(
            source(
                "app/src/main/java/com/otidotid/alke/" +
                    "ui/AppViewModel.kt"
            )
        )

        assertTrue(
            src.contains(
                "if (final?.day != raw.day)"
            )
        )

        assertTrue(
            src.contains(
                "MetricSeriesRow(computedId, raw.day, " +
                    "\"steps_est\", raw.value)"
            )
        )
    }

    @Test
    fun validationStampIsScopedByDeviceAndDay() {
        val src = source(
            "app/src/main/java/com/otidotid/alke/" +
                "ui/AppViewModel.kt"
        )

        assertTrue(
            src.contains(
                "alke.steps.ai.lastValidatedStamp." +
                    "${'$'}activeId.${'$'}{rawRow.day}"
            )
        )
    }
}
