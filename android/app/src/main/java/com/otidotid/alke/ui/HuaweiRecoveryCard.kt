package com.otidotid.alke.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.otidotid.alke.multidevice.huawei.HuaweiRecoveryArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Standalone encrypted health-data archive; never includes binding credentials. */
@Composable
internal fun HuaweiRecoveryCard(enabled: Boolean) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    fun handle(uri: Uri?, restore: Boolean) {
        if (uri == null) { passphrase = ""; return }
        val secret = passphrase.toCharArray()
        passphrase = ""
        busy = true
        result = null
        scope.launch {
            try {
                result = withContext(Dispatchers.IO) {
                    if (restore) {
                        val rows = HuaweiRecoveryArchive.restoreFromUri(context,uri,secret)
                        "Restored $rows missing Huawei records. Existing data kept."
                    } else {
                        HuaweiRecoveryArchive.exportToUri(context,uri,secret)
                        "Encrypted Huawei backup written and verified."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                result = if (restore) "Huawei restore rejected or could not complete."
                    else "Huawei backup failed verification. Do not rely on the output file."
            } finally {
                secret.fill('\u0000')
                busy = false
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        handle(it,false)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        handle(it,true)
    }
    Text("Huawei recovery archive")
    Text("Separate encrypted file for Huawei history only. Choose a recovery passphrase (at least 12 characters). Keep it safe; pairing credentials and WHOOP data are excluded. A restored watch still needs local authentication.")
    OutlinedTextField(value=passphrase,onValueChange={ passphrase=it },singleLine=true,
        enabled=enabled && !busy,label={ Text("Recovery passphrase") },
        visualTransformation=PasswordVisualTransformation())
    TextButton(enabled=enabled && !busy && passphrase.length>=12,onClick={
        save.launch("alke-huawei-recovery.alkeenc")
    }) { Text("Export encrypted Huawei history") }
    TextButton(enabled=enabled && !busy && passphrase.length>=12,onClick={
        open.launch(arrayOf("*/*"))
    }) { Text("Restore missing Huawei history") }
    if (busy) Text("Processing Huawei archive…")
    result?.let { Text(it) }
}
