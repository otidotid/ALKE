package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ALKE visual experience: actual Compose screens based on the approved six-phone design.
 * This is NOT a static mockup. Every readout comes from the existing app's own recorded
 * DailyMetric or computed-metric repository. Nulls remain visibly unavailable.
 * Original detailed screens are reachable through the "Explore details" routes.
 */
private object A {
    val bg = Color(0xFF061523)
    val top = Color(0xFF092536)
    val card = Color(0xFF0D2939)
    val cardDark = Color(0xFF0A2031)
    val stroke = Color(0xFF285064)
    val cyan = Color(0xFF26DEEB)
    val blue = Color(0xFF258EC7)
    val gold = Color(0xFFF0BF60)
    val white = Color(0xFFF5FCFF)
    val muted = Color(0xFF9BB9C5)
    val green = Color(0xFF38DE9C)
}

@Composable
private fun AlkePage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(A.top, A.bg, A.bg)),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun AlkePanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(A.card, A.cardDark)))
            .border(1.dp, A.stroke.copy(alpha = 0.62f), shape)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun AlkeHeader(label: String, onNotification: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(43.dp)
            Spacer(Modifier.width(5.dp))
            Column {
                Text("ALKE", color = A.white, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("THE INTELLIGENCE WITHIN", color = A.muted, fontSize = 7.sp, letterSpacing = 1.5.sp)
            }
        }
        if (onNotification != null) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(A.card).border(1.dp, A.stroke, CircleShape)
                    .clickable(onClick = onNotification),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifications", tint = A.white)
            }
        } else {
            Text(label.uppercase(), color = A.gold, fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun AlkeHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = A.white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = A.muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun AlkeSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = A.white, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (action != null && onAction != null) {
            Text(action, modifier = Modifier.clickable(onClick = onAction), color = A.cyan, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlkePill(text: String, tint: Color = A.cyan) {
    Box(Modifier.clip(CircleShape).background(tint.copy(alpha = 0.15f)).padding(horizontal = 13.dp, vertical = 6.dp)) {
        Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
private fun AlkeGauge(
    label: String,
    value: Double?,
    fraction: Float,
    diameter: Dp = 222.dp,
    suffix: String = "",
    colors: List<Color> = listOf(A.cyan, A.blue, A.gold),
) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 15.dp.toPx()
            val inset = width / 2 + 6.dp.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val top = Offset(inset, inset)
            drawArc(
                color = A.stroke.copy(alpha = 0.60f), startAngle = 135f,
                sweepAngle = 270f, useCenter = false, topLeft = top, size = arc,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            if (value != null && fraction > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(colors + colors.first()),
                    startAngle = 135f,
                    sweepAngle = 270f * fraction.coerceIn(0f, 1f),
                    useCenter = false, topLeft = top, size = arc,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = A.muted, fontSize = 13.sp)
            Text(
                value?.let { if (suffix == "%") "${it.roundToInt()}%" else if (suffix == "y") String.format(Locale.US, "%.1f", it) else String.format(Locale.US, "%.1f", it) } ?: "—",
                color = A.white, fontSize = if (diameter > 170.dp) 48.sp else 34.sp,
                fontWeight = FontWeight.Bold, lineHeight = 52.sp,
            )
            Text(if (value == null) "Awaiting data" else "Recorded", color = if (value == null) A.muted else A.green, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AlkeMetricTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = A.cyan,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier = modifier.clip(shape).background(A.card)
            .border(1.dp, A.stroke.copy(alpha = 0.52f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        Text(title, color = A.muted, fontSize = 11.sp, maxLines = 1)
        Text(value, color = A.white, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlkeLinkRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    accent: Color = A.cyan,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(A.card).border(1.dp, A.stroke.copy(alpha = 0.55f), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(43.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = accent) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = A.white, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = A.muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.muted, modifier = Modifier.size(21.dp))
    }
}

private fun niceMinutes(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0) return "—"
    val mins = value.roundToInt()
    return "${mins / 60}h ${mins % 60}m"
}
private fun niceOne(value: Double?, unit: String = ""): String =
    if (value == null || !value.isFinite()) "—"
    else String.format(Locale.US, "%.1f%s", value, unit)

private fun readinessText(score: Double?): String = when {
    score == null -> "Connect a wearable or import records to build your recovery baseline."
    score >= 75 -> "Your recorded recovery is strong. Consider the training plan that fits your goals."
    score >= 40 -> "Your recovery is moderate. Balance activity with quality rest."
    else -> "Your recorded recovery is lower today. Prioritize rest and check your trends."
}

@Composable
fun AlkeTodayExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onSleep: () -> Unit,
    onHealth: () -> Unit,
    onTrain: () -> Unit,
    onProgress: () -> Unit,
    onInsight: () -> Unit,
    onDetailedToday: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val recovery = day?.recovery?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    AlkePage {
        AlkeHeader("Today", onNotification = onNotifications)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Today", color = A.white, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(dateText, color = A.muted, fontSize = 13.sp)
            Text("Your performance and recovery, in balance.", color = A.muted, fontSize = 12.sp)
        }
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DAILY READINESS", color = A.cyan, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                AlkePill(if (recovery == null) "Building baseline" else "Recovery-based", A.gold)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlkeGauge(label = "Readiness", value = recovery, fraction = ((recovery ?: 0.0) / 100.0).toFloat(), suffix = "%")
            }
            Text(
                if (recovery == null) "No readiness score yet" else when {
                    recovery >= 75 -> "High recovery"
                    recovery >= 40 -> "Moderate recovery"
                    else -> "Low recovery"
                },
                color = if (recovery == null) A.muted else A.green,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 13.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlkeMetricTile("HRV", niceOne(day?.avgHrv, " ms"), Icons.Filled.FavoriteBorder, Modifier.weight(1f), A.cyan, onHealth)
            AlkeMetricTile("RHR", day?.restingHr?.let { "$it bpm" } ?: "—", Icons.Filled.Favorite, Modifier.weight(1f), A.gold, onHealth)
            AlkeMetricTile("Sleep", niceMinutes(day?.totalSleepMin), Icons.Filled.Bedtime, Modifier.weight(1f), A.blue, onSleep)
            AlkeMetricTile("Recovery", recovery?.let { "${it.roundToInt()}%" } ?: "—", Icons.Filled.MonitorHeart, Modifier.weight(1f), A.green, onHealth)
        }
        AlkePanel(Modifier.fillMaxWidth(), onClick = onInsight) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(A.gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = A.gold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("TODAY'S FOCUS", color = A.gold, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                    Text(readinessText(recovery), color = A.white, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
        }
        AlkeSectionTitle("Explore your day")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlkePanel(Modifier.weight(1f), onClick = onTrain) {
                Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = A.gold)
                Text("Train", color = A.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Effort & activities", color = A.muted, fontSize = 11.sp)
            }
            AlkePanel(Modifier.weight(1f), onClick = onProgress) {
                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = A.cyan)
                Text("Progress", color = A.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Long-term trends", color = A.muted, fontSize = 11.sp)
            }
        }
        if (day == null) {
            AlkeLinkRow(
                "Connect your wearable", "Get started with recorded data, never sample scores",
                Icons.Filled.Sensors, onDevices,
            )
        }
        AlkeLinkRow(
            "Detailed metrics", "All original analyses, charts and data sources",
            Icons.Filled.Tune, onDetailedToday, A.muted,
        )
    }
}

@Composable
fun AlkeTrainingExperience(
    vm: AppViewModel,
    onWorkouts: () -> Unit,
    onLive: () -> Unit,
    onRecovery: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val strain = day?.strain?.takeIf { it.isFinite() && it >= 0 }
    AlkePage {
        AlkeHeader("Train")
        AlkeHeading("Training", "Know the effort behind every session.")
        AlkePanel(Modifier.fillMaxWidth()) {
            Text("TODAY'S EFFORT", color = A.gold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlkeGauge("Training strain", strain, ((strain ?: 0.0) / 100.0).toFloat(), 210.dp, colors = listOf(A.gold, Color(0xFFF19B34), A.gold))
            }
            Text(
                if (strain == null) "Waiting for enough activity data" else "On-device effort estimate (0–100 scale)",
                color = A.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        AlkeSectionTitle("Recent effort", "All workouts", onWorkouts)
        AlkeMiniBars(days.takeLast(7).map { it.day.takeLast(5) to it.strain }, A.gold)
        AlkeLinkRow("Workouts", "Sessions, activity contributions and training history", Icons.Filled.DirectionsRun, onWorkouts, A.gold)
        AlkeLinkRow("Live training", "Heart rate, zones and recording tools", Icons.Filled.Favorite, onLive, A.cyan)
        AlkeLinkRow("Recovery", "Rest and recovery before your next workout", Icons.Filled.Bedtime, onRecovery, A.blue)
    }
}

@Composable
private fun AlkeMiniBars(points: List<Pair<String, Double?>>, accent: Color) {
    AlkePanel(Modifier.fillMaxWidth()) {
        if (points.none { it.second?.isFinite() == true }) {
            Text("Your trend will appear once recorded days are available.", color = A.muted, fontSize = 13.sp)
        } else {
            Row(
                Modifier.fillMaxWidth().height(122.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                points.forEach { (label, number) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val portion = (number?.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0, 100.0) / 100.0
                        Box(
                            Modifier.fillMaxWidth().height((85 * portion).coerceAtLeast(4.0).dp)
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                .background(if (number == null) A.stroke else accent),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(label, color = A.muted, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun AlkeRecoveryExperience(
    vm: AppViewModel,
    onFullSleep: () -> Unit,
    onHealth: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val total = day?.totalSleepMin
    val stages = listOf(
        Triple("Deep", day?.deepMin, Color(0xFF3376DC)),
        Triple("REM", day?.remMin, Color(0xFF7A6BDF)),
        Triple("Light", day?.lightMin, Color(0xFF48B0E4)),
    )
    AlkePage {
        AlkeHeader("Recovery")
        AlkeHeading("Recovery", "Sleep and physiology, viewed together.")
        AlkePanel(Modifier.fillMaxWidth()) {
            Text("LAST RECORDED NIGHT", color = A.cyan, letterSpacing = 2.sp, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlkeGauge("Sleep duration", total?.div(60), ((total ?: 0.0) / 600.0).toFloat(), 205.dp, colors = listOf(A.blue, Color(0xFF7461CF), A.cyan))
            }
            Text("Duration: ${niceMinutes(total)}", color = A.white, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 16.sp)
        }
        AlkeSectionTitle("Sleep stages")
        AlkePanel(Modifier.fillMaxWidth()) {
            val knownTotal = stages.sumOf { it.second?.takeIf(Double::isFinite) ?: 0.0 }
            if (knownTotal <= 0) {
                Text("Sleep stages will appear when a recorded night is available.", color = A.muted, fontSize = 12.sp)
            } else {
                Row(Modifier.fillMaxWidth().height(15.dp).clip(RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    stages.forEach { (_, minutes, color) ->
                        if (minutes != null && minutes > 0) {
                            Box(Modifier.weight(minutes.toFloat().coerceAtLeast(0.1f)).fillMaxSize().background(color))
                        }
                    }
                }
            }
            stages.forEach { (label, minutes, color) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(9.dp))
                    Text(label, color = A.white, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(niceMinutes(minutes), color = A.muted, fontSize = 13.sp)
                }
            }
        }
        AlkeLinkRow("Sleep analysis", "Sleep detection, stages, journal and smart alarm", Icons.Filled.Bedtime, onFullSleep, A.blue)
        AlkeLinkRow("Recovery drivers", "HRV, resting heart rate and vital signs", Icons.Filled.MonitorHeart, onHealth, A.cyan)
    }
}

@Composable
fun AlkeProgressExperience(
    vm: AppViewModel,
    onHealth: () -> Unit,
    onVo2: () -> Unit,
    onBodyAge: () -> Unit,
    onTrends: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val vo2 by produceState<Double?>(initialValue = null, key1 = vm.activeStrapId) {
        value = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull()
    }
    AlkePage {
        AlkeHeader("Progress")
        AlkeHeading("Progress", "The long view of your performance.")
        AlkePanel(Modifier.fillMaxWidth(), onClick = onVo2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VO₂ MAX", color = A.cyan, letterSpacing = 2.sp, fontSize = 10.sp)
                    Text(niceOne(vo2), color = A.white, fontWeight = FontWeight.Bold, fontSize = 43.sp)
                    Text("ml/kg/min • estimated", color = A.muted, fontSize = 11.sp)
                }
                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = A.cyan, modifier = Modifier.size(39.dp))
            }
            Text("Open VO₂ Max detail", color = A.cyan, fontSize = 12.sp)
        }
        AlkePanel(Modifier.fillMaxWidth(), onClick = onBodyAge) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ALKE BODY AGE", color = A.gold, letterSpacing = 2.sp, fontSize = 10.sp)
                    Text("Your biological trends", color = A.white, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Methodology, factors and confidence", color = A.muted, fontSize = 11.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.gold)
            }
        }
        AlkeSectionTitle("Recovery trend", "Full trends", onTrends)
        AlkeMiniBars(days.takeLast(7).map { it.day.takeLast(5) to it.recovery }, A.cyan)
        AlkeLinkRow("Vital signs", "HRV, RHR and measured physiology", Icons.Filled.MonitorHeart, onHealth)
        AlkeLinkRow("All trends", "Full history, charts and comparisons", Icons.Filled.Insights, onTrends)
        if (day == null) Text("Your progress screen will populate as measurements arrive.", color = A.muted, fontSize = 12.sp)
    }
}

@Composable
fun AlkeInsightExperience(
    vm: AppViewModel,
    onCoach: () -> Unit,
    onIntelligence: () -> Unit,
    onHealth: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val recovery = day?.recovery
    AlkePage {
        AlkeHeader("Insight")
        AlkeHeading("ALKE Intelligence", "Your data, made meaningful.")
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(57.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("THE INTELLIGENCE WITHIN", color = A.gold, letterSpacing = 1.5.sp, fontSize = 9.sp)
                    Text("Your daily perspective", color = A.white, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(readinessText(recovery), color = A.white, fontSize = 15.sp, lineHeight = 23.sp)
            Text(
                if (day == null) "No metrics have been recorded yet; no conclusions are generated."
                else "Based on the recovery metrics recorded for your selected day.",
                color = A.muted, fontSize = 11.sp,
            )
        }
        AlkeSectionTitle("Your signals")
        listOf(
            Triple("Recovery", recovery?.let { "${it.roundToInt()}%" } ?: "—", A.gold),
            Triple("HRV", niceOne(day?.avgHrv, " ms"), A.cyan),
            Triple("Resting HR", day?.restingHr?.let { "$it bpm" } ?: "—", A.blue),
        ).forEach { (label, value, tint) ->
            AlkePanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = A.muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(value, color = A.white, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
        AlkeLinkRow("Intelligence details", "How Charge, Effort and Rest are calculated", Icons.Filled.Insights, onIntelligence)
        AlkeLinkRow("Ask ALKE Coach", "Optional AI assistant with your configured provider", Icons.Filled.AutoAwesome, onCoach, A.gold)
        AlkeLinkRow("Explore evidence", "Read the underlying health metrics", Icons.Filled.MonitorHeart, onHealth)
    }
}

@Composable
fun AlkeMeExperience(
    onProfile: () -> Unit,
    onDevices: () -> Unit,
    onSettings: () -> Unit,
    onModules: () -> Unit,
    onSources: () -> Unit,
) {
    AlkePage {
        AlkeHeader("Me")
        AlkeHeading("My ALKE", "Make it yours. Keep control of your data.")
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                BrandMark(54.dp)
                Column {
                    Text("ALKE", color = A.white, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 4.sp)
                    Text("Strength × Rest", color = A.gold, fontSize = 12.sp)
                }
            }
            Text("The Intelligence Within", color = A.muted, fontSize = 12.sp)
        }
        AlkeSectionTitle("Your experience")
        AlkeLinkRow("My profile", "Personal settings and metrics", Icons.Filled.FavoriteBorder, onProfile)
        AlkeLinkRow("Devices", "Connect and manage wearable sensors", Icons.Filled.Sensors, onDevices)
        AlkeLinkRow("Data sources", "Imports, Health Connect and provenance", Icons.Filled.Storage, onSources)
        AlkeLinkRow("Settings", "Privacy, preferences and appearance", Icons.Filled.Settings, onSettings)
        AlkeLinkRow("All modules", "Explore the complete ALKE toolkit", Icons.Filled.Tune, onModules, A.gold)
    }
}
