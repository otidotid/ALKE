package com.otidotid.alke.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Opt-in private distribution support, NOT a release asset.
 *
 * The public GitHub APK contains NO personal backup. A private, locally
 * assembled APK MAY include assets/alke-private-backup.noopbak. On a genuinely
 * fresh installation only, its archived history is imported BEFORE the main
 * Android UI or repository is opened. A populated ALKE DB is NEVER replaced
 * automatically. No historical records, tokens or settings are logged.
 *
 * NOTE: a user-owned APK with this asset is SENSITIVE: its private database
 * is extractable from the APK file. Never publish or upload a personalized APK.
 */
object PersonalBackupBootstrap {
    private const val ASSET_NAME = "alke-private-backup.noopbak"

    fun needsInitialRestore(context: Context): Boolean {
        // A prior install (even one with zero rows) is never overwritten.
        if (context.getDatabasePath(WhoopDatabase.DB_NAME).exists()) return false
        return runCatching { context.assets.list("")?.contains(ASSET_NAME) == true }
            .getOrDefault(false)
    }

    suspend fun restoreBundledOnFreshInstall(context: Context): DataBackup.ImportResult =
        withContext(Dispatchers.IO) {
            val application = context.applicationContext
            if (application.getDatabasePath(WhoopDatabase.DB_NAME).exists()) {
                return@withContext DataBackup.ImportResult.Failed(
                    "ALKE already contains a database. Automatic personal restore was safely skipped."
                )
            }
            val stagedArchive = File(application.cacheDir, "alke-private-onboarding.noopbak")
            try {
                application.assets.open(ASSET_NAME).use { input ->
                    stagedArchive.outputStream().buffered().use { output ->
                        input.copyTo(output, bufferSize = 256 * 1024)
                    }
                }
                if (stagedArchive.length() <= 0L) {
                    return@withContext DataBackup.ImportResult.Failed(
                        "Private backup is empty. Your current data was not changed."
                    )
                }
                // Reuse the EXACT Settings / Backup & History restore implementation:
                // header, Room-origin, quick_check, sidecar rollback, food + prefs.
                DataBackup.importFrom(application, Uri.fromFile(stagedArchive))
            } catch (e: IOException) {
                DataBackup.ImportResult.Failed(
                    "Private backup could not be read. Check free storage and try again."
                )
            } finally {
                stagedArchive.delete()
            }
        }
}
