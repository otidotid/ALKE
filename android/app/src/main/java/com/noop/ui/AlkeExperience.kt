package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.noop.data.HrSample
import com.noop.data.WorkoutRow
import com.noop.analytics.RestScorer
import com.noop.analytics.ActivityHeatmap
import com.noop.analytics.ReadinessEngine
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
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
internal fun AlkePanel(
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

@OptIn(ExperimentalMaterial3Api::class)
/**
 * One fixed ALKE identity/sync surface for the whole navigation graph.
 * Parent Scaffold owns its space, so it never scrolls with individual modules.
 */
@Composable
internal fun AlkeGlobalHeader(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(A.top)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        AlkeHeader(vm, onNotifications, onDevices, onProfile, unreadCount)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlkeHeader(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int = 0,
) {
    val live by vm.live.collectAsStateWithLifecycle()
    val activeIsOura by vm.activeIsOura.collectAsStateWithLifecycle()
    val ringBattery by vm.ouraBatteryPct.collectAsStateWithLifecycle()
    val activeName by vm.activeDeviceName.collectAsStateWithLifecycle()
    val syncing = live.backfilling || live.analyzingHistory
    val hasSyncError = !syncing && !live.lastSyncError.isNullOrBlank()
    val battery = if (live.connected) {
        if (activeIsOura) ringBattery?.takeIf { it in 0..100 }
        else live.batteryPct?.takeIf { it.isFinite() && it in 0.0..100.0 }?.roundToInt()
    } else null
    var showSyncHub by remember { mutableStateOf(false) }
    // Battery saver, system reduced motion and the in-app quiet-motion option all
    // keep the logo posed still. Only create a continuous frame loop during a real sync.
    val poseStill = rememberPoseStill()
    val spin = if (syncing && !poseStill) {
        val rotation by rememberInfiniteTransition(label = "alke-sync-halo").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
            label = "sync-rotation",
        )
        rotation
    } else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(15.dp)).clickable { showSyncHub = true }
                .padding(end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
                if (syncing) {
                    Box(
                        Modifier.size(48.dp).graphicsLayer { rotationZ = spin }
                            .border(2.dp, Brush.sweepGradient(listOf(A.cyan, A.gold, A.cyan)), CircleShape),
                    )
                }
                BrandMark(40.dp)
                if (hasSyncError || unreadCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape)
                            .background(if (hasSyncError) A.gold else A.cyan),
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            Column {
                Text("ALKE", color = A.white, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("THE INTELLIGENCE WITHIN", color = A.muted, fontSize = 7.sp, letterSpacing = 1.5.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = onDevices).padding(horizontal = 3.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (live.charging == true) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                    contentDescription = "Wearable battery and connection",
                    tint = if (battery != null && battery <= 20) A.gold else A.cyan,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    battery?.let { "$it%" } ?: "—",
                    color = if (battery == null) A.muted else A.white,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(A.card)
                    .border(1.dp, A.stroke, CircleShape).clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "My profile", tint = A.white)
            }
        }
    }
    if (showSyncHub) {
        ModalBottomSheet(
            onDismissRequest = { showSyncHub = false },
            containerColor = A.card,
            contentColor = A.white,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Sync & notifications", color = A.white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        live.analyzingHistory -> "Updating your health metrics"
                        live.backfilling -> "Syncing wearable history"
                        hasSyncError -> "Sync needs attention"
                        live.connected -> "Wearable connected"
                        else -> "Wearable disconnected"
                    },
                    color = if (hasSyncError) A.gold else A.cyan,
                    fontSize = 14.sp,
                )
                if (hasSyncError) Text(live.lastSyncError.orEmpty(), color = A.muted, fontSize = 12.sp)
                live.lastSyncAt?.let { timestamp ->
                    val time = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
                    Text("Last completed history sync: $time", color = A.muted, fontSize = 12.sp)
                }
                if (!activeName.isNullOrBlank()) {
                    Text(activeName.orEmpty(), color = A.muted, fontSize = 12.sp)
                }
                if (live.connected && live.historyReady && !syncing) {
                    TextButton(onClick = { vm.syncNow(); showSyncHub = false }) {
                        Text("Sync now", color = A.cyan)
                    }
                }
                AlkeLinkRow("Notifications", "Recent updates and alerts", Icons.Filled.NotificationsNone,
                    onClick = { showSyncHub = false; onNotifications() })
                AlkeLinkRow("Device & connection", "Pair, reconnect and manage your wearable", Icons.Filled.Sensors,
                    onClick = { showSyncHub = false; onDevices() })
            }
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

/**
 * The compact Today hero reads the actual live BLE stream while connected,
 * falling back to the latest STORED HR sample. It never invents a current BPM:
 * stale samples are labeled with their recorded time. The existing detail route
 * is deliberately unchanged, including workout/sleep overlays and gestures.
 */
@Composable
private fun AlkeHeartRatePreview(vm: AppViewModel, onOpen: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val activeId = vm.activeStrapId
    val stored by produceState<HrSample?>(initialValue = null, key1 = activeId) {
        while (true) {
            value = runCatching {
                listOfNotNull(
                    vm.repo.latestHr(activeId),
                    if (activeId != "my-whoop") vm.repo.latestHr("my-whoop") else null,
                ).maxByOrNull { it.ts }
            }.getOrNull()
            delay(15_000L)
        }
    }
    // "Live" is truthful only when the device is really connected.
    val currentBpm = if (live.connected) live.heartRate?.takeIf { it in 35..220 } else null
    val shownBpm = currentBpm ?: stored?.bpm?.takeIf { it in 35..220 }
    val label = when {
        currentBpm != null -> "LIVE · WEARABLE CONNECTED"
        stored != null -> "LAST RECORDED · " +
            Instant.ofEpochSecond(stored!!.ts).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
        else -> "AWAITING HEART-RATE DATA"
    }
    AlkePanel(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = A.cyan,
                modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text("HEART RATE", color = A.cyan, fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open heart-rate timeline",
                tint = A.cyan, modifier = Modifier.size(23.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(shownBpm?.toString() ?: "—", color = A.white, fontSize = 49.sp,
                fontWeight = FontWeight.Bold, lineHeight = 51.sp)
            Text("BPM", color = A.muted, fontSize = 16.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(label, color = if (currentBpm != null) A.green else A.muted,
            fontSize = 11.sp, letterSpacing = 0.3.sp, maxLines = 1)
    }
}

@Composable
fun AlkeTodayExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int = 0,
    onSleep: () -> Unit,
    onHealth: () -> Unit,
    onTrain: () -> Unit,
    onProgress: () -> Unit,
    onCoach: () -> Unit,
    onMetric: (String) -> Unit,
    onSteps: () -> Unit,
    onHeartRateTimeline: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val recentDays by vm.recentDays.collectAsStateWithLifecycle()
    // Use the SAME real/imported/estimated steps resolver as the full steps trend.
    // Never invent today's count from a stale or unavailable reading.
    val stepsModel by produceState<VitalDetailModel?>(
        initialValue = null, key1 = vm.activeStrapId, key2 = recentDays,
    ) {
        value = runCatching { buildSeriesVitalDetail(vm, "steps_est") }.getOrNull()
    }
    val lastSteps = stepsModel?.readings.orEmpty().maxByOrNull { it.day }
    val readinessLevel = remember(recentDays, day?.day) {
        ReadinessEngine.evaluate(recentDays, today = day?.day).level
    }
    val readinessLabel = when (readinessLevel) {
        ReadinessEngine.Level.PRIMED -> "Primed"
        ReadinessEngine.Level.BALANCED -> "Balanced"
        ReadinessEngine.Level.STRAINED -> "Strained"
        ReadinessEngine.Level.RUNDOWN -> "Run down"
        ReadinessEngine.Level.INSUFFICIENT -> "Building baseline"
    }
    val recovery = day?.recovery?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.loadWorkouts() }
    val todayWorkouts = workouts.filter {
        Instant.ofEpochSecond(it.startTs).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    }
    val workoutMinutes = todayWorkouts.takeIf { it.isNotEmpty() }
        ?.sumOf { ((it.durationS ?: (it.endTs - it.startTs).toDouble()).coerceAtLeast(0.0)) / 60.0 }
    val context = androidx.compose.ui.platform.LocalContext.current
    val brief = CoachBriefSettings.from(context).let { settings ->
        settings.storedBrief?.takeIf { settings.lastRunDayKey == LocalDate.now().toString() && it.isNotBlank() }
    }
    // Reuse the original Synthesis interpretation, not a second recovery-threshold algorithm.
    val offlineSynthesis = synthesisDetail(day)
    val rest = day?.let { RestScorer.restFromDaily(it) }
    val aiSynthesis by produceState<String?>(initialValue = null,
        key1 = listOf(day?.day, recovery, day?.strain, rest)) {
        value = null
        val metric = day
        if (metric != null) {
            delay(1_500)
            value = runCatching {
                AlkeSynthesisAi.generate(context, vm, metric.day, recovery, metric.strain, rest)
            }.getOrNull()
        }
    }
    AlkePage {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Today", color = A.white, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(dateText, color = A.muted, fontSize = 13.sp)
            Text("Your performance and recovery, in balance.", color = A.muted, fontSize = 12.sp)
        }
        AlkePanel(Modifier.fillMaxWidth(), onClick = { onMetric("recovery") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BODY READINESS", color = A.cyan, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                AlkePill(readinessLabel, A.gold)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlkeGauge(label = "Recovery", value = recovery, fraction = ((recovery ?: 0.0) / 100.0).toFloat(), suffix = "%")
            }
            Text(
                if (recovery == null) "No recovery score yet" else
                    "Readiness: $readinessLabel · Recovery ${recovery.roundToInt()}%",
                color = if (recovery == null) A.muted else A.green,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 13.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlkeMetricTile("HRV", niceOne(day?.avgHrv, " ms"), Icons.Filled.FavoriteBorder, Modifier.weight(1f), A.cyan, { onMetric("hrv") })
            AlkeMetricTile("RHR", day?.restingHr?.let { "$it bpm" } ?: "—", Icons.Filled.Favorite, Modifier.weight(1f), A.gold, { onMetric("rhr") })
            AlkeMetricTile("Sleep", niceMinutes(day?.totalSleepMin), Icons.Filled.Bedtime, Modifier.weight(1f), A.blue, onSleep)
            AlkeMetricTile("Recovery", recovery?.let { "${it.roundToInt()}%" } ?: "—", Icons.Filled.MonitorHeart, Modifier.weight(1f), A.green, { onMetric("recovery") })
        }
        AlkePanel(Modifier.fillMaxWidth(), onClick = onCoach) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(A.gold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = A.gold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TODAY'S FOCUS", color = A.gold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        aiSynthesis?.take(280)
                            ?: brief?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(260)
                            ?: offlineSynthesis,
                        color = A.white, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                    Text(
                        if (aiSynthesis != null || brief != null) "ALKE AI Coach · Open conversation"
                        else "ALKE Synthesis · Explore with AI Coach",
                        color = A.cyan, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
        }
        AlkeSectionTitle("Today's Activity")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlkeMetricTile("Day Strain", niceOne(day?.strain), Icons.Filled.DirectionsRun,
                Modifier.weight(1f), A.gold, { onMetric("strain") })
            AlkeMetricTile("Active Time", workoutMinutes?.let { niceMinutes(it) } ?: "—",
                Icons.Filled.MonitorHeart, Modifier.weight(1f), A.blue, onTrain)
        }
        AlkePanel(Modifier.fillMaxWidth(), onClick = onSteps) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = A.cyan)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("STEPS", color = A.cyan, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Text(
                        lastSteps?.value?.let {
                            java.text.NumberFormat.getIntegerInstance().format(it.roundToInt())
                        } ?: "—",
                        color = A.white, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        lastSteps?.day?.let { "Last recorded · $it" } ?: "Awaiting step data",
                        color = A.muted, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open steps history",
                    tint = A.cyan, modifier = Modifier.size(22.dp))
            }
        }
        Text("Active Time reflects recorded workouts; unavailable activity is never estimated.",
            color = A.muted, fontSize = 10.sp)
        // Tapping this compact real/latest BPM card retains the existing detailed
        // Heart Rate Timeline route, chart, workout icons, sleep bands and gestures.
        AlkeHeartRatePreview(vm, onHeartRateTimeline)
        AlkeSectionTitle("My Day")
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).clickable(onClick = onSleep).padding(vertical = 8.dp)) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = A.blue)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sleep & overnight recovery", color = A.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (day?.totalSleepMin != null) "${niceMinutes(day?.totalSleepMin)} of recorded sleep"
                        else "Awaiting a recorded night",
                        color = A.muted, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).clickable { onMetric("skin") }
                .padding(vertical = 8.dp)) {
                Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = A.cyan)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Skin Temperature", color = A.white, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Nighttime readings and temperature trends",
                        color = A.muted, fontSize = 11.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).clickable(onClick = onTrain).padding(vertical = 8.dp)) {
                Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = A.gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Training & activities", color = A.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (todayWorkouts.isEmpty()) "No recorded workouts to display"
                        else "${todayWorkouts.size} recorded session(s) · ${niceMinutes(workoutMinutes)}",
                        color = A.muted, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).clickable(onClick = onProgress).padding(vertical = 8.dp)) {
                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = A.cyan)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Today's progress", color = A.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        recovery?.let { "Recovery ${it.roundToInt()}% · Review your trends" }
                            ?: "Your health trends will appear as data arrives",
                        color = A.muted, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
        }
    }
}

/**
 * Four primary Train surfaces requested for ALKE:
 * Calories, Most Active Sports, Add Workout, and Active Calories calendar.
 * The understated History action opens the ORIGINAL WorkoutsScreen, preserving
 * live start/stop, full session details, filters, reports, and editing.
 */
@Composable
fun AlkeTrainingExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
    onWorkouts: () -> Unit,
) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val sessions by vm.workouts.collectAsStateWithLifecycle()
    val sortedSessions = remember(sessions) { sessions.sortedByDescending { it.startTs } }
    val rankedSports = remember(sortedSessions) {
        sortedSessions.groupBy { WorkoutEditing.displaySport(it.sport) }
            .map { (sport, rows) ->
                Triple(sport, rows.size, rows.sumOf { it.durationS ?: 0.0 })
            }
            .sortedWith(compareByDescending<Triple<String, Int, Double>> { it.second }
                .thenByDescending { it.third })
    }
    val validCalories = remember(sortedSessions) {
        sortedSessions.mapNotNull { it.energyKcal?.takeIf { value -> value.isFinite() && value >= 0.0 } }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.loadWorkouts() }

    AlkePage {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Train", color = A.white, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("History", modifier = Modifier.clickable(onClick = onWorkouts)
                .padding(horizontal = 4.dp, vertical = 10.dp),
                color = A.cyan, fontSize = 13.sp)
        }
        // Original all-source workout summary; unavailable calories are not estimated.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlkePanel(Modifier.weight(1f)) {
                Text("CALORIES", color = A.gold, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text(if (validCalories.isEmpty()) "—"
                    else String.format(Locale.US, "%,.0f", validCalories.sum()),
                    color = A.white, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1)
                Text("Workout kcal · all sessions", color = A.muted, fontSize = 10.sp,
                    maxLines = 1)
            }
            AlkePanel(Modifier.weight(1f)) {
                Text("MOST ACTIVE SPORTS", color = A.gold, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1)
                Text(rankedSports.firstOrNull()?.first ?: "—",
                    color = A.white, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rankedSports.firstOrNull()?.let {
                    "${it.second} recorded sessions"
                } ?: "No sessions yet", color = A.muted, fontSize = 10.sp, maxLines = 1)
            }
        }
        AlkePanel(Modifier.fillMaxWidth(), onClick = { showAddDialog = true }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = A.gold,
                    modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add Workout", color = A.white, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Log a past session", color = A.muted, fontSize = 11.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = A.cyan)
            }
        }
        AlkeActiveCaloriesCalendar(vm, days)
    }
    if (showAddDialog) {
        ManualWorkoutDialog(vm, editing = null, onDismiss = { showAddDialog = false },
            onSave = { row, replacing ->
                vm.saveManualWorkout(row, replacing)
                showAddDialog = false
            })
    }
}

/** ALKE native presentation of the EXISTING, parity-tested ActivityHeatmap model. */
@Composable
private fun AlkeActiveCaloriesCalendar(vm: AppViewModel, days: List<DailyMetric>) {
    // The original workout calendar core uses daily active calories. Those may
    // come from imported Apple/Health Connect (AppleDaily), not only on-device
    // DailyMetric.activeKcalEst. Prior ALKE demos already seeded 120 days of
    // AppleDaily, so read the same imported-first source precedence as the
    // existing active_kcal detail rather than showing an empty calendar.
    val today = LocalDate.now().toString()
    val imported by produceState<Map<String, Double>>(
        initialValue = emptyMap(), key1 = vm.activeStrapId, key2 = days,
    ) {
        val from = LocalDate.now().minusWeeks(14).toString()
        value = runCatching {
            val byDay = linkedMapOf<String, Double>()
            // Health Connect wins within imports when both sources include a day.
            for (row in vm.repo.appleDaily("health-connect", from, today) +
                vm.repo.appleDaily("apple-health", from, today)) {
                val kcal = row.activeKcal
                if (kcal != null && kcal.isFinite() && kcal >= 0.0) {
                    byDay.putIfAbsent(row.day, kcal)
                }
            }
            byDay
        }.getOrDefault(emptyMap())
    }
    val values = remember(days, imported) {
        val byDay = linkedMapOf<String, Double>()
        for (day in days) {
            val kcal = day.activeKcalEst
            if (kcal != null && kcal.isFinite() && kcal >= 0.0) {
                byDay[day.day] = kcal
            }
        }
        // Imported actual energy always wins over the HR-derived estimate.
        byDay.putAll(imported)
        byDay
    }
    val grid = remember(values, today) { ActivityHeatmap.build(values, today) }
    val shades = listOf(
        A.stroke.copy(alpha = 0.34f),
        A.gold.copy(alpha = 0.25f),
        A.gold.copy(alpha = 0.48f),
        A.gold.copy(alpha = 0.72f),
        A.gold,
    )
    AlkePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ACTIVE CALORIES", color = A.gold, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Last 13 weeks", color = A.muted, fontSize = 12.sp)
            }
            if (!grid.isEmpty) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format(Locale.US, "%,.0f", grid.total), color = A.white,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("kcal · recorded days", color = A.muted, fontSize = 10.sp)
                }
            }
        }
        if (grid.isEmpty) {
            Text("Daily active-calorie readings will appear here when available.",
                color = A.muted, fontSize = 12.sp, lineHeight = 18.sp)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 3.dp
                val leftInset = 5.dp
                val cell = ((maxWidth - leftInset - gap * (grid.weeks - 1)) / grid.weeks)
                    .coerceAtLeast(3.dp)
                Canvas(Modifier.fillMaxWidth().height(cell * 7 + gap * 6)) {
                    val step = cell.toPx() + gap.toPx()
                    for (c in 0 until grid.weeks) {
                        for (r in 0 until 7) {
                            val reading = grid.columns[c][r]
                            val topLeft = Offset(leftInset.toPx() + c * step, r * step)
                            val rect = androidx.compose.ui.geometry.Size(cell.toPx(), cell.toPx())
                            val radius = androidx.compose.ui.geometry.CornerRadius(cell.toPx() * 0.22f)
                            drawRoundRect(shades[reading.level.coerceIn(0, 4)],
                                topLeft = topLeft, size = rect, cornerRadius = radius)
                            if (reading.day == today) {
                                drawRoundRect(A.white, topLeft = topLeft, size = rect,
                                    cornerRadius = radius, style = Stroke(width = 1.3.dp.toPx()))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                Text("Less", color = A.muted, fontSize = 10.sp)
                shades.forEach { shade ->
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(shade))
                }
                Text("More", color = A.muted, fontSize = 10.sp)
            }
        }
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
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
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
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
    onHealth: () -> Unit,
    onVo2: () -> Unit,
    onBodyAge: () -> Unit,
    onTrends: () -> Unit,
    onSteps: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val vo2 by produceState<Double?>(initialValue = null, key1 = vm.activeStrapId) {
        value = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull()
    }
    // Reuse the original steps-history data resolver: real wearable measurements
    // first, then Apple/Health Connect imports, then the motion-model estimate.
    // One source of truth for both this overview and its detail chart.
    val stepsModel by produceState<VitalDetailModel?>(
        initialValue = null, key1 = vm.activeStrapId, key2 = days,
    ) {
        value = runCatching { buildSeriesVitalDetail(vm, "steps_est") }.getOrNull()
    }
    val stepsReadings = stepsModel?.readings.orEmpty().sortedBy { it.day }
    val latestSteps = stepsReadings.lastOrNull()
    AlkePage {
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
        AlkePanel(Modifier.fillMaxWidth(), onClick = onSteps) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("STEPS", color = A.cyan, letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text(
                        latestSteps?.value?.let {
                            java.text.NumberFormat.getIntegerInstance().format(it.roundToInt())
                        } ?: "—",
                        color = A.white, fontWeight = FontWeight.Bold, fontSize = 39.sp,
                    )
                    Text(
                        latestSteps?.day?.let { "Last recorded · $it" } ?: "Awaiting step data",
                        color = A.muted, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open steps history",
                    tint = A.cyan, modifier = Modifier.size(24.dp))
            }
            // Compact 14-reading history; the full date-range chart and exact values
            // remain behind the card. Missing readings are never invented.
            val history = stepsReadings.takeLast(14)
            if (history.isNotEmpty()) {
                val max = history.maxOf { it.value }.coerceAtLeast(1.0)
                Row(Modifier.fillMaxWidth().height(73.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    history.forEach { reading ->
                        val height = (reading.value / max).coerceIn(0.0, 1.0)
                        Box(Modifier.weight(1f)
                            .height((7.0 + 64.0 * height).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(A.cyan.copy(alpha = 0.32f + 0.60f * height.toFloat())))
                    }
                }
                Text("Recent steps · Tap to view full history", color = A.muted, fontSize = 11.sp)
            }
        }
        AlkeSectionTitle("Recovery trend", "Full trends", onTrends)
        AlkeMiniBars(days.takeLast(7).map { it.day.takeLast(5) to it.recovery }, A.cyan)
        AlkeLinkRow("Vital signs", "HRV, RHR and measured physiology", Icons.Filled.MonitorHeart, onHealth)
        if (day == null) Text("Your progress screen will populate as measurements arrive.", color = A.muted, fontSize = 12.sp)
    }
}

@Composable
fun AlkeInsightExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
    onCoach: () -> Unit,
    onIntelligence: () -> Unit,
    onHealth: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val recovery = day?.recovery
    AlkePage {
        AlkeHeading("Insight", "Your data, made meaningful.")
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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


/**
 * ALKE-exclusive Heart Rate Timeline route.
 *
 * Only the chart data/query and timeline gestures are reused from the frozen
 * implementation. The host is a native ALKE screen, not a redirect to legacy Today.
 * No copy of HR records, workout data, scoring algorithm, or database table is created.
 */
@Composable
fun AlkeHeartRateTimelineExperience(
    vm: AppViewModel,
    onBack: () -> Unit,
    onLive: () -> Unit,
    onWorkouts: () -> Unit,
) {
    val todayMetric by vm.today.collectAsStateWithLifecycle()
    val recentDays by vm.recentDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var daysBack by rememberSaveable { mutableIntStateOf(0) }
    val currentDay = logicalDayNow()
    val selectedDay = currentDay.minusDays(daysBack.toLong())
    val metric = if (daysBack == 0) todayMetric
        else recentDays.lastOrNull { it.day == selectedDay.toString() }

    AlkePage {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = A.cyan)
                Text("Back", color = A.cyan, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
        }
        AlkeHeading(
            "Heart Rate Timeline",
            "Your daily heart-rate story, with sleep and workouts in context.",
        )
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = A.cyan)
                Text("HEART RATE & ACTIVITY", color = A.cyan, fontSize = 11.sp,
                    letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Follow your heart-rate curve throughout the day. Workout markers stay aligned to the time of each recorded session.",
                color = A.muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(A.cyan))
                Text("Heart rate", color = A.white, fontSize = 11.sp)
                Box(Modifier.size(8.dp).clip(CircleShape).background(A.gold))
                Text("Workout", color = A.white, fontSize = 11.sp)
                Box(Modifier.size(8.dp).clip(CircleShape).background(A.blue))
                Text("Sleep", color = A.white, fontSize = 11.sp)
            }
        }
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { daysBack = (daysBack + 1).coerceAtMost(30) },
                    enabled = daysBack < 30) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day",
                        tint = if (daysBack < 30) A.cyan else A.muted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (daysBack == 0) "TODAY" else if (daysBack == 1) "YESTERDAY" else "HISTORY",
                        color = A.gold, fontSize = 10.sp, letterSpacing = 1.3.sp,
                    )
                    Text(selectedDay.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
                        color = A.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { daysBack = (daysBack - 1).coerceAtLeast(0) },
                    enabled = daysBack > 0) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next day",
                        tint = if (daysBack > 0) A.cyan else A.muted)
                }
            }
        }
        // The chart retains the inherited 5-minute HR buckets, zoom/scrub, sleep
        // bands and aligned workout sport glyphs, now styled with ALKE colors.
        HeartRateTrendCard(
            viewModel = vm,
            days = recentDays,
            selectedDay = selectedDay,
            today = currentDay,
            displayMetric = metric,
            dayCycleMode = NoopPrefs.dayCycleMode(context),
            alkeStyle = true,
        )
        AlkeLinkRow(
            "Live HR",
            "Real-time BPM, HR zones and training controls",
            Icons.Filled.Favorite, onLive, A.cyan,
        )
        AlkeLinkRow(
            "Workout History",
            "Review recorded sessions and activity contributions",
            Icons.Filled.DirectionsRun, onWorkouts, A.gold,
        )
    }
}

/**
 * ALKE discovery catalog: the app's actual module routes, shown once each.
 * Dock-pinned modules are already reachable from the dock; Me shortcuts remain in Me.
 * No route, feature, saved preference or data is removed.
 */
@Composable
fun AlkeAllModulesExperience(onNavigate: (String) -> Unit) {
    var expanded by remember { mutableStateOf(setOf("Insights", "Activity")) }
    val groups = alkeAllModulesGroups(BottomBarModuleStore.selected)
    val pinnedLabels = mutableListOf<String>()
    for (destination in BottomBarModuleStore.selected) pinnedLabels += alkeModuleLabel(destination)
    AlkePage {
        AlkeHeading("All modules", "Explore features not already in your dock or Me.")
        AlkePanel(Modifier.fillMaxWidth()) {
            Text("PINNED MODULES", color = A.gold, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)
            val pinned = BottomBarModuleStore.selected
            Text(
                if (pinned.isEmpty()) "Your dock has only Today and Me."
                else pinnedLabels.joinToString("  ·  "),
                color = A.white, fontSize = 13.sp,
            )
            Text("Customize your dock in Me. Pinned modules and Me shortcuts are not repeated below.",
                color = A.muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        groups.forEach { group ->
            val isOpen = group.header in expanded
            AlkePanel(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            expanded = if (isOpen) expanded - group.header else expanded + group.header
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.header, color = A.white, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${group.items.size}", color = A.muted, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = if (isOpen) "Collapse" else "Expand",
                        tint = A.cyan, modifier = Modifier
                            .size(20.dp).graphicsLayer { rotationZ = if (isOpen) 90f else 0f })
                }
                if (isOpen) {
                    group.items.forEach { dest ->
                        AlkeLinkRow(
                            title = alkeModuleLabel(dest),
                            subtitle = "Open module",
                            icon = dest.icon,
                            onClick = { onNavigate(dest.route) },
                            accent = A.cyan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlkeMeExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    unreadCount: Int,
    onProfile: () -> Unit,
    onDevices: () -> Unit,
    onSettings: () -> Unit,
    onModules: () -> Unit,
    onSources: () -> Unit,
    onBackupRestore: () -> Unit,
) {
    var showDockEditor by remember { mutableStateOf(false) }
    AlkePage {
        AlkeHeading("Me", "Your preferences, devices and data, in one place.")
        AlkeSectionTitle("Your experience")
        AlkeLinkRow("My profile", "Personal settings and metrics", Icons.Filled.FavoriteBorder, onProfile)
        AlkeLinkRow("Devices", "Connect and manage wearable sensors", Icons.Filled.Sensors, onDevices)
        AlkeLinkRow("Data sources", "Imports, Health Connect and provenance", Icons.Filled.Storage, onSources)
        AlkeLinkRow("Settings", "Privacy, preferences and appearance", Icons.Filled.Settings, onSettings)
        AlkeLinkRow("Customize Dock", "Choose and reorder your quick navigation", Icons.Filled.Tune,
            { showDockEditor = true }, A.cyan)
        AlkeLinkRow("Backup & Restore", "Import your ALKE or legacy backup", Icons.Filled.Storage,
            onBackupRestore, A.blue)
        AlkeLinkRow("All modules", "Explore the complete ALKE toolkit", Icons.Filled.Tune, onModules, A.gold)
    }
    if (showDockEditor) AlkeDockEditorDialog(onDismiss = { showDockEditor = false })
}

/** The real ALKE dock editor: same persisted state as GlassBottomBar, in ALKE's own palette. */
@Composable
private fun AlkeDockEditorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = BottomBarModuleStore.selected
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(A.bg)
                .border(1.dp, A.stroke, RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Customize Dock", color = A.white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                "Today and Me stay fixed. Pin up to three modules in any order.",
                color = A.muted, fontSize = 12.sp,
            )
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                selected.forEach { destination ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(destination.icon, contentDescription = null, tint = A.cyan,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(alkeModuleLabel(destination), modifier = Modifier.weight(1f),
                            color = A.white, fontSize = 12.sp, maxLines = 1)
                        TextButton(onClick = { BottomBarModuleStore.move(context, destination, -1) }) {
                            Text("↑", color = A.cyan)
                        }
                        TextButton(onClick = { BottomBarModuleStore.move(context, destination, 1) }) {
                            Text("↓", color = A.cyan)
                        }
                        TextButton(onClick = { BottomBarModuleStore.setPinned(context, destination, false) }) {
                            Text("×", color = A.gold)
                        }
                    }
                }
                Text("AVAILABLE MODULES", color = A.gold, fontSize = 10.sp,
                    letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                configurableBottomBarDestinations.filterNot { it in selected }.forEach { destination ->
                    val canAdd = selected.size < BottomBarModuleStore.MAX_PINNED
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = canAdd) {
                                BottomBarModuleStore.setPinned(context, destination, true)
                            }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(destination.icon, contentDescription = null, tint = A.muted,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(alkeModuleLabel(destination), modifier = Modifier.weight(1f),
                            color = A.white, fontSize = 13.sp)
                        Text(if (canAdd) "Add" else "Full",
                            color = if (canAdd) A.cyan else A.muted, fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { BottomBarModuleStore.reset(context) }) {
                    Text("Reset", color = A.muted)
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", color = A.cyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
