package com.otidotid.alke.data

import java.io.File
import java.time.YearMonth

/**
 * Bounded app-private cache of VERIFIED encrypted Google Drive archive bytes.
 *
 * Verification against the current manifest SHA-256 and size is performed by
 * GoogleDriveArchiveManager on every open. This helper never trusts cache
 * metadata, never stores a passphrase or token, and never retains cleartext
 * .sqlite / .alkearc files between archive preview calls.
 *
 * Cache files are disposable; Android may evict cacheDir at any time.
 */
internal object VerifiedArchiveCache {
    const val MAX_MONTHS = 3
    const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L
    private val encryptedMonth = Regex("""\d{4}-\d{2}\.alkeenc""")

    /** Called after SQLiteDatabase.close(); keeps ciphertext ONLY. */
    fun clearPlaintextForMonth(directory: File, month: YearMonth) {
        val name = month.toString()
        val keepEncrypted = "$name.alkeenc"
        directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("$name.") && it.name != keepEncrypted
        }?.forEach(File::delete)
    }

    /**
     * LRU by last verified use. Does not alter Drive or raw local history.
     * Oversized single archives simply get re-fetched on a later preview.
     */
    fun prune(directory: File) {
        val candidates = directory.listFiles()
            ?.filter { it.isFile && encryptedMonth.matches(it.name) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var kept = 0
        var size = 0L
        for (file in candidates) {
            val bytes = file.length()
            if (kept >= MAX_MONTHS || bytes > MAX_TOTAL_BYTES - size) {
                file.delete()
            } else {
                kept++
                size += bytes
            }
        }
    }
}
