package com.otidotid.alke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otidotid.alke.ai.ActivitySensitivity
import com.otidotid.alke.ai.CloudActivitySettings
import com.otidotid.alke.ai.CloudActivityProtocol
import com.otidotid.alke.data.WorkoutRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val cloudCyan = Color(0xFF26DEEB)
private val cloudWhite = Color(0xFFF5FCFF)
private val cloudMuted = Color(0xFF9BB9C5)

@Composable internal fun CloudActivitySetupRow() {
    val context = LocalContext.current
    val rev by CloudActivitySettings.revision.collectAsStateWithLifecycle()
    val enabled = remember(rev) { CloudActivitySettings.enabled(context) }
    var show by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Activity Intelligence",color=Palette.textPrimary,style=AlkeType.headline)
            Text(if (enabled) CloudActivitySettings.status(context) else "Cloud-only automatic detection",
                color=Palette.textSecondary,style=AlkeType.caption)
        }
        TextButton(onClick={show=true}) { Text(if (enabled) "Manage" else "Connect",color=cloudCyan) }
    }
    if (show) CloudActivitySetupDialog { show=false }
}

@Composable private fun CloudActivitySetupDialog(onDismiss: () -> Unit) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val configured = remember { CloudActivitySettings.enabled(c) }
    var replaceToken by remember { mutableStateOf(!configured) }
    var sensitivity by remember { mutableStateOf(CloudActivitySettings.sensitivity(c)) }
    AlertDialog(onDismissRequest={ if(!busy) onDismiss() },title={Text("Activity Intelligence")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("ALKE sends compact heart-rate and motion summaries, estimated steps when available, and known activity time ranges to your Cloudflare Worker and Groq. No AI model runs on this phone. Results are logged automatically; no approval per activity.")
                Text("Detection Sensitivity",fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    ActivitySensitivity.entries.forEach { mode ->
                        FilterChip(selected=sensitivity==mode,onClick={sensitivity=mode},
                            label={Text(mode.label)},modifier=Modifier.weight(1f),enabled=!busy)
                    }
                }
                Text(sensitivity.description,style=MaterialTheme.typography.bodySmall)
                Text("Analysis every 15 minutes. High confidence and sensor evidence required in every mode.",
                    style=MaterialTheme.typography.bodySmall)
                if(configured && !replaceToken) {
                    Text("Connected · access token stored securely")
                    TextButton(enabled=!busy,onClick={replaceToken=true}) {Text("Change access token")}
                }
                if(replaceToken) {
                    OutlinedTextField(value=token,onValueChange={token=it},singleLine=true,
                        label={Text("Activity access token")},visualTransformation=PasswordVisualTransformation())
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=consent,onCheckedChange={consent=it})
                        Text("I allow cloud activity analysis",modifier=Modifier.weight(1f))
                    }
                }
                if(error) Text("Could not save securely. Please try again.",color=MaterialTheme.colorScheme.error)
                if(configured) TextButton(enabled=!busy,onClick={
                    busy=true
                    scope.launch {
                        val ok=withContext(Dispatchers.IO){runCatching{CloudActivitySettings.disable(c)}.isSuccess}
                        busy=false
                        if(ok) onDismiss() else error=true
                    }
                }) {Text("Turn off cloud detection")}
            }
        },confirmButton={
            TextButton(enabled=!busy && ((configured && !replaceToken) || (consent && token.trim().length>=32 && token.trim().none{it.isWhitespace()})),onClick={
                busy=true
                scope.launch {
                    val ok=withContext(Dispatchers.IO){runCatching{
                        CloudActivitySettings.setSensitivity(c,sensitivity)
                        if(replaceToken) CloudActivitySettings.configure(c,token)
                    }.isSuccess}
                    token="";busy=false
                    if(ok) onDismiss() else error=true
                }
            }) {Text(if(configured) "Save" else "Connect")}
        },dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("Cancel")}})
}

@Composable internal fun CloudActivityIntelligencePanel(vm: AppViewModel) {
    val c=LocalContext.current
    val rev by CloudActivitySettings.revision.collectAsStateWithLifecycle()
    val dataRev by CloudActivitySettings.dataRevision.collectAsStateWithLifecycle()
    val sessions by vm.workouts.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val enabled=remember(rev){CloudActivitySettings.enabled(c)}
    var show by remember {mutableStateOf(false)}
    var daily by remember {mutableStateOf(emptyList<WorkoutRow>())}
    val scope=rememberCoroutineScope()
    val zone=ZoneId.systemDefault()
    val day=java.time.LocalDate.now(zone)
    val start=day.atStartOfDay(zone).toEpochSecond()
    val end=day.plusDays(1).atStartOfDay(zone).toEpochSecond()
    LaunchedEffect(dataRev,live.lastSyncAt,sessions,day){
        daily=withContext(Dispatchers.IO){vm.repo.cloudDailyActivityRows(start,end)}
    }
    val workouts=sessions.filter{it.startTs<end && it.endTs>start}
    val rows=(workouts+daily).sortedByDescending{it.startTs}
    val fmt=remember {DateTimeFormatter.ofPattern("HH:mm")}
    val unique=CloudActivityProtocol.mergeSpans(rows.map{it.startTs to it.endTs},start,end)
    val mins=(unique.sumOf{it.second-it.first}/60L)
    AlkePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Filled.AutoAwesome,null,tint=cloudCyan,modifier=Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Activity Intelligence",color=cloudWhite,fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            TextButton(onClick={show=true}){Text(if(enabled)"Manage" else "Connect",color=cloudCyan)}
        }
        Text(if(enabled) CloudActivitySettings.status(c) else "Cloud AI · automatic activity detection",color=cloudMuted,fontSize=12.sp)
        if(enabled) Text("Detection sensitivity · "+CloudActivitySettings.sensitivity(c).label,color=cloudCyan,fontSize=12.sp)
        Row(Modifier.fillMaxWidth().background(Color(0xFF0A2031),RoundedCornerShape(16.dp)).padding(12.dp),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Column {Text(rows.size.toString(),color=cloudCyan,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Activities",color=cloudMuted,fontSize=11.sp)}
            Column {Text("$mins min",color=cloudCyan,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Active time",color=cloudMuted,fontSize=11.sp)}
            Column {Text(workouts.size.toString(),color=cloudCyan,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Workouts",color=cloudMuted,fontSize=11.sp)}
        }
        Text("Today's activities",color=cloudWhite,fontWeight=FontWeight.SemiBold)
        if(rows.isEmpty()) Text("No recorded activities yet.",color=cloudMuted,fontSize=13.sp)
        rows.forEach {row ->
            val visual=hrEventVisual(row.sport)
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(42.dp).background(Color(0xFF153547),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){
                    Icon(visual.icon,null,tint=visual.color,modifier=Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)){
                    Text(WorkoutEditing.displaySport(row.sport),color=cloudWhite,fontWeight=FontWeight.Medium)
                    Text(Instant.ofEpochSecond(row.startTs).atZone(zone).format(fmt)+" – "+Instant.ofEpochSecond(row.endTs).atZone(zone).format(fmt),color=cloudMuted,fontSize=11.sp)
                    Text(when(row.source){CloudActivityProtocol.WORKOUT_SOURCE,CloudActivityProtocol.DAILY_SOURCE->"AI Detected";"health-connect"->"Health Connect";else->"Recorded"},color=cloudCyan,fontSize=11.sp)
                }
                Text(((row.endTs-row.startTs)/60.0).roundToInt().toString()+" min",color=cloudWhite,fontSize=13.sp)
                if(row.source==CloudActivityProtocol.DAILY_SOURCE) IconButton(onClick={
                    scope.launch(Dispatchers.IO){vm.repo.dismissCloudDailyActivity(row)}
                }) {Icon(Icons.Filled.Close,"Remove detected activity",tint=cloudMuted)}
            }
        }
        Text("Automatically detected. Intelligently reconciled.",color=cloudMuted,fontSize=11.sp)
        Text("Activity labels are estimates. Stairs require elevation evidence; missing sensor data stays unknown.",color=cloudMuted,fontSize=11.sp)
    }
    if(show) CloudActivitySetupDialog {show=false}
}
