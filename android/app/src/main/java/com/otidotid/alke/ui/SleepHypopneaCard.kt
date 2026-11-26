package com.otidotid.alke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otidotid.alke.R
import com.otidotid.alke.analytics.SleepHypopneaRiskEngine

@Composable
private fun riskLabel(risk: SleepHypopneaRiskEngine.Risk): String = when (risk) {
    SleepHypopneaRiskEngine.Risk.LOW -> stringResource(R.string.sleep_hypopnea_low)
    SleepHypopneaRiskEngine.Risk.MEDIUM -> stringResource(R.string.sleep_hypopnea_medium)
    SleepHypopneaRiskEngine.Risk.HIGH -> stringResource(R.string.sleep_hypopnea_high)
    SleepHypopneaRiskEngine.Risk.WAITING -> stringResource(R.string.sleep_hypopnea_waiting)
    SleepHypopneaRiskEngine.Risk.UNAVAILABLE -> stringResource(R.string.sleep_hypopnea_unavailable)
}

@Composable
private fun riskColor(risk: SleepHypopneaRiskEngine.Risk): Color = when (risk) {
    SleepHypopneaRiskEngine.Risk.LOW -> Palette.restColor
    SleepHypopneaRiskEngine.Risk.MEDIUM -> Color(0xFFE9B968)
    SleepHypopneaRiskEngine.Risk.HIGH -> Color(0xFFF08283)
    else -> Palette.textTertiary
}

/** Compact Today shortcut or fuller Sleep module card, both backed by the same assessment. */
@Composable
internal fun SleepHypopneaCard(
    assessment: SleepHypopneaRiskEngine.Assessment,
    onOpen: () -> Unit,
    compact: Boolean = false,
) {
    val risk = assessment.risk
    val accent = riskColor(risk)
    AlkeCard(modifier = Modifier.clickable(onClick = onOpen), tint = Palette.restColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(if (compact) 36.dp else 44.dp)
                    .background(Palette.restColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = Palette.restColor,
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!compact) {
                    Text(
                        stringResource(R.string.sleep_hypopnea_title),
                        style = AlkeType.overline,
                        color = Palette.textSecondary,
                    )
                }
                if (assessment.isReady) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.sleep_hypopnea_risk_title),
                            style = if (compact) AlkeType.subhead else AlkeType.headline,
                            color = Palette.textPrimary,
                        )
                        Text(
                            riskLabel(risk),
                            style = if (compact) AlkeType.headline else AlkeType.title2,
                            color = accent,
                        )
                    }
                } else if (compact) {
                    Text(
                        stringResource(R.string.sleep_hypopnea_title),
                        style = AlkeType.subhead,
                        color = Palette.textSecondary,
                    )
                }
                if (!compact && !assessment.isReady) {
                    // Keep the purpose identifiable without presenting an unmeasured verdict.
                    Text(
                        stringResource(R.string.sleep_hypopnea_risk_title),
                        style = AlkeType.headline,
                        color = Palette.textPrimary,
                    )
                    Text(
                        if (risk == SleepHypopneaRiskEngine.Risk.WAITING)
                            stringResource(R.string.sleep_hypopnea_waiting_hint)
                        else stringResource(R.string.sleep_hypopnea_unavailable_hint),
                        style = AlkeType.footnote,
                        color = Palette.textSecondary,
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Palette.textTertiary,
            )
        }
    }
}

@Composable
internal fun SleepHypopneaDetailContent(assessment: SleepHypopneaRiskEngine.Assessment) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.sleep_hypopnea_title),
            style = AlkeType.overline,
            color = Palette.restColor,
        )
        // Never show "HYPOPNEA RISK PENDING" as though it were a measurement.
        // The verdict is displayed only when a completed night has real coverage.
        if (assessment.isReady) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sleep_hypopnea_risk_title), style = AlkeType.headline, color = Palette.textPrimary)
                Text(riskLabel(assessment.risk), style = AlkeType.title1, color = riskColor(assessment.risk))
            }
        }
        if (!assessment.isReady) {
            Text(
                if (assessment.risk == SleepHypopneaRiskEngine.Risk.WAITING)
                    stringResource(R.string.sleep_hypopnea_waiting_hint)
                else stringResource(R.string.sleep_hypopnea_unavailable_hint),
                style = AlkeType.body,
                color = Palette.textSecondary,
            )
        }
        Text(
            stringResource(R.string.sleep_hypopnea_method),
            style = AlkeType.body,
            color = Palette.textSecondary,
        )
        Text(
            stringResource(R.string.sleep_hypopnea_disclaimer),
            style = AlkeType.subhead,
            color = Palette.textTertiary,
        )
        if (assessment.risk == SleepHypopneaRiskEngine.Risk.LOW) {
            Text(
                stringResource(R.string.sleep_hypopnea_low_caution),
                style = AlkeType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}
