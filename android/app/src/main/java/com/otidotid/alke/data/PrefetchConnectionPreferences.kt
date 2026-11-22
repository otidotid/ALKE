package com.otidotid.alke.data

import android.content.Context

/**
 * The user, not the OS metered flag alone, chooses which transports may be
 * used for OPTIONAL nearby-week prefetch. Manual Drive reads and automatic
 * full backup scheduling are separately controlled and never changed here.
 *
 * A metered Wi-Fi hotspot is still a Wi-Fi transport, not mobile data.
 */
internal enum class PrefetchConnectionMode(
    val raw: String,
    val label: String,
    val description: String,
) {
    UNMETERED_WIFI(
        "unmetered_wifi",
        "Wi-Fi non-metered only",
        "Only Wi-Fi that Android classifies as unmetered",
    ),
    ALL_WIFI(
        "all_wifi",
        "All Wi-Fi (including tethering)",
        "Allow Wi-Fi even if the hotspot is classified as metered",
    ),
    WIFI_AND_MOBILE(
        "wifi_and_mobile",
        "Wi-Fi + mobile data",
        "Allow both Wi-Fi and cellular, including metered connections",
    );

    companion object {
        fun fromRaw(raw: String?) =
            entries.firstOrNull { it.raw == raw } ?: UNMETERED_WIFI
    }
}

/** Persistent preference with a backwards-compatible, conservative default. */
internal class PrefetchConnectionPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "alke_cloud_history", Context.MODE_PRIVATE,
    )

    fun read(): PrefetchConnectionMode = PrefetchConnectionMode.fromRaw(
        prefs.getString(KEY, null),
    )

    fun write(mode: PrefetchConnectionMode) {
        prefs.edit().putString(KEY, mode.raw).apply()
    }

    companion object {
        private const val KEY = "prefetch_connection_mode"
    }
}
