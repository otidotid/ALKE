package com.otidotid.alke.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * One data-source decision for a selected history day. Never a scoring input,
 * backup writer, cloud sync trigger or permission to delete local data.
 */
internal sealed class UnifiedHistoryRead {
    enum class Source { LOCAL_DATABASE, CACHED_DAILY_SNAPSHOT }

    data class Day(
        val daily: DailyMetric,
        val source: Source,
    ) : UnifiedHistoryRead()

    /** Only an intent to open Drive; download, decryption, checksum and DB
     * verification are still performed by GoogleDriveArchiveManager. */
    data class CloudRequired(
        val record: GoogleDriveArchiveRecord,
    ) : UnifiedHistoryRead()

    data class Unavailable(val reason: String) : UnifiedHistoryRead()
}

/**
 * Priority is based on actual AVAILABLE records, not just an age-based tier.
 * Even for a day older than 22 days, existing local data is used directly.
 *
 * The snapshot is an optional, time-bounded presentation cache: never a source
 * of truth for scoring or editing. Cloud is requested only if both local
 * sources lack that day and the verified archive manifest covers it.
 */
internal object UnifiedHistoryRouting {
    fun decide(
        day: LocalDate,
        today: LocalDate,
        local: DailyMetric?,
        snapshot: DailyMetric?,
        archives: List<GoogleDriveArchiveRecord>,
    ): UnifiedHistoryRead {
        if (day.isAfter(today)) {
            return UnifiedHistoryRead.Unavailable("Future dates have no recorded history.")
        }
        // Caller resolves the active device's source-union and guarantees that
        // cached rows were loaded from the SAME source-tagged snapshot.
        if (local != null && local.day == day.toString()) {
            return UnifiedHistoryRead.Day(local, UnifiedHistoryRead.Source.LOCAL_DATABASE)
        }
        if (snapshot != null && snapshot.day == day.toString()) {
            return UnifiedHistoryRead.Day(snapshot, UnifiedHistoryRead.Source.CACHED_DAILY_SNAPSHOT)
        }
        if (HybridHistoryPolicy.tier(day, today) != HybridHistoryPolicy.Tier.ENCRYPTED_CLOUD_ELIGIBLE) {
            return UnifiedHistoryRead.Unavailable(
                "No daily history found on this phone or in a valid local snapshot for $day."
            )
        }
        val month = YearMonth.from(day).toString()
        val record = archives.firstOrNull { it.month == month }
            ?: return UnifiedHistoryRead.Unavailable(
                "No registered archive covers $day yet. Check automatic history sync."
            )
        val coveredThrough = record.coveredThrough?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
                ?: return UnifiedHistoryRead.Unavailable("Archive coverage metadata is invalid.")
        }
        if (coveredThrough != null && day.isAfter(coveredThrough)) {
            return UnifiedHistoryRead.Unavailable(
                "Archive $month is verified only through $coveredThrough. Sync archives again."
            )
        }
        return UnifiedHistoryRead.CloudRequired(record)
    }
}

/**
 * Shared, bounded local-first history lookup. A one-day indexed Room query
 * reuses WhoopRepository's canonical imported/computed/edit merge, and only
 * if the local day is missing do we look at the disposable daily snapshot.
 *
 * This read does NOT request Drive OAuth, persist a passphrase, remove raw
 * history, restore a backup, or modify any scoring engine.
 */
internal class UnifiedHistoryReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun read(
        day: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): UnifiedHistoryRead = withContext(Dispatchers.IO) {
        if (day.isAfter(today)) {
            return@withContext UnifiedHistoryRead.Unavailable(
                "Future dates have no recorded history."
            )
        }
        val db = WhoopDatabase.get(appContext)
        val activeSource = DeviceRegistry(db).activeDeviceId() ?: "my-whoop"
        val local = WhoopRepository(db, appContext).dayMerged(activeSource, day.toString())
        val cached = if (local == null) {
            SmartDailySnapshotStore(appContext.filesDir)
                .read(activeSource)?.firstOrNull { it.day == day.toString() }
        } else null
        val records = if (local == null && cached == null) {
            GoogleDriveArchiveManager(appContext).archives(today)
        } else emptyList()
        UnifiedHistoryRouting.decide(day, today, local, cached, records)
    }
}
