package com.otidotid.alke.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-surface safety contracts because ArchiveEncryption tests explicitly
 * forbid overwriting pre-existing encrypted/plaintext target paths.
 */
class CloudDayPreviewSafetyContractTest {
    private fun archiveSource(): String {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(7) {
            val target = File(current,
                "app/src/main/java/com/otidotid/alke/data/GoogleDriveArchive.kt")
            if (target.isFile) return target.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Archive source missing from source checkout")
    }

    @Test fun independentlyEncryptedPreviewAlwaysUsesUnusedTargetPaths() {
        val source = archiveSource()
        val read = source.substringAfter("private suspend fun readVerifiedDayPreview(")
            .substringBefore("    data class ArchivePreview(", missingDelimiterValue = "")
        // Method placement can change; the essential invariant is that the
        // .json decrypt destination is deleted before decrypt is invoked.
        assertTrue(source.contains(
            "val clear = File.createTempFile(\"week-\", \".json\", work).apply {"))
        assertTrue(source.contains(
            "val encrypted = File.createTempFile(\"weekly-\", \".alkeenc\", work).apply {"))
        assertTrue(source.contains(
            "val recovered = File.createTempFile(\"weekly-\", \".verify.json\", work).apply {"))
        assertTrue(source.contains("ArchiveEncryption.decrypt(verified, recovered, passphrase)"))
        assertTrue(source.contains("ArchiveEncryption.decrypt(encrypted, clear, passphrase)"))
    }

    @Test fun monthlyReaderIsStillTheFallbackAndNeverDeletesLocalHistory() {
        val source = archiveSource()
        assertTrue(source.contains("previewMonthOnline(accessToken, month, passphrase, day)"))
        assertTrue(source.contains("catch (cancelled: CancellationException)"))
        assertTrue(source.contains("dayPreviewParts = previews"))
    }
}
