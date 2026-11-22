package com.otidotid.alke.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent accidentally promoting optional week cache to a raw data store. */
class WeekPreviewIntegrationContractTest {
    private fun source(name: String): String {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(8) {
            val file = File(current,
                "app/src/main/java/com/otidotid/alke/data/$name")
            if (file.isFile) return file.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Missing source: $name")
    }

    @Test fun monthlyFallbackAndVerifiedCacheRemainInOnlineDayPath() {
        val code = source("GoogleDriveArchive.kt")
        assertTrue(code.contains("VerifiedWeekPreviewCache("))
        assertTrue(code.contains("previewMonthOnline(accessToken, month, passphrase, day)"))
        assertTrue(code.contains("prefetchAdjacentPreview("))
        assertTrue(code.contains("CloudPreviewPrefetch.eligibleOnDevice(appContext)"))
        assertTrue(code.contains("clear.delete()"))
    }

    @Test fun onlineOnlyCannotDeleteRecordsInThisRelease() {
        val code = source("VerifiedOnlineOnlyReadiness.kt")
        assertTrue(code.contains("Full table-by-table remote restore"))
        assertTrue(code.contains("Rollback copy"))
        assertTrue(code.contains("read-only", ignoreCase = true) ||
            code.contains("READ-ONLY"))
    }
}
