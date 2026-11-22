package com.otidotid.alke.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.StatFs
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Independently encrypted 7-day bounded, read-only archive PREVIEW segment.
 * A segment contains the exact legacy day-preview lines, not a raw-history
 * replacement. Its parent ciphertext hash prevents stale rolling-month reuse.
 */
data class CloudDayPreviewPart(
    val fromDay: String,
    val throughDay: String,
    val driveFileId: String,
    val sha256: String,
    val sizeBytes: Long,
    val previewLineCount: Int,
    val monthlySha256: String,
)

data class GoogleDriveArchiveRecord(
    val month: String,
    val driveFileId: String,
    val sha256: String,
    val sizeBytes: Long,
    val recordCount: Long,
    val schemaVersion: Int,
    val verifiedAtMs: Long,
    /** False for archives uploaded before passphrase protection was introduced. */
    val encrypted: Boolean = false,
    /** Reference to untouched original Drive object after a manual legacy encryption migration. */
    val legacyDriveFileId: String? = null,
    /** Inclusive rolling cutoff. Null for legacy archives which cover a complete month. */
    val coveredThrough: String? = null,
    /** Missing in old manifests; old monthly archives remain fully readable. */
    val dayPreviewParts: List<CloudDayPreviewPart> = emptyList(),
)

internal class GoogleDriveArchiveManifest(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun read(): List<GoogleDriveArchiveRecord> = runCatching {
        if (!file.isFile) return emptyList()
        val array = JSONArray(file.readText(Charsets.UTF_8))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(GoogleDriveArchiveRecord(
                    month = item.getString("month"),
                    driveFileId = item.getString("driveFileId"),
                    sha256 = item.getString("sha256"),
                    sizeBytes = item.getLong("sizeBytes"),
                    recordCount = item.getLong("recordCount"),
                    schemaVersion = item.getInt("schemaVersion"),
                    verifiedAtMs = item.getLong("verifiedAtMs"),
                    encrypted = item.optBoolean("encrypted", false),
                    legacyDriveFileId = item.optString("legacyDriveFileId").takeIf { it.isNotBlank() },
                    coveredThrough = item.optString("coveredThrough").takeIf { it.isNotBlank() },
                    dayPreviewParts = buildList {
                        val previews = item.optJSONArray("dayPreviewParts") ?: JSONArray()
                        for (partIndex in 0 until previews.length()) {
                            // An optional malformed preview cannot hide a full monthly archive.
                            val part = runCatching {
                                val node = previews.getJSONObject(partIndex)
                                CloudDayPreviewPart(
                                    fromDay = node.getString("fromDay"),
                                    throughDay = node.getString("throughDay"),
                                    driveFileId = node.getString("driveFileId"),
                                    sha256 = node.getString("sha256"),
                                    sizeBytes = node.getLong("sizeBytes"),
                                    previewLineCount = node.getInt("previewLineCount"),
                                    monthlySha256 = node.getString("monthlySha256"),
                                )
                            }.getOrNull()
                            if (part != null) add(part)
                        }
                    },
                ))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized fun put(record: GoogleDriveArchiveRecord) {
        val array = JSONArray()
        (read().filterNot { it.month == record.month } + record).sortedBy { it.month }.forEach { row ->
            array.put(JSONObject()
                .put("month", row.month).put("driveFileId", row.driveFileId)
                .put("sha256", row.sha256).put("sizeBytes", row.sizeBytes)
                .put("recordCount", row.recordCount).put("schemaVersion", row.schemaVersion)
                .put("verifiedAtMs", row.verifiedAtMs)
                .put("encrypted", row.encrypted)
                .put("legacyDriveFileId", row.legacyDriveFileId)
                .put("coveredThrough", row.coveredThrough)
                .put("dayPreviewParts", JSONArray().apply {
                    row.dayPreviewParts.forEach { part ->
                        put(JSONObject().put("fromDay", part.fromDay)
                            .put("throughDay", part.throughDay)
                            .put("driveFileId", part.driveFileId)
                            .put("sha256", part.sha256)
                            .put("sizeBytes", part.sizeBytes)
                            .put("previewLineCount", part.previewLineCount)
                            .put("monthlySha256", part.monthlySha256))
                    }
                }))
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(array.toString(), Charsets.UTF_8)
        if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
    }
    companion object { private const val FILE_NAME = "google-drive-archive-manifest.json" }
}

/** Verified monthly cold storage. No local row is deleted before every required month is verified. */
internal class GoogleDriveArchiveManager(
    context: Context,
    private val driveClient: GoogleDriveBackupClient = GoogleDriveBackupClient(),
) {
    private val appContext = context.applicationContext
    private val manifest = GoogleDriveArchiveManifest(appContext)

    suspend fun createAndVerifyArchives(
        accessToken: String,
        ownerId: String,
        recoveryPassphrase: CharArray,
        today: LocalDate = LocalDate.now(),
    ): List<GoogleDriveArchiveRecord> = withContext(Dispatchers.IO) {
        // The monthly snapshot copies the same live databases used by the daily
        // summary and full backups; only one heavy operation runs per process.
        BackgroundDataWorkGate.withExclusive archive@{
        require(recoveryPassphrase.size >= 12) {
            "A recovery passphrase with at least 12 characters is required before archiving."
        }
        // Rolling day 22 eligibility (not the old 90-day/complete-month wait).
        // A partial current month is re-uploaded when another day becomes eligible.
        // Archive uploads are manual: we NEVER persist the passphrase or OAuth token.
        val lastArchiveMonth = HybridHistoryPolicy.lastArchiveMonth(today)
        val existing = manifest.read().associateBy { it.month }.toMutableMap()
        val oldest = oldestLocalMonth() ?: return@archive emptyList()
        val created = mutableListOf<GoogleDriveArchiveRecord>()
        var month = oldest
        while (!month.isAfter(lastArchiveMonth)) {
            val coveredThrough = HybridHistoryPolicy.coveredThrough(month, today)
            var previous = existing[month.toString()]
            if (previous != null && !previous.encrypted) {
                // Make a NEW authenticated encrypted copy of the old archive, download it,
                // verify it and keep the original Drive object as legacyDriveFileId.
                // This is safe unattended after the user enrolls their recovery passphrase.
                previous = encryptLegacyMonth(accessToken, ownerId, month, recoveryPassphrase)
                existing[month.toString()] = previous
            }
            if (previous == null || !HybridHistoryPolicy.isCoveredThrough(previous.coveredThrough, coveredThrough)) {
                if (previous != null) {
                    // Refuse a rolling refresh with the wrong passphrase: older data must stay readable.
                    val verified = openMonthOnline(accessToken, month, recoveryPassphrase)
                    verified.close()
                    clearTemporaryCache(month)
                }
                buildUploadVerify(month, accessToken, ownerId, recoveryPassphrase, coveredThrough).also {
                    manifest.put(it); existing[it.month] = it; created += it
                }
            }
            month = month.plusMonths(1)
        }
        // A verified manifest entry is required for each eligible month. It is NOT permission
        // to purge rows: all original health and food data remain on-device.
        val required = generateSequence(oldest) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(lastArchiveMonth) }
        check(required.all { candidate ->
            existing[candidate.toString()]?.let {
                HybridHistoryPolicy.isCoveredThrough(it.coveredThrough, HybridHistoryPolicy.coveredThrough(candidate, today))
            } == true
        }) { "One or more rolling monthly archives are not verified." }
        created
        }
    }

    /**
     * Manual, one-month migration: verify legacy, encrypt its original bytes, upload as a NEW
     * Drive object, download/decrypt/open the new object, then atomically update the manifest.
     * Neither the original Drive object nor any local health record is deleted or modified.
     */
    suspend fun encryptLegacyMonth(
        accessToken: String,
        ownerId: String,
        month: YearMonth,
        recoveryPassphrase: CharArray,
    ): GoogleDriveArchiveRecord = withContext(Dispatchers.IO) {
        require(ownerId.isNotBlank() && accessToken.isNotBlank()) { "Google Drive must be connected." }
        require(recoveryPassphrase.size >= 12) { "Use a recovery passphrase of at least 12 characters." }
        val old = manifest.read().singleOrNull { it.month == month.toString() }
            ?: error("No local archive manifest entry for $month.")
        require(!old.encrypted) { "This month is already encrypted." }

        // Verify the *existing* archive before copying it. Always close the read-only handle.
        val verifiedOriginal = openMonthOnline(accessToken, month)
        verifiedOriginal.close()
        clearTemporaryCache(month)

        val work = File(appContext.cacheDir, "drive-migration-work").apply { mkdirs() }
        val original = File(work, "$month.legacy.alkearc")
        val encrypted = File(work, "$month.new.alkeenc")
        val downloaded = File(work, "$month.download.alkeenc")
        val decrypted = File(work, "$month.verify.alkearc")
        val extracted = File(work, "$month.verify.sqlite")
        try {
            listOf(original, encrypted, downloaded, decrypted, extracted).forEach(File::delete)
            driveClient.download(accessToken, old.driveFileId, original)
            check(original.length() == old.sizeBytes) { "Legacy archive length changed." }
            check(driveClient.sha256(original) == old.sha256) { "Legacy archive hash mismatch." }
            ArchiveEncryption.encrypt(original, encrypted, recoveryPassphrase)
            val encryptedHash = driveClient.sha256(encrypted)
            val remote = driveClient.uploadArchive(
                accessToken, encrypted, ownerId, month.toString(),
                old.recordCount, old.schemaVersion, encrypted = true,
            )
            check(remote.sha256 == encryptedHash) { "New archive metadata checksum mismatch." }
            driveClient.download(accessToken, remote.id, downloaded)
            check(downloaded.length() == encrypted.length()) { "Downloaded encrypted archive length mismatch." }
            check(driveClient.sha256(downloaded) == encryptedHash) { "Encrypted archive download hash mismatch." }
            ArchiveEncryption.decrypt(downloaded, decrypted, recoveryPassphrase)
            check(driveClient.sha256(decrypted) == old.sha256) {
                "Decrypted archive differs from the verified original."
            }
            extractDatabase(decrypted, extracted)
            SQLiteDatabase.openDatabase(extracted.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                check(db.version == old.schemaVersion) { "Archive schema changed." }
                check(countRows(db) == old.recordCount) { "Archive row count changed." }
            }
            val migrated = old.copy(
                driveFileId = remote.id,
                sha256 = encryptedHash,
                sizeBytes = encrypted.length(),
                verifiedAtMs = System.currentTimeMillis(),
                encrypted = true,
                legacyDriveFileId = old.driveFileId,
            )
            manifest.put(migrated)
            migrated
        } finally {
            // Only working copies are removed, not the originals on phone or in Drive.
            listOf(original, encrypted, downloaded, decrypted, extracted).forEach(File::delete)
        }
    }

    suspend fun openMonthOnline(
        accessToken: String,
        month: YearMonth,
        recoveryPassphrase: CharArray? = null,
    ): SQLiteDatabase = withContext(Dispatchers.IO) {
        val record = manifest.read().firstOrNull { it.month == month.toString() }
            ?: error("Archived history requires Google Drive and is not available for $month.")
        if (record.encrypted) require(recoveryPassphrase?.isNotEmpty() == true) {
            "Recovery passphrase required to open this encrypted archive."
        }
        val cache = File(appContext.cacheDir, "drive-archive-cache").apply { mkdirs() }
        val blob = File(cache, "${record.month}.${if (record.encrypted) "alkeenc" else "alkearc"}")
        val clearArchive = File(cache, "${record.month}.alkearc")
        val dbFile = File(cache, "${record.month}.sqlite")
        try {
            if (!blob.isFile || driveClient.sha256(blob) != record.sha256) {
                blob.delete()
                driveClient.download(accessToken, record.driveFileId, blob)
            }
            check(blob.length() == record.sizeBytes) { "Archive file size mismatch." }
            check(driveClient.sha256(blob) == record.sha256) { "Archive checksum mismatch." }
            val zipInput = if (record.encrypted) {
                // Never trust a leftover decrypted cache entry from an earlier operation.
                clearArchive.delete()
                ArchiveEncryption.decrypt(blob, clearArchive, recoveryPassphrase!!)
                clearArchive
            } else blob // Pre-encryption archives remain readable, but are labeled as legacy.
            dbFile.delete()
            extractDatabase(zipInput, dbFile)
            val opened = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                check(opened.version == record.schemaVersion) { "Archive schema mismatch." }
                check(countRows(opened) == record.recordCount) { "Archive record count mismatch." }
                // Keep only a small, authenticated ENCRYPTED download cache.
                // A same-month read can reuse its verified blob without another Drive fetch.
                // Cleartext SQLite is always deleted after the preview closes.
                if (record.encrypted) blob.setLastModified(System.currentTimeMillis())
                VerifiedArchiveCache.prune(cache)
                opened
            } catch (failure: Throwable) {
                opened.close()
                throw failure
            }
        } catch (failure: Throwable) {
            // No plaintext or partially extracted SQLite cache survives a failed authentication.
            clearTemporaryCache(month)
            throw failure
        }
    }

    data class ArchivePreview(
        val record: GoogleDriveArchiveRecord,
        val tableCounts: Map<String, Long>,
        val totalRecords: Long,
        /** Read-only, bounded daily detail from the downloaded archive (not a restore). */
        val history: List<String> = emptyList(),
        val selectedDay: LocalDate? = null,
        /** Verified encrypted weekly day-preview; not the full raw monthly archive. */
        val partialDayPreview: Boolean = false,
    )

    suspend fun previewMonthOnline(
        accessToken: String, month: YearMonth, recoveryPassphrase: CharArray? = null,
        selectedDay: LocalDate? = null,
    ): ArchivePreview = withContext(Dispatchers.IO) {
            if (selectedDay != null) {
                require(YearMonth.from(selectedDay) == month) { "Selected day is outside the archive month." }
                require(HybridHistoryPolicy.tier(selectedDay, LocalDate.now()) ==
                    HybridHistoryPolicy.Tier.ENCRYPTED_CLOUD_ELIGIBLE) {
                    "This day is not yet eligible for cloud history."
                }
            }
            val record = manifest.read().firstOrNull { it.month == month.toString() }
                ?: error("No verified archive is registered for $month.")
            if (selectedDay != null && record.coveredThrough != null) {
                require(!selectedDay.isAfter(LocalDate.parse(record.coveredThrough))) {
                    "This date has not been archived yet. Sync rolling archives first."
                }
            }
            val opened = openMonthOnline(accessToken, month, recoveryPassphrase)
            try {
                val counts = linkedMapOf<String, Long>()
                opened.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' " +
                        "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' ORDER BY name",
                    null,
                ).use { tables ->
                    while (tables.moveToNext()) {
                        val table = tables.getString(0)
                        opened.rawQuery("SELECT COUNT(*) FROM ${q(table)}", null).use { count ->
                            if (count.moveToFirst()) counts[table] = count.getLong(0)
                        }
                    }
                }
                ArchivePreview(record, counts, counts.values.sum(),
                    readHistoryPreview(opened, counts.keys, selectedDay), selectedDay)
            } finally {
                opened.close()
                // On success preserve only the checksum-verified encrypted blob.
                // Never retain decrypted .alkearc or temporary SQLite files.
                VerifiedArchiveCache.clearPlaintextForMonth(
                    File(appContext.cacheDir, "drive-archive-cache"), month)
            }
        }

    /** Never selects free-text notes, waveform BLOBs, GPS routes, or Coach conversation text. */
    private fun readHistoryPreview(
        db: SQLiteDatabase, tables: Set<String>, selectedDay: LocalDate? = null,
    ): List<String> = buildList {
        if ("dailyMetric" in tables) {
            db.rawQuery(
                "SELECT day,deviceId,totalSleepMin,avgHrv,recovery,steps FROM dailyMetric " +
                    (if (selectedDay == null) "" else "WHERE day = ? ") +
                    "ORDER BY day DESC LIMIT 50", selectedDay?.let { arrayOf(it.toString()) },
            ).use { c -> while (c.moveToNext()) {
                fun n(i: Int): String = if (c.isNull(i)) "–" else c.getString(i)
                add("${c.getString(0)} · ${c.getString(1)} · Sleep ${n(2)} min · HRV ${n(3)} ms · Recovery ${n(4)} · Steps ${n(5)}")
            } }
        }
        if ("workout" in tables) {
            db.rawQuery(
                "SELECT startTs,sport,source,durationS FROM workout " +
                    (if (selectedDay == null) "" else "WHERE startTs >= ? AND startTs < ? ") +
                    "ORDER BY startTs DESC LIMIT 50",
                selectedDay?.let { dayEpochWindow(it) },
            ).use { c -> while (c.moveToNext()) {
                val day = Instant.ofEpochSecond(c.getLong(0)).atZone(ZoneId.systemDefault()).toLocalDate()
                val duration = if (c.isNull(3)) "?" else (c.getDouble(3) / 60.0).toInt().toString()
                add("$day · Workout: ${c.getString(1)} · ${c.getString(2)} · $duration min")
            } }
        }
        if ("sleepSession" in tables) {
            db.rawQuery("SELECT startTs,endTs,deviceId FROM sleepSession " +
                (if (selectedDay == null) "" else "WHERE startTs >= ? AND startTs < ? ") +
                "ORDER BY startTs DESC LIMIT 50", selectedDay?.let { dayEpochWindow(it) })
                .use { c -> while (c.moveToNext()) {
                    val day = Instant.ofEpochSecond(c.getLong(0)).atZone(ZoneId.systemDefault()).toLocalDate()
                    add("$day · Sleep session · ${c.getString(2)} · ${(c.getLong(1) - c.getLong(0)) / 60} min")
                } }
        }
        if ("metricSeries" in tables) {
            db.rawQuery("SELECT day,deviceId,`key`,value FROM metricSeries " +
                (if (selectedDay == null) "" else "WHERE day = ? ") +
                "ORDER BY day DESC LIMIT 50", selectedDay?.let { arrayOf(it.toString()) })
                .use { c -> while (c.moveToNext()) {
                    add("${c.getString(0)} · ${c.getString(1)} · ${c.getString(2)}: ${c.getDouble(3)}")
                } }
        }
    }

    private fun dayEpochWindow(day: LocalDate): Array<String> = arrayOf(
        day.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toString(),
        day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toString(),
    )

    /**
     * Prefer a tiny independently-encrypted, SHA-256 verified 7-day preview when
     * its index points to the EXACT currently recorded monthly archive.
     * Legacy/no-part/missing/corrupt sidecars always fall back to the existing
     * full monthly reader. Neither route modifies local history.
     */
    suspend fun previewDayOnline(
        accessToken: String, day: LocalDate, passphrase: CharArray? = null,
    ): ArchivePreview = withContext(Dispatchers.IO) {
        val month = YearMonth.from(day)
        val record = manifest.read().firstOrNull { it.month == month.toString() }
        val part = record?.let { CloudDayPreviewIndex.find(it, day) }
        if (record != null && part != null && passphrase?.isNotEmpty() == true &&
            HybridHistoryPolicy.tier(day, LocalDate.now()) ==
                HybridHistoryPolicy.Tier.ENCRYPTED_CLOUD_ELIGIBLE
        ) {
            try {
                return@withContext readVerifiedDayPreview(accessToken, record, part, day, passphrase)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (auth: GoogleDriveAuthenticationException) {
                throw auth
            } catch (_: Exception) {
                // A sidecar never becomes a required path. The independently
                // verified monthly archive remains the recovery/legacy fallback.
            }
        }
        previewMonthOnline(accessToken, month, passphrase, day)
    }

    private suspend fun readVerifiedDayPreview(
        token: String, record: GoogleDriveArchiveRecord,
        part: CloudDayPreviewPart, day: LocalDate, passphrase: CharArray,
    ): ArchivePreview {
        val work = File(appContext.cacheDir, "drive-archive-day-preview").apply { mkdirs() }
        val encrypted = File.createTempFile("week-", ".alkeenc", work)
        val clear = File.createTempFile("week-", ".json", work).apply {
            // ArchiveEncryption.decrypt refuses to overwrite an existing target.
            check(delete())
        }
        try {
            driveClient.download(token, part.driveFileId, encrypted)
            check(encrypted.length() == part.sizeBytes) { "Weekly preview size mismatch." }
            check(driveClient.sha256(encrypted) == part.sha256) { "Weekly preview checksum mismatch." }
            ArchiveEncryption.decrypt(encrypted, clear, passphrase)
            check(clear.length() in 1..(2L * 1024L * 1024L)) { "Weekly preview too large." }
            val doc = JSONObject(clear.readText(Charsets.UTF_8))
            check(doc.getString("format") == "alke-weekly-day-preview-v1")
            check(doc.getString("month") == record.month)
            check(doc.getString("monthlySha256") == record.sha256)
            check(doc.getString("fromDay") == part.fromDay &&
                doc.getString("throughDay") == part.throughDay)
            check(doc.getInt("schemaVersion") == record.schemaVersion)
            val days = doc.getJSONObject("days")
            var rowCount = 0
            var cursor = LocalDate.parse(part.fromDay)
            val end = LocalDate.parse(part.throughDay)
            while (!cursor.isAfter(end)) {
                val lines = days.getJSONArray(cursor.toString())
                check(lines.length() <= 200) { "Weekly day preview row limit exceeded." }
                rowCount += lines.length()
                cursor = cursor.plusDays(1)
            }
            check(rowCount == part.previewLineCount) { "Weekly preview row count mismatch." }
            val selected = days.getJSONArray(day.toString())
            val history = (0 until selected.length()).map { selected.getString(it) }
            return ArchivePreview(record, emptyMap(), record.recordCount, history, day,
                partialDayPreview = true)
        } finally {
            // Both downloaded ciphertext and plaintext are private working
            // files, not persistent raw history. Neither survives the read.
            clear.delete()
            encrypted.delete()
        }
    }

    fun clearTemporaryCache(month: YearMonth? = null) {
        val cache = File(appContext.cacheDir, "drive-archive-cache")
        cache.listFiles()?.filter { file -> month == null || file.name.startsWith(month.toString()) }
            ?.forEach(File::delete)
    }

    fun archives(today: LocalDate = LocalDate.now()): List<GoogleDriveArchiveRecord> =
        manifest.read().filter { record ->
            runCatching {
                YearMonth.parse(record.month) <= lastEligibleArchiveMonth(today)
            }.getOrDefault(false)
        }

    internal fun lastEligibleArchiveMonth(today: LocalDate): YearMonth =
        HybridHistoryPolicy.lastArchiveMonth(today)
    private suspend fun buildUploadVerify(
        month: YearMonth, token: String, ownerId: String, recoveryPassphrase: CharArray,
        coveredThrough: LocalDate,
    ): GoogleDriveArchiveRecord {
        val work = File(appContext.cacheDir, "drive-archive-work").apply { mkdirs() }
        val dbCopy = File(work, "$month.sqlite")
        val archive = File(work, "$month.alkearc")
        val encryptedArchive = File(work, "$month.alkeenc")
        val verifyEncrypted = File(work, "$month.verify.alkeenc")
        val verifyArchive = File(work, "$month.verify.alkearc")
        val verifyDb = File(work, "$month.verify.sqlite")
        try {
            // Do not re-use plaintext or encrypted files left by a prior interrupted attempt.
            listOf(dbCopy, archive, encryptedArchive, verifyEncrypted, verifyArchive, verifyDb)
                .forEach(File::delete)
            checkpointAndCopy(dbCopy)
            filterToMonth(dbCopy, month, coveredThrough.plusDays(1))
            val snapshot = SQLiteDatabase.openDatabase(dbCopy.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val schema: Int
            val count: Long
            try { schema = snapshot.version; count = countRows(snapshot) }
            finally { snapshot.close() }
            zipDatabase(dbCopy, archive, month, count, schema)

            // Encrypt before anything leaves the phone. Drive only receives .alkeenc bytes.
            ArchiveEncryption.encrypt(archive, encryptedArchive, recoveryPassphrase)
            val checksum = driveClient.sha256(encryptedArchive)
            val remote = driveClient.uploadArchive(
                token, encryptedArchive, ownerId, month.toString(), count, schema, encrypted = true,
            )
            check(remote.sha256 == checksum) { "Google Drive archive metadata checksum mismatch." }
            driveClient.download(token, remote.id, verifyEncrypted)
            check(verifyEncrypted.length() == encryptedArchive.length()) { "Google Drive archive size mismatch." }
            check(driveClient.sha256(verifyEncrypted) == checksum) { "Google Drive archive SHA-256 mismatch." }
            ArchiveEncryption.decrypt(verifyEncrypted, verifyArchive, recoveryPassphrase)
            extractDatabase(verifyArchive, verifyDb)
            val opened = SQLiteDatabase.openDatabase(verifyDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                check(opened.version == schema) { "Google Drive archive schema mismatch." }
                check(countRows(opened) == count) { "Google Drive archive record count mismatch." }
            } finally { opened.close() }
            // Optional, small weekly preview files are independently encrypted
            // and verified. The full monthly archive above has ALREADY passed
            // download/decrypt/SQLite checks; failure to create any preview
            // never invalidates that recovery archive.
            val previews = try {
                uploadVerifiedDayPreviews(
                    dbCopy, month, coveredThrough, checksum, schema,
                    token, ownerId, recoveryPassphrase,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            return GoogleDriveArchiveRecord(
                month.toString(), remote.id, checksum, encryptedArchive.length(), count,
                schema, System.currentTimeMillis(), encrypted = true,
                coveredThrough = coveredThrough.toString(),
                dayPreviewParts = previews,
            )
        } finally {
            // App-private cache only. No plaintext is retained after this manual operation.
            listOf(dbCopy, archive, encryptedArchive, verifyEncrypted, verifyArchive, verifyDb)
                .forEach(File::delete)
        }
    }

    /**
     * Export the SAME bounded human-readable day lines as the historic monthly
     * reader, but as independently encrypted weekly JSON sidecars. Unlike a
     * SQLite history copy, these previews do NOT contain raw waveform, GPS,
     * food notes or hidden extra tables. Full monthly originals stay intact.
     */
    private suspend fun uploadVerifiedDayPreviews(
        monthlySnapshot: File,
        month: YearMonth, coveredThrough: LocalDate,
        monthlySha256: String, schemaVersion: Int,
        token: String, ownerId: String, passphrase: CharArray,
    ): List<CloudDayPreviewPart> {
        val source = SQLiteDatabase.openDatabase(
            monthlySnapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = mutableSetOf<String>()
            source.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'", null
            ).use { cursor ->
                while (cursor.moveToNext()) tables.add(cursor.getString(0))
            }
            val results = mutableListOf<CloudDayPreviewPart>()
            val work = File(appContext.cacheDir, "drive-archive-day-preview").apply { mkdirs() }
            for (window in CloudDayPreviewIndex.windows(month, coveredThrough)) {
                val clear = File.createTempFile("weekly-", ".json", work)
                val encrypted = File.createTempFile("weekly-", ".alkeenc", work).apply {
                    // ArchiveEncryption.encrypt publishes only to a new filename.
                    check(delete())
                }
                val verified = File.createTempFile("weekly-", ".verify.alkeenc", work)
                val recovered = File.createTempFile("weekly-", ".verify.json", work).apply {
                    // ArchiveEncryption.decrypt likewise refuses to overwrite.
                    check(delete())
                }
                try {
                    val days = JSONObject()
                    var count = 0
                    var day = window.from
                    while (!day.isAfter(window.through)) {
                        val lines = readHistoryPreview(source, tables, day)
                        count += lines.size
                        days.put(day.toString(), JSONArray().apply { lines.forEach(::put) })
                        day = day.plusDays(1)
                    }
                    val content = JSONObject()
                        .put("format", "alke-weekly-day-preview-v1")
                        .put("month", month.toString())
                        .put("fromDay", window.from.toString())
                        .put("throughDay", window.through.toString())
                        .put("monthlySha256", monthlySha256)
                        .put("schemaVersion", schemaVersion)
                        .put("days", days)
                        .toString()
                    check(content.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) {
                        "Weekly preview exceeded its optional size limit."
                    }
                    clear.writeText(content, Charsets.UTF_8)
                    ArchiveEncryption.encrypt(clear, encrypted, passphrase)
                    val hash = driveClient.sha256(encrypted)
                    val remote = driveClient.uploadDayPreview(
                        token, encrypted, ownerId, window.from.toString(),
                        window.through.toString(), count,
                    )
                    check(remote.sha256 == hash) { "Weekly preview Drive metadata checksum mismatch." }
                    driveClient.download(token, remote.id, verified)
                    check(verified.length() == encrypted.length())
                    check(driveClient.sha256(verified) == hash)
                    ArchiveEncryption.decrypt(verified, recovered, passphrase)
                    check(recovered.readBytes().contentEquals(clear.readBytes())) {
                        "Downloaded encrypted day preview did not roundtrip."
                    }
                    results.add(
                        CloudDayPreviewPart(window.from.toString(), window.through.toString(),
                            remote.id, hash, encrypted.length(), count, monthlySha256)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One optional preview may be missing. The index simply
                    // falls back to the authenticated monthly original.
                } finally {
                    clear.delete()
                    encrypted.delete()
                    verified.delete()
                    recovered.delete()
                }
            }
            return results
        } finally {
            source.close()
        }
    }

    private fun checkpointAndCopy(destination: File) {
        val room = WhoopDatabase.get(appContext)
        val db = room.openHelper.writableDatabase
        // A FULL checkpoint can return BUSY without throwing. Copying the main file after
        // a partial checkpoint could omit WAL records, so verify SQLite's busy result.
        db.query("PRAGMA wal_checkpoint(FULL)").use { c ->
            check(c.moveToFirst() && c.getInt(0) == 0) {
                "Could not checkpoint the active database for archiving. Retry later."
            }
        }
        val source = appContext.getDatabasePath(WhoopDatabase.DB_NAME)
        check(source.isFile)
        check(StatFs(appContext.filesDir.absolutePath).availableBytes >= source.length() * 3L) {
            "Not enough storage to build and verify a ALKE monthly archive."
        }
        destination.delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val escaped = destination.absolutePath.replace("'", "''")
            // VACUUM INTO is guaranteed only with the SQLite generation shipped from API 30.
            db.execSQL("VACUUM INTO '$escaped'")
        } else {
            // API 26-29: FULL checkpoint above makes the main database self-contained. Copy the
            // checkpointed file instead of executing unsupported VACUUM INTO syntax. Filtering and
            // integrity verification still happen on this private copy before upload or pruning.
            // API 26-29 lack VACUUM INTO. Keep Room's writer transaction occupied while
            // copying the checkpointed main file, preventing a concurrent checkpoint/write.
            room.runInTransaction {
                source.copyTo(destination, overwrite = true)
            }
        }
        check(destination.isFile && destination.length() > 0L)
    }

    private fun filterToMonth(file: File, month: YearMonth, endExclusive: LocalDate) {
        val fromDay = month.atDay(1)
        require(!endExclusive.isAfter(month.plusMonths(1).atDay(1))) {
            "Rolling archive cannot include a day newer than its month."
        }
        val toDay = endExclusive
        val fromTs = fromDay.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val toTs = toDay.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        db.beginTransaction()
        try {
            temporalTables(db).forEach { item ->
                val args = when (item.encoding) {
                    TimeEncoding.DAY_TEXT -> arrayOf(fromDay.toString(), toDay.toString())
                    TimeEncoding.EPOCH_SECONDS -> arrayOf(fromTs, toTs)
                    TimeEncoding.EPOCH_MILLIS -> arrayOf(fromTs * 1000L, toTs * 1000L)
                }
                db.execSQL("DELETE FROM ${q(item.table)} WHERE ${q(item.column)} < ? OR ${q(item.column)} >= ?", args)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction(); db.close() }
    }


    private enum class TimeEncoding { DAY_TEXT, EPOCH_SECONDS, EPOCH_MILLIS }
    private data class Temporal(val table: String, val column: String, val encoding: TimeEncoding)

    private fun temporalTables(db: SupportSQLiteDatabase): List<Temporal> =
        discoverTemporalTables(
            tableNames = {
                buildList {
                    db.query(
                        "SELECT name FROM sqlite_master WHERE type='table' " +
                            "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
                    ).use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
            },
            columns = { table ->
                buildSet {
                    db.query("PRAGMA table_info(${q(table)})").use { info ->
                        val index = info.getColumnIndex("name")
                        while (info.moveToNext()) add(info.getString(index))
                    }
                }
            },
            scalarLong = { sql -> db.query(sql).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null } },
        )

    private fun temporalTables(db: SQLiteDatabase): List<Temporal> =
        discoverTemporalTables(
            tableNames = {
                buildList {
                    db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' " +
                            "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
                        null,
                    ).use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
            },
            columns = { table ->
                buildSet {
                    db.rawQuery("PRAGMA table_info(${q(table)})", null).use { info ->
                        val index = info.getColumnIndex("name")
                        while (info.moveToNext()) add(info.getString(index))
                    }
                }
            },
            scalarLong = { sql -> db.rawQuery(sql, null).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null } },
        )

    private fun discoverTemporalTables(
        tableNames: () -> List<String>,
        columns: (String) -> Set<String>,
        scalarLong: (String) -> Long?,
    ): List<Temporal> {
        val nowSeconds = Instant.now().epochSecond
        val minSeconds = LocalDate.of(VALID_MIN_YEAR, 1, 1)
            .atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        return buildList {
            tableNames().forEach { table ->
                val names = columns(table)
                DAY_COLUMNS.firstOrNull(names::contains)?.let { dayColumn ->
                    add(Temporal(table, dayColumn, TimeEncoding.DAY_TEXT))
                    return@forEach
                }
                val timestamp = SECOND_COLUMNS.firstOrNull(names::contains) ?: return@forEach
                val min = scalarLong("SELECT MIN(${q(timestamp)}) FROM ${q(table)} WHERE ${q(timestamp)} > 0")
                    ?: return@forEach
                val encoding = when {
                    min in minSeconds..(nowSeconds + FUTURE_TOLERANCE_SECONDS) -> TimeEncoding.EPOCH_SECONDS
                    min in (minSeconds * 1000L)..((nowSeconds + FUTURE_TOLERANCE_SECONDS) * 1000L) -> TimeEncoding.EPOCH_MILLIS
                    else -> return@forEach
                }
                add(Temporal(table, timestamp, encoding))
            }
        }
    }

    private fun oldestLocalMonth(): YearMonth? {
        // Never call Context.openOrCreateDatabase() or close this handle. Room owns this database
        // lifecycle; closing an independently obtained handle can close Room's shared connection.
        val db = WhoopDatabase.get(appContext).openHelper.readableDatabase
        val now = LocalDate.now()
        return temporalTables(db).mapNotNull { item ->
            when (item.encoding) {
                TimeEncoding.DAY_TEXT -> db.query(
                    "SELECT MIN(${q(item.column)}) FROM ${q(item.table)} " +
                        "WHERE ${q(item.column)} >= '$VALID_MIN_YEAR-01-01' AND ${q(item.column)} <= '${now}'",
                ).use { c ->
                    if (!c.moveToFirst() || c.isNull(0)) null
                    else runCatching { YearMonth.from(LocalDate.parse(c.getString(0))) }.getOrNull()
                }
                TimeEncoding.EPOCH_SECONDS, TimeEncoding.EPOCH_MILLIS -> {
                    val divisor = if (item.encoding == TimeEncoding.EPOCH_MILLIS) 1000L else 1L
                    db.query("SELECT MIN(${q(item.column)}) FROM ${q(item.table)} WHERE ${q(item.column)} > 0").use { c ->
                        if (!c.moveToFirst() || c.isNull(0)) null else {
                            val seconds = c.getLong(0) / divisor
                            runCatching { YearMonth.from(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault())) }
                                .getOrNull()?.takeIf { it.year in VALID_MIN_YEAR..now.year }
                        }
                    }
                }
            }
        }.minOrNull()
    }

    private fun countRows(db: SQLiteDatabase): Long {
        var total = 0L
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'", null).use { tables ->
            while (tables.moveToNext()) db.rawQuery("SELECT COUNT(*) FROM ${q(tables.getString(0))}", null).use { c -> if (c.moveToFirst()) total += c.getLong(0) }
        }
        return total
    }

    private fun zipDatabase(db: File, archive: File, month: YearMonth, count: Long, schema: Int) {
        ZipOutputStream(FileOutputStream(archive).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY)); FileInputStream(db).use { it.copyTo(zip) }; zip.closeEntry()
            zip.putNextEntry(ZipEntry(META_ENTRY))
            zip.write(JSONObject().put("month", month.toString()).put("recordCount", count).put("schemaVersion", schema).toString().toByteArray())
            zip.closeEntry()
        }
    }
    private fun extractDatabase(archive: File, destination: File) = ZipFile(archive).use { zip ->
        val entry = zip.getEntry(DB_ENTRY) ?: error("Archived history database is missing.")
        zip.getInputStream(entry).use { input -> FileOutputStream(destination).use(input::copyTo) }
    }
    private fun q(value: String) = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private const val DB_ENTRY = "alke-archive.sqlite"
        private const val META_ENTRY = "archive-manifest.json"
        private val DAY_COLUMNS = listOf("day", "localDay")
        private val SECOND_COLUMNS = listOf("ts", "startTs", "consumedAt", "createdAt", "updatedAt")
        private const val VALID_MIN_YEAR = 2020
        private const val FUTURE_TOLERANCE_SECONDS = 366L * 24L * 60L * 60L
    }
}
