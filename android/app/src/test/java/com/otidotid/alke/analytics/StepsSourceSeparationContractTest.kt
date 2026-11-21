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
            src.contains("metricSeriesComputedUnion(") && src.contains("steps_ai_raw")
        )

        assertTrue(src.contains("rawGeneration"))
        assertTrue(src.contains("rawGeneration"))

        assertTrue(
            src.contains("latestRaw?.value?.toBits() != rawRow.value.toBits()")
        )

        assertTrue(
            src.contains("\"steps_est\"")
        )
    }

    @Test
    fun failedRequestCannotBlindlyOverwriteExistingFinal() {
        val src = source(
            "app/src/main/java/com/otidotid/alke/" +
                "ui/AppViewModel.kt"
        )

        // Failed remote validation remains pending.
        // A failed or disabled cloud response must never be committed. A run may
        // skip the current row or stop the batch to avoid retrying an unavailable endpoint.
        assertTrue(
            src.contains("if (!result.remoteAccepted) continue") ||
                src.contains("if (!result.remoteAccepted) break")
        )

        // Success metadata is written only after the transactional
        // result has actually been committed.
        assertTrue(
            src.contains(
                "if (committed)"
            )
        )

        assertTrue(
            src.contains(
                ".putString(stampKey, rawGeneration)"
            )
        )

        // The old failure path must not replace an existing local
        // final with the unvalidated raw input.
        assertTrue(
            !src.contains(
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
