package com.otidotid.alke.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.otidotid.alke.data.WorkoutRow
import com.otidotid.alke.ingest.StravaWorkoutExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit owner action. No network upload, token or permission request from ALKE. */
@Composable
internal fun StravaExportAction(vm: AppViewModel,row: WorkoutRow) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var pending by remember(row) { mutableStateOf<StravaWorkoutExport.Prepared?>(null) }
    var busy by remember(row) { mutableStateOf(false) }
    var status by remember(row) { mutableStateOf<String?>(null) }
    var saved by remember(row) { mutableStateOf(false) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file=pending
        pending=null
        if(uri==null) { busy=false;status="Export cancelled." }
        else if(file==null) { busy=false;status="Export interrupted. Please retry." }
        else scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(file.bytes.size<=25_000_000)
                    val output=requireNotNull(context.contentResolver.openOutputStream(uri,"wt"))
                    output.use { it.write(file.bytes) }
                }
                saved=true
                status=if(file.omittedUntimedRoute) "FIT saved without GPS: the older route has no original timestamps."
                    else if(file.hasGps) "FIT saved with recorded GPS." else "FIT saved with recorded heart rate."
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { status="Could not save FIT. Please retry." }
            finally { busy=false }
        }
    }
    Column {
        TextButton(enabled=!busy,onClick={
            busy=true;saved=false;status=null
            scope.launch {
                try {
                    val file=StravaWorkoutExport.prepare(context.applicationContext,vm.repo,vm.activeStrapId,row)
                    pending=file
                    save.launch("ALKE-${row.startTs}.fit")
                } catch(e:CancellationException) { busy=false;throw e }
                catch(_:Exception) { busy=false;pending=null;status="No exportable recorded data or export failed. Sync the workout and retry." }
            }
        }) { Text(if(busy) "Preparing export…" else "Export to Strava (FIT)") }
        status?.let { Text(it,style=AlkeType.footnote,color=Palette.textSecondary) }
        if(saved) TextButton(onClick={
            try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.strava.com/upload/select"))) }
            catch(_:Exception) { status="Open strava.com/upload/select in your browser and choose the saved FIT file." }
        }) { Text("Open Strava upload") }
    }
}
