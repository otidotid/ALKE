package com.otidotid.alke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PremiumSurface.card)
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=12.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically) {
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
    content:@Composable (String,(String)->Unit)->Unit) {
    var selected by rememberSaveable(initialMode) { mutableStateOf(initialMode.takeIf { it in modes } ?: modes.first()) }
    val stateHolder=rememberSaveableStateHolder()
    val choose:(String)->Unit={ if(it in modes) selected=it }
    Column(Modifier.fillMaxSize().background(PremiumSurface.canvas)) {
        Column(Modifier.fillMaxWidth().background(PremiumSurface.surface).padding(horizontal=16.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,color=PremiumSurface.white,fontSize=23.sp,fontWeight=FontWeight.Bold)
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
            "Explore" -> TrendsExploreScreen(vm)
            "Compare" -> CompareScreen(vm)
            else -> TrendsScreen(vm)
        }
    }
}

@Composable
internal fun PremiumStressBreathe(vm:AppViewModel,initialMode:String) {
    PremiumModeHost("Stress & Breathe",listOf("Stress","Breathe"),initialMode) { mode,choose ->
        if(mode=="Stress") StressScreen(vm,onBreathe={choose("Breathe")})
        else BreatheScreen(vm,onOpenStress={choose("Stress")})
    }
}

@Composable
internal fun PremiumHeartRateCenter(vm:AppViewModel,initialMode:String,onBack:()->Unit,
    onDevices:()->Unit,onWorkouts:()->Unit) {
    PremiumModeHost("Heart Rate",listOf("Timeline","Live HR","Rhythm"),initialMode) { mode,choose ->
        when(mode) {
            "Live HR" -> LiveScreen(vm,onManageDevices=onDevices)
            "Rhythm" -> RhythmRoute(vm)
            else -> AlkeHeartRateTimelineExperience(vm,onBack=onBack,onLive={choose("Live HR")},onWorkouts=onWorkouts)
        }
    }
}
