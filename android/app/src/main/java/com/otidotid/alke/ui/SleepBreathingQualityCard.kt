package com.otidotid.alke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.otidotid.alke.analytics.SleepBreathingRegularityEngine
import java.util.Locale

/**
 * Experimental, non-diagnostic Sleep section. Never shows hypopnea counts, apnea risk,
 * desaturation events or a numerical health "quality" score.
 */
@Composable
internal fun SleepBreathingQualityCard(
    selectedDay: String?,
    report: SleepBreathingRegularityEngine.Report?,
    recent: List<Pair<String, SleepBreathingRegularityEngine.Report?>>,
) {
    var expanded by remember { mutableStateOf(false) }
    val status = when (report?.state) {
        SleepBreathingRegularityEngine.State.RELATIVELY_STEADY -> "Relatively steady"
        SleepBreathingRegularityEngine.State.MORE_VARIABLE -> "More variable"
        SleepBreathingRegularityEngine.State.INSUFFICIENT_DATA -> "Insufficient data"
        null -> if (selectedDay == null) "No recorded sleep" else "Checking signal..."
    }
    val explanation = when (report?.reason) {
        SleepBreathingRegularityEngine.Reason.SLEEP_TOO_SHORT ->
            "At least 4 hours of recorded sleep are required."
        SleepBreathingRegularityEngine.Reason.NO_DIRECT_RESPIRATION ->
            "No directly recorded respiration samples for this night. Some WHOOP models do not provide this stream."
        SleepBreathingRegularityEngine.Reason.LOW_COVERAGE ->
            "Too few usable 5-minute breathing windows; missing data is not interpreted as regular breathing."
        SleepBreathingRegularityEngine.Reason.UNUSABLE_SIGNAL ->
            "The recorded respiration signal could not pass signal-quality checks."
        else -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader("Sleep breathing regularity", overline = "Experimental", trailing = "Wellness only")
        AlkeCard(tint = Palette.metricPurple) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status, style = AlkeType.headline, color = Palette.textPrimary)
                if (report?.hasEvidence == true) {
                    Text(
                        "Median ${String.format(Locale.US, "%.1f", report.medianBpm)} breaths/min  ·  " +
                            "Recorded coverage ${report.coveragePercent}%",
                        style = AlkeType.footnote, color = Palette.textSecondary,
                    )
                    Text(
                        "Within-night variability ${String.format(Locale.US, "%.1f", report.variabilityPercent)}% " +
                            "(experimental, not a medical threshold)",
                        style = AlkeType.footnote, color = Palette.textSecondary,
                    )
                } else {
                    Text(explanation.ifEmpty { "Direct breathing measurements are being checked." },
                        style = AlkeType.footnote, color = Palette.textSecondary)
                }
                Text(
                    "Not a sleep apnea or hypopnea detector. No airflow measurement or clinically validated event count.",
                    style = AlkeType.footnote, color = Palette.textTertiary,
                )
                Text(
                    if (expanded) "Hide nightly detail" else "View nightly detail and 7-night trend",
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
                    style = AlkeType.footnote, color = Palette.metricPurple,
                )
                if (expanded) {
                    if (report?.hasEvidence == true && report.windows.size >= 2) {
                        Text("Direct respiratory-rate observations · 5-minute windows",
                            style = AlkeType.footnote, color = Palette.textSecondary)
                        BreathingRateTrace(report.windows)
                    }
                    Text("Recent 7 recorded nights", style = AlkeType.footnote, color = Palette.textPrimary)
                    if (recent.isEmpty()) {
                        Text("No recent sleep history.", style = AlkeType.footnote, color = Palette.textSecondary)
                    } else {
                        recent.forEach { (day, historical) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day, style = AlkeType.footnote, color = Palette.textSecondary)
                                Text(
                                    if (historical == null) "Checking..."
                                    else if (historical.hasEvidence)
                                        String.format(Locale.US, "%.1f%% relative variability", historical.variabilityPercent)
                                    else "Insufficient data",
                                    style = AlkeType.footnote, color = Palette.textPrimary,
                                )
                            }
                        }
                    }
                    Text(
                        "The comparison describes observed variation in raw respiration signal only. " +
                            "It is not SpO2, AHI, ODI, snoring, apnea or hypopnea screening. " +
                            "If you have persistent symptoms such as loud snoring or daytime sleepiness, consult a clinician.",
                        style = AlkeType.footnote, color = Palette.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreathingRateTrace(windows: List<SleepBreathingRegularityEngine.Window>) {
    val color = Palette.metricPurple
    Canvas(
        modifier = Modifier.fillMaxWidth().height(92.dp).semantics {
            contentDescription = "Experimental direct respiratory rate by 5-minute window; gaps mean missing readings."
        },
    ) {
        val last = (windows.lastOrNull()?.offsetMinutes ?: 5).coerceAtLeast(5)
        val left = 7.dp.toPx()
        val top = 7.dp.toPx()
        val usableW = size.width - 2f * left
        val usableH = size.height - 2f * top
        if (usableW <= 0f || usableH <= 0f) return@Canvas
        windows.forEach { window ->
            val x = left + usableW * window.offsetMinutes / last
            val y = top + usableH * (25.0 - window.rateBpm).toFloat() / 17f
            drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(x, y))
        }
    }
}
