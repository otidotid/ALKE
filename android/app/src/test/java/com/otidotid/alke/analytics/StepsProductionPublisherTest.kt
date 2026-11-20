package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsProductionPublisherTest {
    private val calibration = StepsEstimateEngine.Calibration(
        coefficient = 40.0,
        sampleDays = 14,
        confidence = 0.9,
        manual = false,
    )

    @Test
    fun publishesRawAndLocalFinalForEveryMotionDay() {
        val rows = StepsProductionPublisher.rows(
            computedDeviceId = "whoop-test-alke",
            motionByDay = linkedMapOf(
                "2026-11-19" to 200.0,
                "2026-11-20" to 25.0,
            ),
            referenceStepsByDay = emptyMap(),
            calibration = calibration,
        )

        assertEquals(4, rows.size)

        val byDay = rows.groupBy { it.day }

        assertEquals(
            setOf("2026-11-19", "2026-11-20"),
            byDay.keys,
        )

        byDay.forEach { (_, dayRows) ->
            assertEquals(2, dayRows.size)
            assertEquals(
                setOf("steps_ai_raw", "steps_est"),
                dayRows.map { it.key }.toSet(),
            )

            val raw = dayRows.single {
                it.key == "steps_ai_raw"
            }

            val final = dayRows.single {
                it.key == "steps_est"
            }

            assertEquals(raw.value, final.value, 0.0)
        }

        assertEquals(
            1_000.0,
            byDay.getValue("2026-11-20")
                .single { it.key == "steps_est" }
                .value,
            0.0,
        )
    }

    @Test
    fun refreshedMotionReplacesBothRawAndLocalFinal() {
        fun finalFor(motion: Double): Double {
            return StepsProductionPublisher.rows(
                computedDeviceId = "whoop-test-alke",
                motionByDay = mapOf(
                    "2026-11-20" to motion
                ),
                referenceStepsByDay = emptyMap(),
                calibration = calibration,
            ).single {
                it.key == "steps_est"
            }.value
        }

        val morning = finalFor(25.0)
        val afternoon = finalFor(100.0)

        assertTrue(afternoon > morning)
        assertEquals(4_000.0, afternoon, 0.0)
    }

    @Test
    fun rawAndLocalFinalAlwaysMatchBeforeAiValidation() {
        val rows = StepsProductionPublisher.rows(
            computedDeviceId = "whoop-test-alke",
            motionByDay = mapOf(
                "2026-11-20" to 100.0
            ),
            referenceStepsByDay = emptyMap(),
            calibration = calibration,
        )

        val raw = rows.single {
            it.key == "steps_ai_raw"
        }

        val final = rows.single {
            it.key == "steps_est"
        }

        assertEquals(raw.day, final.day)
        assertEquals(raw.deviceId, final.deviceId)
        assertEquals(raw.value, final.value, 0.0)
    }

    @Test
    fun realReferenceStepsRemainAuthoritative() {
        val rows = StepsProductionPublisher.rows(
            computedDeviceId = "whoop-test-alke",
            motionByDay = mapOf(
                "2026-11-20" to 100.0
            ),
            referenceStepsByDay = mapOf(
                "2026-11-20" to 3_800.0
            ),
            calibration = calibration,
        )

        assertTrue(rows.isEmpty())
    }
}
