package com.otidotid.alke.ui

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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.otidotid.alke.R
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
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
import com.otidotid.alke.data.DailyMetric
import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.WorkoutRow
import com.otidotid.alke.analytics.RestScorer
import com.otidotid.alke.analytics.ActivityHeatmap
import com.otidotid.alke.analytics.ReadinessEngine
import com.otidotid.alke.analytics.FitnessAgeEngine
import com.otidotid.alke.analytics.FitnessAgePaceEngine
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
    // No perpetual battery pulse while idle. The ALKE sync halo below is
    // driven solely by the real BLE/history-sync state.
    val hasSyncError = !syncing && !live.lastSyncError.isNullOrBlank()
    val battery = if (live.connected) {
        if (activeIsOura) ringBattery?.takeIf { it in 0..100 }
        else live.batteryPct?.takeIf { it.isFinite() && it in 0.0..100.0 }?.roundToInt()
    } else null
    var showSyncHub by remember { mutableStateOf(false) }
    // Header belongs to the parent Scaffold (outside NavHost): live sync motion never
    // restarts when switching modules. Never pause an active sync merely for navigation.
    val syncValues = if (syncing) {
        // Keep this transition active for the entire true sync lifecycle; it is
        // owned by the global header, never by a module that navigation can dispose.
        val syncMotion = rememberInfiniteTransition(label = "alke-global-sync")
        val spin by syncMotion.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "sync-rotation",
        )
        val pulse by syncMotion.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse),
            label = "sync-aurora-pulse",
        )
        spin to pulse
    } else 0f to 0f
    val spin = syncValues.first
    val syncPulse = syncValues.second
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // This is the single global header, shared by Today and every module.
        // The icon/wordmark follow the approved minimal cyan ALKE identity.
        Row(
            modifier = Modifier.clip(RoundedCornerShape(15.dp))
                .clickable { showSyncHub = true }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (syncing) {
                    // Preserve the original live syncing animation around the NEW mark.
                    // It is hosted above NavHost, so changing screens cannot restart it.
                    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
                        val ring = 3.7.dp.toPx()
                        val inset = ring / 2f + 3.dp.toPx()
                        val ringSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                        val top = Offset(inset, inset)
                        drawCircle(
                            A.cyan.copy(alpha = 0.10f + 0.14f * syncPulse),
                            radius = size.minDimension * 0.48f,
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(A.cyan, A.gold, A.white, A.cyan)),
                            startAngle = 0f, sweepAngle = 215f, useCenter = false,
                            topLeft = top, size = ringSize,
                            style = Stroke(width = ring, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = A.gold.copy(alpha = 0.66f + 0.34f * syncPulse),
                            startAngle = 250f, sweepAngle = 42f, useCenter = false,
                            topLeft = top, size = ringSize,
                            style = Stroke(width = ring, cap = StrokeCap.Round),
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.alke_header_minimal_mark),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(39.dp).graphicsLayer {
                        val scale = if (syncing) 1f + syncPulse * 0.07f else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                )
                if (hasSyncError || unreadCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                            .background(if (hasSyncError) A.gold else A.cyan),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "ALKE", color = A.white, fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp,
                maxLines = 1,
            )
        }

        // Battery number still comes from the CURRENT connected device.
        // No stale charge value after disconnect, and no second battery shortcut.
        val batteryChipShape = RoundedCornerShape(50.dp)
        Row(
            modifier = Modifier
                .clip(batteryChipShape)
                .background(A.cardDark.copy(alpha = 0.76f))
                .border(1.dp, A.stroke.copy(alpha = 0.68f), batteryChipShape)
                .semantics {
                    stateDescription = when {
                        battery == null -> "Wearable battery unavailable"
                        live.charging == true -> "Wearable battery $battery percent, charging"
                        else -> "Wearable battery $battery percent"
                    }
                }
                .padding(start = 13.dp, end = 11.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                battery?.let { "$it%" } ?: "—",
                color = if (battery == null) A.muted
                        else if (battery <= 20) A.gold else A.white,
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 1,
            )
            Box(
                Modifier.width(1.dp).height(21.dp)
                    .background(A.muted.copy(alpha = 0.34f)),
            )
            Box(Modifier.size(31.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.alke_header_wearable_outline),
                    contentDescription = null,
                    tint = if (battery == null) A.muted else Color.Unspecified,
                    modifier = Modifier.size(31.dp),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                battery == null -> A.muted.copy(alpha = 0.45f)
                                battery <= 20 || live.charging == true -> A.gold
                                else -> A.cyan
                            },
                        ),
                )
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


/**
 * Stage 1 progressive disclosure: display fewer panels by default while preserving
 * their original Compose content and routes. This component never queries or mutates
 * any recorded data, derived score, or settings outside ephemeral presentation state.
 */
@Composable
private fun AlkeExpandableSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(shape)
                .background(A.card)
                .border(1.dp, A.stroke.copy(alpha = 0.55f), shape)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = A.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = A.muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = A.cyan,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f },
            )
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
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
    animateContinuously: Boolean = false,
) {
    val poseStill = rememberPoseStill()
    val showMotion = animateContinuously && !poseStill
    val progress by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (poseStill) tween(0) else tween(1100, easing = FastOutSlowInEasing),
        label = "readiness-fill",
    )
    // Today and Insights share this visible living aura + orbit, but no idle
    // frame loop runs when battery saver or the motion preference disables motion.
    val motion = if (showMotion) rememberInfiniteTransition(label = "today-body-readiness") else null
    val orbit = motion?.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "readiness-orbit",
    )?.value ?: 0f
    val breathe = motion?.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse),
        label = "readiness-glow",
    )?.value ?: 0f
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
            if (showMotion) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(A.cyan.copy(alpha = 0.12f + 0.15f * breathe), Color.Transparent),
                        center = Offset(center.x + size.width * 0.12f * kotlin.math.sin(Math.toRadians(orbit.toDouble())).toFloat(),
                            center.y + size.height * 0.12f * kotlin.math.cos(Math.toRadians(orbit.toDouble())).toFloat()),
                        radius = size.minDimension * 0.44f,
                    ),
                    radius = size.minDimension * 0.44f,
                )
            }
            if (value != null && progress > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(colors + colors.first()),
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false, topLeft = top, size = arc,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
            if (showMotion) {
                // Bright, double-color moving highlight. Clearly readable during
                // smoke tests, including when a static Recovery number is displayed.
                drawArc(
                    color = A.cyan.copy(alpha = 0.75f + 0.25f * breathe),
                    startAngle = 135f + orbit,
                    sweepAngle = 43f,
                    useCenter = false, topLeft = top, size = arc,
                    style = Stroke(width = width * 0.80f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = A.gold.copy(alpha = 0.72f + 0.28f * breathe),
                    startAngle = 170f + orbit,
                    sweepAngle = 23f,
                    useCenter = false, topLeft = top, size = arc,
                    style = Stroke(width = width * 0.70f, cap = StrokeCap.Round),
                )
                val angle = Math.toRadians((135f + orbit + 42f).toDouble())
                val radius = (arc.width * .5f).coerceAtLeast(0f)
                val movingCenter = Offset(
                    center.x + radius * kotlin.math.cos(angle).toFloat(),
                    center.y + radius * kotlin.math.sin(angle).toFloat(),
                )
                drawCircle(
                    color = A.white.copy(alpha = 0.75f + 0.25f * breathe),
                    radius = width * 0.40f + 1.dp.toPx() * breathe,
                    center = movingCenter,
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

/**
 * Stage 1: two concise companions to Charge, without inventing a new score.
 * Every value is handed in by the original DailyMetric / RestScorer pipeline.
 */
@Composable
private fun AlkeOutlookScoreTile(
    title: String,
    context: String,
    value: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    // Daily Outlook: labels and their numbers sit on ONE horizontal line,
    // rather than stacking three short lines and leaving the narrow tile empty.
    // Keep the Activity/Sleep context under its label and the big numeric
    // reading right-aligned. Two 72dp tiles + 8dp gap match the Charge gauge.
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape)
            .background(A.cardDark)
            .border(1.dp, tint.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title.uppercase(Locale.ROOT), color = tint,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, maxLines = 1)
            Text(context, color = A.muted, fontSize = 10.sp, maxLines = 1)
        }
        Text(value, color = A.white, fontSize = 27.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Shared Me accordion chrome. More Tools and Settings must have the same
 * 43dp icon badge, card, spacing, type scale and animated direction cue.
 * SettingsScreen owns the Settings body below the Me header (one scroll host).
 */
@Composable
private fun AlkeMeDisclosureHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    accent: Color = A.cyan,
) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(A.card)
            .border(1.dp, A.stroke.copy(alpha = 0.55f), shape)
            .clickable(onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(43.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = A.white, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = A.muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = A.cyan,
            modifier = Modifier.size(21.dp)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
        )
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
    onHypopneaDetail: () -> Unit = {},
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val recentDays by vm.recentDays.collectAsStateWithLifecycle()
    // Hypopnea is a FIXED card between Heart Rate and Explore Your Trends.
    // The morning verdict remains stable within one wake-day but cannot leak
    // into the next day's UNAVAILABLE or WAITING assessment.
    val hypopneaRevision by SleepHypopneaData.revision.collectAsStateWithLifecycle()
    val hypopneaContext = LocalContext.current
    var todayHypopneaState by remember(vm.activeStrapId) {
        mutableStateOf(HypopneaTodaySelection.State(
            null,
            com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Assessment(
                com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Risk.WAITING,
            ),
        ))
    }
    var todayHabitualMidsleep by remember(vm.activeStrapId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(vm.activeStrapId) {
        todayHabitualMidsleep = try {
            vm.repo.habitualMidsleepSec(vm.activeStrapId)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }
    LaunchedEffect(vm.activeStrapId, recentDays, hypopneaRevision, todayHabitualMidsleep) {
        val nextNight = try {
            SleepHypopneaData.latestNight(
                hypopneaContext, vm.repo, vm.activeStrapId,
                habitualMidsleepSec = todayHabitualMidsleep,
            )
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            SleepHypopneaData.LatestNight(
                null, null, null,
                com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Assessment(
                    com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Risk.UNAVAILABLE,
                ),
            )
        }
        todayHypopneaState = HypopneaTodaySelection.choose(
            todayHypopneaState,
            HypopneaTodaySelection.State(nextNight.wakeDay, nextNight.assessment),
        )
    }
    val hypopneaToday = todayHypopneaState.assessment
    // The existing Steps detail resolver provides real, imported, and estimated
    // readings with the same precedence as Progress. Show ONLY today's reading
    // in the Today tile; a prior date must never masquerade as today's steps.
    val stepsModel by produceState<VitalDetailModel?>(
        initialValue = null, key1 = vm.activeStrapId, key2 = recentDays,
    ) {
        value = runCatching { buildSeriesVitalDetail(vm, "steps_est") }.getOrNull()
    }
    val todaysSteps = stepsModel?.readings.orEmpty().lastOrNull {
        it.day == LocalDate.now().toString()
    }?.value?.takeIf { it.isFinite() && it >= 0.0 }
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
        // The only daily *score hero*: Charge, Effort and Rest share one source of
        // truth. All original detail routes and algorithms remain untouched.
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR DAILY OUTLOOK", color = A.cyan, fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                AlkePill(readinessLabel, A.gold)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(157.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onMetric("recovery") },
                    contentAlignment = Alignment.Center,
                ) {
                    AlkeGauge(
                        label = "Charge",
                        value = recovery,
                        fraction = ((recovery ?: 0.0) / 100.0).toFloat(),
                        diameter = 156.dp,
                        suffix = "%",
                        animateContinuously = true,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlkeOutlookScoreTile(
                        title = "Effort", context = "Activity",
                        value = day?.strain?.let { UnitFormatter.effortDisplay(it, UnitPrefs.effortScale(context)) } ?: "—",
                        tint = A.gold, onClick = { onMetric("strain") },
                    )
                    AlkeOutlookScoreTile(
                        title = "Rest", context = "Sleep",
                        value = rest?.takeIf { it.isFinite() && it in 0.0..100.0 }
                            ?.roundToInt()?.toString() ?: "—",
                        tint = A.blue, onClick = onSleep,
                    )
                }
            }
            Text("Scores use the existing ALKE analysis · Tap for details",
                color = A.muted, fontSize = 11.sp)
        }
        AlkeSectionTitle("Key signals")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlkeMetricTile("HRV", niceOne(day?.avgHrv, " ms"), Icons.Filled.FavoriteBorder, Modifier.weight(1f), A.cyan, { onMetric("hrv") })
            AlkeMetricTile("RHR", day?.restingHr?.let { "$it bpm" } ?: "—", Icons.Filled.Favorite, Modifier.weight(1f), A.gold, { onMetric("rhr") })
            AlkeMetricTile("Sleep time", niceMinutes(day?.totalSleepMin), Icons.Filled.Bedtime, Modifier.weight(1f), A.blue, onSleep)
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
        // Day Strain is already visible as Effort in the single Daily Outlook hero.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlkeMetricTile("Steps",
                todaysSteps?.let { java.text.NumberFormat.getIntegerInstance().format(it.roundToInt()) } ?: "—",
                Icons.Filled.DirectionsWalk, Modifier.weight(1f), A.cyan, onSteps)
            AlkeMetricTile("Active Time", workoutMinutes?.let { niceMinutes(it) } ?: "—",
                Icons.Filled.MonitorHeart, Modifier.weight(1f), A.blue, onTrain)
        }
        Text("Active Time reflects recorded workouts; unavailable activity is never estimated.",
            color = A.muted, fontSize = 10.sp)
        // Tapping this compact real/latest BPM card retains the existing detailed
        // Heart Rate Timeline route, chart, workout icons, sleep bands and gestures.
        AlkeHeartRatePreview(vm, onHeartRateTimeline)
        // Fixed ALKE Today order: HEART RATE -> HYPOPNEA -> EXPLORE TRENDS.
        // The card reflects a completed night; an unclassified/in-progress
        // window never makes a false clinical claim or shows "Risk PENDING".
        if (hypopneaToday.isReady) {
            val risk = hypopneaToday.risk
            val riskTone = when (risk) {
                com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Risk.LOW -> A.green
                com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Risk.MEDIUM -> A.gold
                com.otidotid.alke.analytics.SleepHypopneaRiskEngine.Risk.HIGH -> Color(0xFFF08283)
                else -> A.muted
            }
            AlkeLinkRow(
                title = "Hypopnea Risk ${risk.name}",
                subtitle = "Last completed sleep · Open Sleep details",
                icon = Icons.Filled.Bedtime,
                onClick = onHypopneaDetail,
                accent = riskTone,
            )
        }
        // Sleep and Train are already available in the metric tiles above.
        // The old three-row "My Day" recap repeated the same readings and was
        // removed from the presentation only; all source data and detail routes stay.
        AlkeLinkRow(
            "Explore your trends",
            "Fitness Age, weekly recovery and recorded history",
            Icons.Filled.ShowChart,
            onProgress,
        )
    }
}

/**
 * Train unifies the workout summary, live/manual controls, Active Calories,
 * Activity Breakdown, and All Sessions on one page. No separate History route.
 */
@Composable
fun AlkeTrainingExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
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
        Text("Train", color = A.white, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        // Keep the frequent workout controls and Activity Calories visible.
        AlkeTrainWorkoutActions(vm, onAdd = { showAddDialog = true })
        AlkeActiveCaloriesCalendar(vm, days)
        // Both original workout sections stay immediately below Active Calories.
        AlkeTrainHistorySections(vm)
        // Lifetime totals are available on demand, not repeated as primary KPIs.
        AlkeExpandableSection("Lifetime activity", "Recorded calories and most active sport") {
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
        }
    }
    if (showAddDialog) {
        ManualWorkoutDialog(vm, editing = null, onDismiss = { showAddDialog = false },
            onSave = { row, replacing ->
                vm.saveManualWorkout(row, replacing)
                showAddDialog = false
            })
    }
}

/**
 * Premium Start/Add row. A live start requires a bonded wearable; while recording
 * we reuse the original pause/resume/open/end banner and manual Add action.
 */
@Composable
private fun AlkeTrainWorkoutActions(vm: AppViewModel, onAdd: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val activeWorkout by vm.activeWorkout.collectAsStateWithLifecycle()
    var showStartWorkout by remember { mutableStateOf(false) }
    if (activeWorkout != null) {
        WorkoutStartSection(vm, onAdd = onAdd)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlkePanel(Modifier.weight(1f),
                onClick = if (live.bonded) ({ showStartWorkout = true }) else null) {
                Icon(Icons.Filled.DirectionsRun, contentDescription = null,
                    tint = if (live.bonded) A.gold else A.muted, modifier = Modifier.size(24.dp))
                Text("Start Workout", color = if (live.bonded) A.white else A.muted,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (live.bonded) "Record a live session" else "Connect a wearable",
                    color = A.muted, fontSize = 10.sp, maxLines = 1)
            }
            AlkePanel(Modifier.weight(1f), onClick = onAdd) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                    tint = A.cyan, modifier = Modifier.size(24.dp))
                Text("Add Workout", color = A.white, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Log a past session", color = A.muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
    if (showStartWorkout) {
        StartWorkoutSheet(vm = vm, onDismiss = { showStartWorkout = false })
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
        // Sleep details explain recorded duration and stages, rather than drawing
        // another scoring dial. The Rest score itself is centralized on Today.
        AlkePanel(Modifier.fillMaxWidth()) {
            Text("LAST RECORDED NIGHT", color = A.cyan, letterSpacing = 2.sp,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(niceMinutes(total), color = A.white, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(
                if (total == null) "Awaiting a recorded night" else "Recorded sleep duration · Open Sleep analysis for full details",
                color = A.muted, fontSize = 12.sp, lineHeight = 17.sp,
            )
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

/**
 * Quiet, premium Fitness Age hero: the arc is a clearly labelled 20–80 AGE SCALE,
 * not a fabricated "Healthspan score" or pace-of-aging prediction.
 * Never estimates absent metrics. Opens the existing detailed weekly Fitness Age.
 */
@Composable
private fun AlkeFitnessAgeHero(
    fitnessAge: Double?,
    readingDay: String?,
    chronologicalAge: Int,
    agePace: FitnessAgePaceEngine.Result,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val minAge = FitnessAgeEngine.minAge
    val maxAge = FitnessAgeEngine.maxAge
    val position = fitnessAge?.let {
        ((it - minAge) / (maxAge - minAge)).toFloat().coerceIn(0f, 1f)
    } ?: 0f
    var entered by remember(fitnessAge) { mutableStateOf(false) }
    LaunchedEffect(fitnessAge, reduceMotion) { entered = true }
    val animatedPosition by animateFloatAsState(
        targetValue = if (reduceMotion || entered) position else 0f,
        animationSpec = if (reduceMotion) tween(0)
        else tween(durationMillis = 940, easing = FastOutSlowInEasing),
        label = "fitness_age_reveal",
    )
    // Gentle continuous living-instrument motion. Never interpolate the stored age itself.
    // Reduced Motion produces an entirely static view; Compose stops running after navigation.
    val ambient = if (reduceMotion || fitnessAge == null) 0f else {
        val motion = rememberInfiniteTransition(label = "alke_fitness_age_ambient")
        motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 3_800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alke_fitness_age_breathe",
        ).value
    }
    // Repeating sweep is deliberately separate from the measured age pointer: the reading
    // stays at its exact position while decorative light travels along the instrument.
    val orbit = if (reduceMotion || fitnessAge == null) 0f else {
        val motion = rememberInfiniteTransition(label = "alke_age_orbit")
        motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 3_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "alke_age_orbit_sweep",
        ).value
    }
    // Annualized trend comes exclusively from the existing FitnessAgePaceEngine;
    // an improving (negative) slope is shown as AGE DOWN, never a fabricated value.
    val paceLabel = agePace.pace?.let { rate ->
        String.format(Locale.US, "%.1fx", kotlin.math.abs(rate)) +
            if (rate < 0.0) " AGE DOWN" else " AGING PACE"
    } ?: "PACE CALIBRATING"
    val difference = fitnessAge?.let { age ->
        if (chronologicalAge > 0) chronologicalAge.toDouble() - age else null
    }
    val comparison = when {
        difference == null -> "Add your age in Profile for a comparison"
        difference > 0.05 -> String.format(Locale.US, "%.1f years younger", difference)
        difference < -0.05 -> String.format(Locale.US, "%.1f years older", -difference)
        else -> "In line with your chronological age"
    }
    val comparisonColor = if (difference != null && difference > 0.05) A.green
        else if (difference != null && difference < -0.05) A.gold else A.white

    AlkePanel(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("FITNESS AGE", color = A.cyan,
                    letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Your fitness, in perspective", color = A.white,
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            AlkePill(paceLabel, A.gold)
        }
        // The age-scale position remains data-accurate; only glow and number breathe.
        Box(Modifier.fillMaxWidth().height(206.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(194.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 10.dp.toPx()
                    val inset = width / 2 + 10.dp.toPx()
                    val arcSize = Size(size.width - 2f * inset, size.height - 2f * inset)
                    val origin = Offset(inset, inset)
                    val radius = arcSize.width / 2f
                    // Continuous ALKE cyan/gold aurora beneath the age number.
                    val span = size.minDimension
                    val cyanCenter = Offset(
                        center.x + (orbit - 0.5f) * span * 0.38f,
                        center.y - span * 0.10f + ambient * span * 0.07f,
                    )
                    val goldCenter = Offset(
                        center.x - (orbit - 0.5f) * span * 0.30f,
                        center.y + span * 0.22f - ambient * span * 0.08f,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(A.cyan.copy(alpha = 0.24f + ambient * 0.26f),
                                Color.Transparent),
                            center = cyanCenter, radius = span * 0.43f,
                        ),
                        radius = span * 0.43f, center = cyanCenter,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(A.gold.copy(alpha = 0.16f + ambient * 0.22f),
                                Color.Transparent),
                            center = goldCenter, radius = span * 0.37f,
                        ),
                        radius = span * 0.37f, center = goldCenter,
                    )
                    drawArc(
                        color = A.stroke.copy(alpha = 0.72f), startAngle = 135f,
                        sweepAngle = 270f, useCenter = false, topLeft = origin,
                        size = arcSize, style = Stroke(width = width, cap = StrokeCap.Round),
                    )
                    if (fitnessAge != null) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(A.cyan, A.gold, A.cyan)),
                            startAngle = 135f, sweepAngle = 270f * animatedPosition,
                            useCenter = false, topLeft = origin,
                            size = arcSize, style = Stroke(width = width, cap = StrokeCap.Round),
                        )
                        val angle = Math.toRadians((135f + 270f * animatedPosition).toDouble())
                        val marker = Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * radius,
                            center.y + kotlin.math.sin(angle).toFloat() * radius,
                        )
                        drawCircle(A.cyan.copy(alpha = 0.11f + ambient * 0.22f),
                            radius = (8f + 4f * ambient).dp.toPx(), center = marker)
                        drawCircle(A.white, radius = (3.4f + 0.45f * ambient).dp.toPx(), center = marker)
                        // Visible orbiting light, not a second reading marker.
                        val orbitAngle = Math.toRadians((135f + 270f * orbit).toDouble())
                        val orbitDot = Offset(
                            center.x + kotlin.math.cos(orbitAngle).toFloat() * radius,
                            center.y + kotlin.math.sin(orbitAngle).toFloat() * radius,
                        )
                        drawCircle(A.gold.copy(alpha = 0.18f), radius = 12.dp.toPx(), center = orbitDot)
                        drawCircle(A.white.copy(alpha = 0.90f), radius = 3.dp.toPx(), center = orbitDot)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    val ageText = fitnessAge?.let {
                        String.format(Locale.US, "%.1f", it)
                    } ?: "—"
                    Box(contentAlignment = Alignment.Center) {
                        // A soft neon echo behind the real numeral gives an observable
                        // perpetual breathing/rolling effect without changing its digits.
                        Text(
                            ageText,
                            color = A.cyan.copy(alpha = 0.08f + ambient * 0.32f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp, lineHeight = 55.sp,
                            modifier = Modifier.graphicsLayer {
                                val scale = 1.04f + ambient * 0.14f
                                scaleX = scale
                                scaleY = scale
                                translationY = -(ambient * 2.dp.toPx())
                            },
                        )
                        Text(
                            ageText,
                            color = androidx.compose.ui.graphics.lerp(A.white, A.gold, ambient * 0.58f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp, lineHeight = 55.sp,
                            modifier = Modifier.graphicsLayer {
                                val scale = 1f + ambient * 0.082f
                                scaleX = scale
                                scaleY = scale
                                translationY = -(ambient * 3.dp.toPx())
                            },
                        )
                    }
                    Text("YEARS", color = A.muted, fontSize = 11.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (fitnessAge != null) {
            Text(comparison, color = comparisonColor,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                if (chronologicalAge > 0) "Chronological age: $chronologicalAge years"
                else "Weekly fitness estimate based on your recorded metrics",
                color = A.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Fitness Age is not available yet", color = A.white,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Open details to check which recordings are still needed.",
                color = A.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("AGE SCALE 20–80", color = A.muted, fontSize = 10.sp,
                letterSpacing = 1.sp)
            Text(readingDay?.let { "Recorded $it" } ?: "Awaiting weekly result",
                color = A.muted, fontSize = 10.sp)
        }
        Text("Weekly estimate · ±5-year uncertainty · View factors and trends",
            color = A.cyan, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
    }
}


/**
 * Shared age-factor tile: accurate, fixed-value fill with an ambient repeating glow.
 * Missing metrics stay unavailable. Motion stops under the system Reduce Motion setting.
 */
@Composable
private fun AlkeFitnessFactorCard(
    modifier: Modifier,
    index: Int,
    label: String,
    value: String,
    caption: String,
    fraction: Float?,
    tint: Color,
    onClick: () -> Unit,
) {
    val reduced = rememberReduceMotion()
    var target by remember(label, fraction, reduced) {
        mutableStateOf(if (reduced) (fraction ?: 0f).coerceIn(0f, 1f) else 0f)
    }
    LaunchedEffect(fraction, reduced) { target = (fraction ?: 0f).coerceIn(0f, 1f) }
    val fill by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) tween(0)
        else tween(durationMillis = 780, delayMillis = index * 90, easing = FastOutSlowInEasing),
        label = "alke_factor_${index}_fill",
    )
    val shimmer = if (reduced) 0f else {
        val motion = rememberInfiniteTransition(label = "alke_factor_${index}_ambient")
        motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 2_900 + index * 270, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alke_factor_${index}_glow",
        ).value
    }
    val sweep = if (reduced) 0f else {
        val motion = rememberInfiniteTransition(label = "alke_factor_${index}_sweep_transition")
        motion.animateFloat(
            initialValue = -0.20f,
            targetValue = 1.20f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 1_650 + index * 240, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "alke_factor_${index}_traveling_light",
        ).value
    }
    AlkePanel(modifier.staggeredAppear(index), onClick = onClick) {
        Text(label, color = tint.copy(alpha = 0.75f + shimmer * 0.25f), fontWeight = FontWeight.Bold, fontSize = 10.sp,
            letterSpacing = 0.8.sp, maxLines = 1)
        Text(value, color = A.white, fontWeight = FontWeight.Bold,
            fontSize = if (value.length > 10) 16.sp else 24.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
            .background(A.stroke.copy(alpha = 0.62f))) {
            if (fraction != null && fill > 0f) {
                Box(Modifier.fillMaxWidth(fill).fillMaxSize()
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.68f + 0.28f * shimmer))) {
                    if (!reduced) {
                        Canvas(Modifier.fillMaxSize()) {
                            val highlight = Offset(size.width * sweep, size.height / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(A.white.copy(alpha = 0.92f), tint.copy(alpha = 0.25f),
                                        Color.Transparent),
                                    center = highlight, radius = 16.dp.toPx(),
                                ),
                                radius = 16.dp.toPx(), center = highlight,
                            )
                        }
                    }
                }
            } else if (!reduced) {
                // A dim traveling indicator means the card is alive, not that data exists.
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(tint.copy(alpha = 0.30f), radius = 3.dp.toPx(),
                        center = Offset(size.width * sweep, size.height / 2f))
                }
            }
        }
        Text(caption, color = A.muted, fontSize = 10.sp, lineHeight = 14.sp,
            minLines = 2)
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
    onFitnessAge: () -> Unit,
    onVo2: () -> Unit,
    onRestingHr: () -> Unit,
    onTrain: () -> Unit,
    onTrends: () -> Unit,
    onSteps: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    // Use the same persisted weekly Fitness Age metric as Health and its detail view.
    // React to merged days after sync/import; never create a new scoring algorithm.
    val fitnessAgeReading by produceState<Triple<Double, String, String>?>(
        initialValue = null, key1 = vm.activeStrapId, key2 = days,
    ) {
        val row = runCatching {
            vm.repo.latestMetricComputedUnion(vm.activeStrapId, "fitness_age")
        }.getOrNull()
        value = row?.value?.takeIf { it.isFinite() }?.let {
            Triple(it, row.day, row.deviceId)
        }
    }
    // Reuse the longitudinal Theil-Sen Fitness Age pace; never infer speed from one week.
    val agePace by produceState(
        initialValue = FitnessAgePaceEngine.calculate(emptyList()),
        key1 = vm.activeStrapId, key2 = days,
    ) {
        value = runCatching {
            FitnessAgePaceEngine.calculate(
                vm.repo.metricSeriesComputedUnion(
                    vm.activeStrapId, "fitness_age", "0000-01-01", "9999-12-31",
                ).map { FitnessAgePaceEngine.Point(it.day, it.value) },
            )
        }.getOrDefault(FitnessAgePaceEngine.calculate(emptyList()))
    }
    // Show exactly the metabolic factor associated with this stored weekly score.
    // The existing provenance gate filters out stale AI or unrelated lab samples.
    val metabolicFactor by produceState<Pair<Double, Boolean>?>(
        initialValue = null, key1 = fitnessAgeReading, key2 = days,
    ) {
        value = null
        val score = fitnessAgeReading
        if (score != null) {
            value = runCatching {
                vm.repo.withMetabolicCalculationTransaction {
                    val scoreTrace = vm.repo.scoreInputDetails(score.third, score.second, "fitness_age")
                    val candidate = vm.repo.metricSeries(
                        score.third, "fitness_factor_metabolic_delta", score.second, score.second,
                    ).firstOrNull { it.deviceId == score.third && it.day == score.second }
                    val factorTrace = vm.repo.scoreInputDetails(
                        score.third, score.second, "fitness_factor_metabolic_delta",
                    )
                    val valid = com.otidotid.alke.analytics.currentFitnessAgeMetabolicFactor(
                        score.third, score.second, scoreTrace, candidate, factorTrace,
                    )
                    valid?.value?.takeIf { it.isFinite() }?.let {
                        it to hasAiMetabolicProvenance(scoreTrace, score.third, score.second)
                    }
                }
            }.getOrNull()
        }
    }
    val context = LocalContext.current
    val chronologicalAge = remember(context) { ProfileStore.from(context.applicationContext).age }
    val vo2 by produceState<Double?>(initialValue = null, key1 = vm.activeStrapId, key2 = days) {
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
        // Hero comes first, not a menu row. The age ring and number breathe continuously and open
        // the original Fitness Age analysis (history, factors and methodology).
        AlkeFitnessAgeHero(
            fitnessAge = fitnessAgeReading?.first,
            readingDay = fitnessAgeReading?.second,
            chronologicalAge = chronologicalAge,
            agePace = agePace,
            onClick = onFitnessAge,
        )
        AlkeSectionTitle("Fitness Factors")
        val rhr = days.takeLast(7).mapNotNull { it.restingHr?.toDouble() }
            .average().takeIf { it.isFinite() }
        val activity = days.takeLast(7).mapNotNull { it.strain }
            .average().takeIf { it.isFinite() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlkeFitnessFactorCard(
                modifier = Modifier.weight(1f), index = 1,
                label = "VO₂ MAX", value = vo2?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                caption = "ml/kg/min · estimated",
                fraction = vo2?.let { ((it - 15.0) / 55.0).toFloat().coerceIn(0f, 1f) },
                tint = A.cyan, onClick = onVo2,
            )
            AlkeFitnessFactorCard(
                modifier = Modifier.weight(1f), index = 2,
                label = "RESTING HR", value = rhr?.let { "${it.roundToInt()} bpm" } ?: "—",
                caption = "Recent 7-day average",
                fraction = rhr?.let { (1f - (it.toFloat() - 40f) / 40f).coerceIn(0f, 1f) },
                tint = A.blue, onClick = onRestingHr,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlkeFitnessFactorCard(
                modifier = Modifier.weight(1f), index = 3,
                label = "WEEKLY ACTIVITY",
                value = activity?.let { String.format(Locale.US, "%.0f / 100", it) } ?: "—",
                caption = "7-day effort overview",
                fraction = activity?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
                tint = A.green, onClick = onTrain,
            )
            AlkeFitnessFactorCard(
                modifier = Modifier.weight(1f), index = 4,
                label = "METABOLIC",
                value = metabolicFactor?.first?.let { String.format(Locale.US, "%+.1f yr", it) }
                    ?: "Not available",
                caption = when {
                    metabolicFactor?.second == true -> "AI · experimental"
                    metabolicFactor != null -> "Weekly age adjustment"
                    else -> "No eligible inputs yet"
                },
                fraction = null,
                tint = A.gold, onClick = onFitnessAge,
            )
        }
        Text(
            "Metabolic contributes to Fitness Age only when eligible weekly inputs are available.",
            color = A.muted, fontSize = 11.sp, lineHeight = 17.sp,
            modifier = Modifier.staggeredAppear(5),
        )
        AlkePanel(Modifier.fillMaxWidth().staggeredAppear(7), onClick = onSteps) {
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
        // Detailed recovery bars are opt-in. Fitness Age and its four recorded
        // contributors remain the always-visible primary Progress experience.
        AlkeExpandableSection("Recovery history", "7-day trend and full recorded trends") {
            AlkeSectionTitle("Recovery trend", "Full trends", onTrends)
            AlkeMiniBars(days.takeLast(7).map { it.day.takeLast(5) to it.recovery }, A.cyan)
        }
        if (day == null) Text("Your progress screen will populate as measurements arrive.", color = A.muted, fontSize = 12.sp)
    }
}

@Composable
internal fun AlkeInsightsReadinessHero(recovery: Double?) {
    // IMPORTANT: Insights and Today intentionally share the EXACT same living
    // Body Readiness dial. A second renderer drifted and had barely visible motion.
    // The only data source remains the recorded Recovery reading.
    val valid = recovery?.takeIf { it.isFinite() && it >= 0.0 && it <= 100.0 }
    AlkePanel(Modifier.fillMaxWidth()) {
        Text("BODY READINESS", color = A.cyan,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            AlkeGauge(
                label = "Recovery",
                value = valid,
                fraction = ((valid ?: 0.0) / 100.0).toFloat(),
                diameter = 222.dp,
                suffix = "%",
                animateContinuously = true,
            )
        }
        Text(
            "Readiness from your recorded Recovery score. The animation is visual only; no extra score is calculated.",
            color = A.muted, fontSize = 11.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
fun AlkeInsightExperience(
    vm: AppViewModel,
    onNotifications: () -> Unit,
    onDevices: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
    onIntelligence: () -> Unit,
    onHealth: () -> Unit,
    onSkinTemp: () -> Unit,
) {
    val day by vm.today.collectAsStateWithLifecycle()
    val recovery = day?.recovery
    val absoluteSkinC = day?.skinTempC?.takeIf { it.isFinite() }
        ?: day?.skinTempDevC?.takeIf {
            it.isFinite() && com.otidotid.alke.analytics.VitalBands.isAbsoluteSkinTemp(it)
        }
    val deviationSkinC = day?.skinTempDevC?.takeIf {
        it.isFinite() && !com.otidotid.alke.analytics.VitalBands.isAbsoluteSkinTemp(it)
    }
    val skinValue = when {
        absoluteSkinC != null -> String.format(Locale.getDefault(), "%.1f °C", absoluteSkinC)
        deviationSkinC != null -> String.format(Locale.getDefault(), "%+.1f °C Δ", deviationSkinC)
        else -> "—"
    }
    // Only data rendered by THIS Insights page may enter the AI evidence envelope.
    val evidence = AlkeInsightsEvidence(
        dayKey = day?.day,
        recovery = recovery,
        hrvMs = day?.avgHrv,
        restingHrBpm = day?.restingHr,
        skinAbsoluteC = absoluteSkinC,
        skinDeviationC = deviationSkinC,
    )
    val context = LocalContext.current
    val aiSummary by produceState<String?>(initialValue = null, key1 = evidence) {
        value = AlkeInsightsAi.generate(context, evidence)
    }
    AlkePage {
        AlkeHeading("Insight", "Your data, made meaningful.")
        AlkeInsightsReadinessHero(recovery)
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
        // Skin temperature belongs with physiology/insight signals, directly
        // below Resting HR. Original Health and full history continue unchanged.
        AlkePanel(Modifier.fillMaxWidth(), onClick = onSkinTemp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(A.cyan))
                Spacer(Modifier.width(10.dp))
                Text("Skin Temperature", color = A.muted,
                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(skinValue, color = A.white, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = "Skin temperature history",
                    tint = A.cyan, modifier = Modifier.size(18.dp))
            }
        }
        AlkeLinkRow("Intelligence details", "How Charge, Effort and Rest are calculated", Icons.Filled.Insights, onIntelligence)
        AlkeLinkRow("Explore Evidence", "Read the underlying health metrics", Icons.Filled.MonitorHeart, onHealth)
        AlkePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape)
                    .background(A.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI",
                        tint = A.gold, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("YOUR INSIGHT SUMMARY", color = A.gold,
                        fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text("Your Insight Summary", color = A.white,
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
            }
            Text(
                aiSummary ?: when {
                    !evidence.hasEvidence() -> "No Insights evidence recorded yet. No conclusions are generated."
                    !com.otidotid.alke.ai.AiKeyStore.readConsent(context) ||
                        !AlkePrefs.coachEnabled(context) || !AlkePrefs.coachSignals(context) ->
                        "AI summary requires your enabled AI and health-evidence consent in Me settings."
                    else -> "Insights-only AI summary unavailable. Your recorded evidence remains visible above."
                },
                color = A.white, fontSize = 14.sp, lineHeight = 21.sp,
            )
            Text("Based only on the evidence displayed in Insights · AI interpretation, not a score",
                color = A.muted, fontSize = 11.sp)
        }
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
            dayCycleMode = AlkePrefs.dayCycleMode(context),
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
                    Text(if (group.header == "Settings") "Others" else group.header,
                        color = A.white, fontSize = 16.sp,
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
    onDevices: () -> Unit,
    onModules: () -> Unit,
    onSources: () -> Unit,
    onBackupRestore: () -> Unit,
    onOpenTestCentre: () -> Unit,
    onOpenSelfHostedPush: () -> Unit,
) {
    val context = LocalContext.current
    var profileRevision by remember { mutableIntStateOf(0) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && ProfileAvatarStore.setAvatarFromUri(context, uri)) profileRevision++
    }
    var showDockEditor by remember { mutableStateOf(false) }
    var personalSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var moreToolsExpanded by rememberSaveable { mutableStateOf(false) }

    // Me is the only home of settings. Reuse the COMPLETE existing SettingsScreen
    // body rather than duplicating hundreds of controls or reimplementing settings
    // callbacks. SettingsScreen provides one scrollable scaffold for the full page.
    SettingsScreen(
        vm = vm,
        onOpenTestCentre = onOpenTestCentre,
        onOpenBackupSync = onBackupRestore,
        onOpenSelfHostedPush = onOpenSelfHostedPush,
        showSettingsBody = personalSettingsExpanded,
        meHeader = {
            profileRevision
            AlkeSectionTitle("Devices & Sources")
            AlkeLinkRow("Devices", "Connect and manage wearable sensors", Icons.Filled.Sensors, onDevices)
            AlkeLinkRow("Data Sources", "Imports, Health Connect and provenance", Icons.Filled.Storage, onSources)

            AlkeSectionTitle("Data & Backup")
            AlkeLinkRow("Google Drive / Local Folder", "Backup, restore and verified archived history", Icons.Filled.Storage,
                onBackupRestore, A.blue)

            AlkeSectionTitle("App")
            AlkeLinkRow("Customize Dock", "Choose and reorder your quick navigation", Icons.Filled.Tune,
                { showDockEditor = true }, A.cyan)
            AlkeMeDisclosureHeader(
                title = "More Tools",
                subtitle = "All modules and notification controls",
                icon = Icons.Filled.Apps,
                expanded = moreToolsExpanded,
                onToggle = { moreToolsExpanded = !moreToolsExpanded },
                accent = A.gold,
            )
            if (moreToolsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AlkeLinkRow("All Modules", "Explore the complete ALKE toolkit", Icons.Filled.Apps, onModules, A.gold)
                    AlkeLinkRow("Notifications", "Updates and notification preferences", Icons.Filled.NotificationsNone, onNotifications)
                }
            }
            AlkeMeDisclosureHeader(
                title = "Settings",
                subtitle = "Profile, display and app preferences",
                icon = Icons.Filled.Settings,
                expanded = personalSettingsExpanded,
                onToggle = { personalSettingsExpanded = !personalSettingsExpanded },
            )
        },
    )
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
