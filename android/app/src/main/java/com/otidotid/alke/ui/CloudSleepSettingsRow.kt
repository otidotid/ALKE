package com.otidotid.alke.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otidotid.alke.AlkeApplication
import com.otidotid.alke.ai.CloudActivitySettings
import com.otidotid.alke.ai.CloudSleepSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CloudSleepSettingsRow() {
    val app = LocalContext.current.applicationContext as? AlkeApplication ?: return
    val device = app.activeDeviceId
    val revision by CloudSleepSettings.revision.collectAsStateWithLifecycle()
    val enabled = remember(device, revision) { CloudSleepSettings.token(app, device) != null }
    var editing by remember(device) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { editing = true }.padding(12.dp)) {
        Text("Sleep Intelligence", style = AlkeType.subhead, color = Palette.textPrimary)
        Text(if (enabled) CloudSleepSettings.status(app) else "Set up cloud sleep detection",
            style = AlkeType.footnote, color = Palette.textSecondary)
    }
    if (!editing) return
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    val activityToken = remember { CloudActivitySettings.token(app) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) editing = false },
        title = { Text("Sleep Intelligence") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ALKE sends five-minute heart rate, movement and available HRV summaries to your Cloudflare Worker and Groq. Completed main sleep, day sleep and naps are estimated automatically. You can edit them in Sleep.")
            if (enabled) Text(CloudSleepSettings.status(app))
            else if (activityToken == null) OutlinedTextField(value = token, onValueChange = { token = it },
                singleLine = true, label = { Text("Worker access token") }, visualTransformation = PasswordVisualTransformation())
            if (error) Text("Could not save these settings.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy && (enabled || activityToken != null ||
                (token.trim().length >= 32 && token.trim().none(Char::isWhitespace))), onClick = {
                busy = true
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching { CloudSleepSettings.configure(app, device,
                            if (enabled) null else activityToken ?: token.trim()) }.isSuccess
                    }
                    busy = false
                    if (saved) { token = ""; editing = false } else error = true
                }
            }) { Text(if (enabled) "Turn off" else "Allow sleep analysis") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editing = false }) { Text("Cancel") } })
}
