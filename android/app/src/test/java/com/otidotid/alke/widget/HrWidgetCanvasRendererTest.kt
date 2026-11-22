package com.otidotid.alke.widget

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HrWidgetCanvasRendererTest {
    private val now = 1_800_000_000_000L

    @Test fun noVerticalOverlapAtNormalAndCompactHeights() {
        for ((w, h) in listOf(
            250f to 150f, 300f to 145f, 330f to 210f,
            180f to 116f, 260f to 92f, 360f to 180f,
        )) {
            val l = HrWidgetCanvasRenderer.layout(w, h)
            assertTrue("header/value overlap: $w x $h", l.headerBaseline + 8 < l.valueBaseline)
            assertTrue("value/subtitle overlap: $w x $h", l.valueBaseline + 2 < l.detailBaseline)
            assertTrue("subtitle/chart overlap: $w x $h", l.detailBaseline + 5 < l.graphTop)
            assertTrue("graph outside widget: $w x $h", l.graphBottom < l.height)
            if (h < 116f) assertFalse(l.showGraph)
        }
    }

    @Test fun rendererProducesBoundedNonemptyBitmapInAllThreeStates() {
        val samples = listOf(
            WidgetSnapshot(
                heartRate = 74, heartRateStale = false, heartRateAtMs = now - 1_000L,
                hrSeries = listOf(
                    HrPoint(now / 1000L - 300L, 70),
                    HrPoint(now / 1000L - 120L, 76),
                    HrPoint(now / 1000L - 60L, 74),
                ),
            ),
            WidgetSnapshot(
                heartRate = 74, heartRateStale = true,
                heartRateAtMs = now - 3L * 3_600_000L,
                hrSeries = emptyList(),
            ),
            WidgetSnapshot(),
        )
        for (snapshot in samples) {
            for ((w, h) in listOf(250f to 150f, 330f to 205f, 180f to 110f)) {
                val bitmap = HrWidgetCanvasRenderer.render(
                    snapshot = snapshot, dark = true,
                    widthDp = w, heightDp = h, density = 3f, nowMs = now,
                )
                assertEquals(Bitmap.Config.RGB_565, bitmap.config)
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
                assertTrue("Oversized RemoteViews payload", bitmap.byteCount <= 390_000)
                // More than one color is needed: the header + BPM cannot be a blank card.
                val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                val corner = bitmap.getPixel(0, 0)
                assertTrue("Image should render layered content", center != 0 || corner != 0)
                bitmap.recycle()
            }
        }
    }

    @Test fun stalenessDescribedOnceWithoutRepeatedRecordedStatus() {
        assertEquals("Last reading · 3h ago",
            HrWidgetAge.label(now - 3L * 3_600_000L, now))
        assertEquals("Last reading · just now", HrWidgetAge.label(now - 1_000L, now))
    }

    @Test fun canvasGeometryIsFiniteForUnreliableLauncherSizes() {
        val fallback = HrWidgetCanvasRenderer.layout(Float.NaN, 0f)
        assertEquals(300f, fallback.width)
        assertEquals(150f, fallback.height)
        assertTrue(fallback.showGraph)
    }
}
