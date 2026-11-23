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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.otidotid.alke.analytics.SleepBreathingRegularityEngine as Breathing
import java.util.Locale

/**
 * Simple WHOOP 4 sleep breathing display:
 * NUMBER (≈ when inferred) / DATA TRUST / descriptive pattern indicator.
 *
 * AASSM hypopnea/AHI require validated airflow and desaturation/arousal evidence;
 * we do not invent risk categories for hypopnea from wrist RR or ADC.
 */
@Composable
internal fun SleepBreathingQualityCard(
    selectedDay: String?,
    report: Breathing.Report?,
    recent: List<Pair<String, Breathing.Report?>>,
    openDetailsOnEntry: Boolean = false,
) {
    var expanded by remember { mutableStateOf(openDetailsOnEntry) }
    LaunchedEffect(openDetailsOnEntry) {
        if (openDetailsOnEntry) expanded = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader("SLEEP BREATHING QUALITY")
        AlkeCard(tint = Palette.metricPurple) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Actual WHOOP 4 CVHR/hypopnea classifier remains in research and
                // clinical validation. No evidence-based low/medium/high risk is available
                // yet; never convert ordinary RR/HRV variability into "Low risk".
                Text("HYPOPNEA RISK: NOT ASSESSED",
                    style = AlkeType.headline, color = Palette.textPrimary)
                Text(
                    "ALKE is not a medical device. For medical concerns, " +
                        "consult a qualified healthcare professional.",
                    style = AlkeType.footnote, color = Palette.textTertiary,
                )

                Text(
                    if (expanded) "Hide Details" else "Details · Previous 7 Nights",
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
                    style = AlkeType.footnote, color = Palette.metricPurple,
                )
                if (expanded) {
                    // Reuse the existing breathing evidence only INSIDE details; it is not
                    // a hypopnea risk label, and the Today shortcut never displays bpm.
                    val valueLabel = when {
                        selectedDay == null -> "No recorded sleep"
                        report == null -> "Analyzing breathing..."
                        report.hasEvidence -> formatRate(report)
                        else -> "No respiratory reading"
                    }
                    Text(valueLabel, style = AlkeType.headline, color = Palette.textPrimary)
                    if (report != null && report.hasEvidence) {
                        Text("Data Trust: ${trustText(report.trust)}",
                            style = AlkeType.footnote, color = Palette.textSecondary)
                        Text("Breathing Pattern: ${patternText(report.pattern)}",
                            style = AlkeType.footnote, color = Palette.textPrimary)
                    }
                    if (report != null && report.windows.size >= 2) {
                        Text(
                            "Respiratory-rate observations · five-minute windows",
                            style = AlkeType.footnote, color = Palette.textSecondary,
                        )
                        BreathingRateTrace(report.windows)
                    }
                    if (report != null) {
                        Text(
                            "Source: ${sourceText(report.source)}",
                            style = AlkeType.footnote, color = Palette.textSecondary,
                        )
                        if (report.variabilityPercent != null) {
                            Text(
                                "Observed Variation: ${String.format(Locale.US, "%.1f", report.variabilityPercent)}% · " +
                                    "Window Coverage: ${report.coveragePercent}%",
                                style = AlkeType.footnote, color = Palette.textSecondary,
                            )
                        }
                        Text(
                            "Direct ${report.directCoveragePercent}% · RR estimates " +
                                "${report.rrCoveragePercent}% · HR " +
                                "${report.hrCoveragePercent?.let { "$it%" } ?: "n/a"} · motion " +
                                "${report.movementCoveragePercent?.let { "$it%" } ?: "n/a"}",
                            style = AlkeType.footnote, color = Palette.textTertiary,
                        )
                    }
                    Text("Recent 7 recorded nights", style = AlkeType.footnote, color = Palette.textPrimary)
                    if (recent.isEmpty()) {
                        Text("No recorded sleep history.", style = AlkeType.footnote, color = Palette.textSecondary)
                    } else {
                        recent.forEach { (day, historical) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(day, style = AlkeType.footnote, color = Palette.textSecondary)
                                Text(
                                    when {
                                        historical == null -> "Analyzing..."
                                        historical.hasEvidence -> formatRate(historical) + " · " + trustText(historical.trust)
                                        else -> "No reliable respiratory rate"
                                    },
                                    style = AlkeType.footnote, color = Palette.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRate(report: Breathing.Report): String =
    (if (report.approximate) "≈ " else "") +
        String.format(Locale.US, "%.1f", report.medianBpm) + " breaths/min"

private fun trustText(trust: Breathing.Trust): String = when (trust) {
    Breathing.Trust.HIGH -> "High"
    Breathing.Trust.MEDIUM -> "Medium"
    Breathing.Trust.LOW -> "Low"
    Breathing.Trust.NONE -> "Unavailable"
}

private fun sourceText(source: Breathing.Source): String = when (source) {
    Breathing.Source.DIRECT_RESPIRATION -> "Direct respiration"
    Breathing.Source.MIXED_DIRECT_AND_RR -> "Respiration + RR estimate"
    Breathing.Source.RR_ESTIMATE -> "RR/HRV estimate"
    Breathing.Source.DAILY_AGGREGATE -> "Daily respiratory aggregate"
    Breathing.Source.NONE -> "No respiration source"
}

private fun patternText(pattern: Breathing.Pattern): String = when (pattern) {
    Breathing.Pattern.LOW_VARIATION -> "Low Variation"
    Breathing.Pattern.MODERATE_VARIATION -> "Medium Variation"
    Breathing.Pattern.HIGH_VARIATION -> "High Variation"
    Breathing.Pattern.NOT_ASSESSABLE -> "Not Assessed"
}

@Composable
private fun BreathingRateTrace(windows: List<Breathing.Window>) {
    val color = Palette.metricPurple
    Canvas(
        modifier = Modifier.fillMaxWidth().height(92.dp).semantics {
            contentDescription = "Respiratory rate by five-minute window; gaps indicate missing readings."
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
            drawCircle(
                if (window.derivedFromRr) color.copy(alpha = 0.55f) else color,
                radius = 2.2.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}
