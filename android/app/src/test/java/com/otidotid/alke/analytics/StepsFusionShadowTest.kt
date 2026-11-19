package com.otidotid.alke.analytics

import com.otidotid.alke.data.GravitySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsFusionShadowTest {
    private fun gravity(minute: Long, vectors: List<Double>) = vectors.mapIndexed { index, x ->
        GravitySample("whoop-4", minute * 60 + index, x, 0.0, 1.0)
    }

    @Test fun motionFoldNeverCrossesMinuteBoundary() {
        val rows = gravity(100, listOf(0.0, 1.0)) + gravity(101, listOf(100.0, 101.0))
        val out = StepsFusionShadow.motionByMinute(rows)
        assertEquals(2, out.size)
        assertEquals(1.0, out[0].motion, 1e-9)
        assertEquals(1.0, out[1].motion, 1e-9)
    }

    @Test fun missingTeacherMinuteIsUnknownNotZero() {
        val motion = (0L until 31L).map { StepsFusionShadow.MotionMinute(it, 2, 1.0) }
        val teacher = (0L until 30L).map { StepsFusionShadow.TeacherMinute(it, 20) }
        val fit = StepsFusionShadow.fit(motion, teacher)!!
        val report = StepsFusionShadow.evaluate(motion, teacher, fit)
        assertEquals(30, report.comparisons.size)
        assertEquals(600, report.totalTeacherSteps)
        assertEquals(600, report.totalEstimatedSteps)
    }

    @Test fun tooFewLabelsCannotProduceFit() {
        val motion = (0L until 29L).map { StepsFusionShadow.MotionMinute(it, 2, 1.0) }
        val teacher = (0L until 29L).map { StepsFusionShadow.TeacherMinute(it, 20) }
        assertNull(StepsFusionShadow.fit(motion, teacher))
    }

    @Test fun impossibleTeacherCadenceIsExcluded() {
        val motion = (0L until 31L).map { StepsFusionShadow.MotionMinute(it, 2, 2.0) }
        val teacher = (0L until 30L).map { StepsFusionShadow.TeacherMinute(it, 40) } +
            StepsFusionShadow.TeacherMinute(30, 999)
        val fit = StepsFusionShadow.fit(motion, teacher)!!
        assertEquals(20.0, fit.stepsPerMotionUnit, 1e-9)
        assertEquals(30, fit.labelledMinutes)
    }

    @Test fun robustMedianResistsOneWristMotionOutlier() {
        val motion = (0L until 31L).map { minute ->
            StepsFusionShadow.MotionMinute(minute, 2, if (minute == 30L) 100.0 else 1.0)
        }
        val teacher = (0L until 31L).map { StepsFusionShadow.TeacherMinute(it, 20) }
        val fit = StepsFusionShadow.fit(motion, teacher)!!
        assertEquals(20.0, fit.stepsPerMotionUnit, 1e-9)
        assertTrue(fit.confidence != StepsFusionShadow.Confidence.INSUFFICIENT)
    }

    @Test fun minuteEstimateIsPhysiologicallyBounded() {
        assertEquals(240, StepsFusionShadow.estimateMinute(1000.0, 1000.0))
        assertEquals(0, StepsFusionShadow.estimateMinute(0.0, 10.0))
    }
}
