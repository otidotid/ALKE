package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner-approved visual layout, no data or score algorithm change. */
class AlkeDailyOutlookLayoutContractTest {
    private fun source(): String {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(6) {
            val file = File(current, "app/src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
            if (file.isFile) return file.readText()
            current = current.parentFile ?: return@repeat
        }
        error("ALKE Experience source missing")
    }

    @Test fun effortAndRestValuesAreHorizontallyAlignedWithTheirLabels() {
        val screen = source()
        val tile = screen.substringAfter("private fun AlkeOutlookScoreTile(")
            .substringBefore("@Composable\nprivate fun AlkeMetricTile(")
        assertTrue(tile.contains("Row(\n        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)"))
        assertTrue(tile.contains("verticalAlignment = Alignment.CenterVertically"))
        assertTrue(tile.contains("Column(\n            modifier = Modifier.weight(1f)"))
        assertTrue(tile.contains("Text(title.uppercase(Locale.ROOT)"))
        assertTrue(tile.contains("Text(context, color = A.muted"))
        assertTrue(tile.contains("Text(value, color = A.white, fontSize = 27.sp"))
        assertTrue(tile.contains("textAlign = TextAlign.End"))
        assertTrue(tile.contains(".clickable(onClick = onClick)"))
        assertFalse(tile.contains("fontSize = 24.sp"))
    }

    @Test fun dailyOutlookRetainsOriginalScoresScaleAndDetailRoutes() {
        val screen = source()
        val today = screen.substringAfter("fun AlkeTodayExperience(")
            .substringBefore("AlkeSectionTitle(\"Key signals\")")
        assertTrue(today.contains("title = \"Effort\", context = \"Activity\""))
        assertTrue(today.contains("title = \"Rest\", context = \"Sleep\""))
        assertTrue(today.contains("UnitFormatter.effortDisplay(it, UnitPrefs.effortScale(context))"))
        assertTrue(today.contains("onMetric(\"strain\")"))
        assertTrue(today.contains("onClick = onSleep"))
    }
}
