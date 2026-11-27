package com.otidotid.alke.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.otidotid.alke.multidevice.huawei.HuaweiRfcommTransport
import java.util.regex.Pattern

/**
 * Explicit, system-mediated discovery and Bluetooth bonding for the debug probe.
 * Association and OS bonding are NOT the Huawei application-layer handshake.
 * No registry or WHOOP operations. Never automatically resets or forgets a watch.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
@Composable
internal fun HuaweiSystemPairButton(enabled: Boolean, onReport: (String) -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(CompanionDeviceManager::class.java)
    }
    var searching by remember { mutableStateOf(false) }
    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        searching = false
        if (result.resultCode != Activity.RESULT_OK) {
            onReport("Device selection cancelled.")
        } else {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= 33) {
                    result.data?.getParcelableExtra(
                        CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                }
            try {
                when {
                    device == null -> onReport("No Bluetooth device returned by Android. Use Find paired GT 5 to refresh.")
                    !HuaweiRfcommTransport.isGt5Name(device.name) ->
                        onReport("The selected device is not identified as a GT 5.")
                    device.bondState == BluetoothDevice.BOND_BONDED ->
                        onReport("Android Bluetooth bond exists. Select Find paired GT 5, then Probe. Huawei authentication still pending.")
                    device.bondState == BluetoothDevice.BOND_BONDING ->
                        onReport("Confirm Bluetooth pairing on the phone and watch, then select Find paired GT 5.")
                    device.createBond() ->
                        onReport("Bluetooth bonding requested. Confirm on the phone and watch, then select Find paired GT 5. Huawei authentication still pending.")
                    else -> onReport("Android could not start Bluetooth bonding.")
                }
            } catch (_: SecurityException) {
                onReport("Allow Nearby devices permission first.")
            } catch (_: Exception) {
                onReport("Android Bluetooth bonding could not be started.")
            }
        }
    }

    TextButton(enabled = enabled && !searching && manager != null, onClick = {
        val service = manager ?: return@TextButton
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("^HUAWEI WATCH GT 5(?! PRO(?:[- ]|$))(?:[- ].+)?$", Pattern.CASE_INSENSITIVE))
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(false)
            .build()
        searching = true
        try {
            // The compatibility overload works from API 26 and delivers the chooser
            // through onDeviceFound. Android presents all consent UI itself.
            service.associate(request, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    try {
                        chooser.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                    } catch (_: Exception) {
                        searching = false
                        onReport("Android device chooser could not be opened.")
                    }
                }
                override fun onFailure(error: CharSequence?) {
                    searching = false
                    onReport("Android discovery did not find an available GT 5.")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            searching = false
            onReport("Android denied companion-device discovery.")
        } catch (_: Exception) {
            searching = false
            onReport("Android companion-device discovery unavailable.")
        }
    }) { Text(if (searching) "Searching…" else "Search for new GT 5") }
}
