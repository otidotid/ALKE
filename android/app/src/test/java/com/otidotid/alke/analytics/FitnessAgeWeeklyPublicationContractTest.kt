package com.otidotid.alke.analytics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessAgeWeeklyPublicationContractTest {

    private fun engineSource(): String {
        var root =
            File(
                System.getProperty("user.dir") ?: "."
            ).canonicalFile

        val relative =
            "app/src/main/java/com/otidotid/alke/" +
                "analytics/IntelligenceEngine.kt"

        repeat(6) {
            val candidate = File(root, relative)

            if (candidate.isFile) {
                return candidate.readText()
            }

            root = root.parentFile ?: return@repeat
        }

        error("IntelligenceEngine.kt not found")
    }

    @Test
    fun calculationRunsBeforeWeeklyPublicationGate() {
        val source = engineSource()

        val calculation =
            source.indexOf(
                "val faPts ="
            )

        val publicationGate =
            source.indexOf(
                "val publishFitnessAge ="
            )

        val saturdayGate =
            source.indexOf(
                "newestDay == weeklyDay"
            )

        val publicationWrite =
            source.indexOf(
                "if (publishFitnessAge)"
            )

        assertTrue(calculation >= 0)
        assertTrue(publicationGate > calculation)
        assertTrue(saturdayGate > publicationGate)
        assertTrue(publicationWrite > saturdayGate)
    }
    @Test
    fun existingSaturdayAnchorAndSevenDayInputRemain() {
        val source = engineSource()

        assertTrue(
            source.contains(
                "val weeklyDay = " +
                    "saturdayKeyOnOrBefore(newestDay)"
            )
        )

        assertTrue(
            source.contains(
                "val faGate7 = " +
                    "faGateByDay.values.sortedBy { it.day }." +
                    "takeLast(7)"
            )
        )

        assertTrue(
            source.contains(
                "captureWeeklyLabs("
            )
        )

        assertTrue(
            source.contains(
                "fitnessAgeRows("
            )
        )
    }

    @Test
    fun existingPublishedSaturdayIsNotOverwritten() {
        val source = engineSource()

        // Calculation remains active, but the visible weekly result is
        // published only when the newest logical day is the Saturday key.
        assertTrue(
            source.contains(
                "newestDay == weeklyDay"
            )
        )

        assertTrue(
            source.contains(
                "val publishFitnessAge ="
            )
        )

        assertTrue(
            source.contains(
                "if (publishFitnessAge)"
            )
        )

        // The removed implementation must not add another repository
        // history read inside the Fitness Age publication block.
        assertTrue(
            !source.contains(
                "val fitnessAgePublishedForWeek ="
            )
        )
    }
    @Test
    fun vitalityPipelineStillFollowsFitnessAgeBlock() {
        val source = engineSource()

        val fitnessAgeGate =
            source.indexOf(
                "if (publishFitnessAge)"
            )

        val vitalityCalculation =
            source.indexOf(
                "val currentVitalityRows = vitalityRows("
            )

        val vitalityWrite =
            source.indexOf(
                "currentVitalityRows",
                vitalityCalculation + 1,
            )

        assertTrue(fitnessAgeGate >= 0)
        assertTrue(vitalityCalculation > fitnessAgeGate)
        assertTrue(vitalityWrite > vitalityCalculation)
    }

    @Test
    fun everyFitnessAgePublicationPathUsesSaturdayGate() {
        val source = engineSource()

        val saturdayGateCount =
            Regex(
                """newestDay\s*==\s*weeklyDay"""
            ).findAll(source).count()

        // Main analysis publication plus recomputeFitnessAgeOnly.
        assertTrue(
            saturdayGateCount >= 2
        )

        assertTrue(
            source.contains(
                "if (originalRows.isNotEmpty() && newestDay == weeklyDay)"
            )
        )
    }
}
