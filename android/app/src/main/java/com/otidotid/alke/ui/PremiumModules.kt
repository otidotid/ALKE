package com.otidotid.alke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Shared v624-derived surface tokens for consolidated premium modules. */
internal object PremiumSurface {
    val canvas=Color(0xFF091D2B)
    val surface=Color(0xFF0B2433)
    val card=Color(0xFF102D3D)
    val selected=Color(0xFF1B4354)
    val cyan=Color(0xFF26DEEB)
    val muted=Color(0xFF91B8C8)
    val white=Color(0xFFF4FAFF)
}

@Composable
internal fun PremiumLink(title:String,subtitle:String,onClick:()->Unit) {
    PremiumLink(title, subtitle, onClick, icon = null)
}

/** Me shortcuts use a real leading vector; other modules keep their existing layout. */
@Composable
internal fun PremiumLink(title:String,subtitle:String,onClick:()->Unit,icon:ImageVector?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PremiumSurface.card)
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=12.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(if (icon != null) 12.dp else 0.dp)) {
        if (icon != null) {
            Box(Modifier.size(43.dp).clip(CircleShape).background(PremiumSurface.cyan.copy(alpha=0.12f)), contentAlignment=Alignment.Center) {
                Icon(icon, contentDescription=null, tint=PremiumSurface.cyan, modifier=Modifier.size(23.dp))
            }
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,color=PremiumSurface.white,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text(subtitle,color=PremiumSurface.muted,fontSize=11.sp)
        }
        Icon(Icons.Filled.ChevronRight,null,tint=PremiumSurface.cyan,modifier=Modifier.size(20.dp))
    }
}

/** Compose only the visible tool. Saved state is isolated per tool; no parallel sensor charts. */
@Composable
private fun PremiumModeHost(title:String,modes:List<String>,initialMode:String,
    onBack:(()->Unit)?=null,
    requestMode:String?=null, requestId:Long=0L, forcedMode:String?=null,
    content:@Composable (String,(String)->Unit)->Unit) {
    var selected by rememberSaveable(initialMode) { mutableStateOf(initialMode.takeIf { it in modes } ?: modes.first()) }
    val stateHolder=rememberSaveableStateHolder()
    val choose:(String)->Unit={ if(it in modes) selected=it }
    LaunchedEffect(requestId,requestMode) {
        if(requestId!=0L && requestMode!=null) choose(requestMode)
    }
    LaunchedEffect(forcedMode) {
        if(forcedMode!=null) choose(forcedMode)
    }
    Column(Modifier.fillMaxSize().background(PremiumSurface.canvas)) {
        Column(Modifier.fillMaxWidth().background(PremiumSurface.surface).padding(horizontal=16.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(onBack!=null) androidx.compose.material3.TextButton(onClick=onBack) { Text("Back",color=PremiumSurface.cyan) }
                Text(title,color=PremiumSurface.white,fontSize=23.sp,fontWeight=FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().background(PremiumSurface.canvas,RoundedCornerShape(12.dp)).padding(4.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                modes.forEach { mode ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if(selected==mode) PremiumSurface.selected else Color.Transparent)
                        .semantics { this.selected=selected==mode }
                        .clickable(role=Role.Tab){choose(mode)}
                        .padding(vertical=10.dp),contentAlignment=Alignment.Center) {
                        Text(mode,color=if(selected==mode) PremiumSurface.cyan else PremiumSurface.muted,
                            fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            stateHolder.SaveableStateProvider(selected) { content(selected,choose) }
        }
    }
}

@Composable
internal fun PremiumTrendsStudio(vm:AppViewModel,initialMode:String) {
    PremiumModeHost("Trends Studio",listOf("Trends","Explore","Compare"),initialMode) { mode,_ ->
        when(mode) {
            "Explore" -> TrendsExploreScreen(vm,embedded=true)
            "Compare" -> CompareScreen(vm,embedded=true)
            else -> TrendsScreen(vm,embedded=true)
        }
    }
}

@Composable
internal fun PremiumStressBreathe(vm:AppViewModel,initialMode:String) {
    PremiumModeHost("Stress & Breathe",listOf("Stress","Breathe"),initialMode) { mode,choose ->
        if(mode=="Stress") StressScreen(vm,onBreathe={choose("Breathe")},embedded=true)
        else BreatheScreen(vm,onOpenStress={choose("Stress")},embedded=true)
    }
}

@Composable
internal fun PremiumHeartRateCenter(vm:AppViewModel,initialMode:String,onBack:()->Unit,
    onDevices:()->Unit,onWorkouts:()->Unit,onMetric:(String)->Unit={},timelineRequestId:Long=0L) {
    val workoutRequested by vm.presentActiveWorkout.collectAsStateWithLifecycle()
    PremiumModeHost("Heart Rate",listOf("Timeline","Live HR","Rhythm"),initialMode,onBack=onBack,
        requestMode="Timeline",requestId=timelineRequestId,
        forcedMode=if(initialMode=="Live HR" && workoutRequested) "Live HR" else null) { mode,choose ->
        when(mode) {
            "Live HR" -> LiveScreen(vm,onManageDevices=onDevices,embedded=true)
            "Rhythm" -> RhythmRoute(vm)
            else -> AlkeHeartRateTimelineExperience(vm,onBack=onBack,onLive={choose("Live HR")},onWorkouts=onWorkouts,embedded=true,onMetric=onMetric)
        }
    }
}

@Composable
internal fun PremiumReadout(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(value,color=PremiumSurface.white,fontSize=15.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Text(label,color=PremiumSurface.muted,fontSize=11.sp,maxLines=1)
    }
}
@Composable
internal fun PremiumJournalTile(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    active:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if(active) PremiumSurface.selected else PremiumSurface.canvas)
        .clickable(role=Role.Button,onClick=onClick).padding(vertical=12.dp,horizontal=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(icon,null,tint=PremiumSurface.cyan,modifier=Modifier.size(24.dp))
        Text(label,color=PremiumSurface.white,fontSize=12.sp,maxLines=1)
        Text(value,color=PremiumSurface.muted,fontSize=10.sp,maxLines=1)
    }
}
@Composable
internal fun PremiumSparkline(values:List<Double>,color:Color,modifier:Modifier) {
    val points=remember(values){values.filter { it.isFinite() }}
    androidx.compose.foundation.Canvas(modifier) {
        if(points.size>=2) {
            val low=points.minOrNull()?:0.0
            val span=((points.maxOrNull()?:low)-low).coerceAtLeast(0.01)
            fun point(index:Int)=androidx.compose.ui.geometry.Offset(
                size.width*index/(points.size-1),size.height*(0.9f-0.8f*((points[index]-low)/span).toFloat()))
            for(index in 1 until points.size) drawLine(color,point(index-1),point(index),strokeWidth=2.dp.toPx(),
                cap=androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Scope fast BLE state to the leaf, not all of Me. */
@Composable
internal fun PremiumWearableLink(vm:AppViewModel,onClick:()->Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val status=if(live.connected) if(live.bonded) "Connected" else "Pairing" else "Not connected"
    PremiumLink("Wearable devices",status,onClick, icon = Icons.Filled.Sensors)
}
