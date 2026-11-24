package com.otidotid.alke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otidotid.alke.R
import kotlinx.coroutines.delay

/** Short branded cold launch; skip recreation and explicit widget entry. */
@Composable
internal fun AlkeBrandLaunch(showOnLaunch: Boolean, content: @Composable () -> Unit) {
    var showBrand by remember { mutableStateOf(showOnLaunch) }
    LaunchedEffect(Unit) {
        if (showBrand) {
            delay(900)
            showBrand = false
        }
    }
    if (showBrand) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1324)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandMark(size = 104.dp)
                Text("ALKE", color = Color(0xFFF5FCFF), fontSize = 30.sp,
                    letterSpacing = 7.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.alke_brand_tagline), color = Color(0xFF9BB9C5),
                    fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    } else {
        content()
    }
}
