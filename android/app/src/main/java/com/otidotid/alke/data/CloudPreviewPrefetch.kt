package com.otidotid.alke.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import java.time.LocalDate
import java.time.YearMonth

/**
 * Strict, best-effort foreground prefetch. Never accesses the recovery secret:
 * prefetch downloads encrypted bytes only, and never starts a WorkManager job.
 */
internal object CloudPreviewPrefetch {
    fun neighbor(record: GoogleDriveArchiveRecord, day: LocalDate): CloudDayPreviewPart? {
        // One adjacent week ONLY, inside the same month. A preview-part
        // checksum must match the current monthly parent via the index.
        val month = YearMonth.from(day)
        val next = day.plusDays(7)
        val previous = day.minusDays(7)
        return listOf(next, previous).firstNotNullOfOrNull {
            if (YearMonth.from(it) == month) CloudDayPreviewIndex.find(record, it)
            else null
        }?.takeIf { CloudDayPreviewIndex.find(record, day)?.sha256 != it.sha256 }
    }

    /**
     * Explicit choice of transport. Do NOT use metered alone to classify Wi-Fi:
     * hotspots/tethering may be Wi-Fi TRANSPORT_WIFI but marked metered.
     * Battery/Data Saver still suppress optional prefetch on every mode.
     */
    fun eligible(
        mode: PrefetchConnectionMode,
        validated: Boolean,
        wifi: Boolean,
        cellular: Boolean,
        unmetered: Boolean,
        batterySaver: Boolean,
        dataSaver: Boolean,
    ): Boolean {
        if (!validated || batterySaver || dataSaver) return false
        return when (mode) {
            PrefetchConnectionMode.UNMETERED_WIFI -> wifi && unmetered
            PrefetchConnectionMode.ALL_WIFI -> wifi
            PrefetchConnectionMode.WIFI_AND_MOBILE -> wifi || cellular
        }
    }

    fun eligibleOnDevice(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return@runCatching false
        val active = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(active) ?: return@runCatching false
        val pm = context.getSystemService(Context.POWER_SERVICE)
            as? PowerManager ?: return@runCatching false
        eligible(
            mode = PrefetchConnectionPreferences(context).read(),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            batterySaver = pm.isPowerSaveMode,
            dataSaver = cm.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
        )
    }.getOrDefault(false)
}
