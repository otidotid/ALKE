package com.otidotid.alke.data

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Disposable, encrypted-only LRU for Google Drive's independent week previews.
 *
 * Never retains plaintext, OAuth credentials, recovery passphrases or health
 * rows. Every cache hit is re-verified by length + SHA256 against the CURRENT
 * month-bound manifest part before decrypting. A corrupt file is re-fetched.
 *
 * An in-process coroutine mutex protects read-versus-prune and concurrent
 * requests from leaving partially downloaded ciphertext in the cache.
 */
internal class VerifiedWeekPreviewCache(private val directory: File) {
    companion object {
        private val lock = Mutex()
        const val MAX_PARTS = 8
        const val MAX_BYTES = 64L * 1024L * 1024L
        private val namePattern = Regex("""week-[a-f0-9]{64}\.alkeenc""")
    }

    suspend fun <T> withVerified(
        part: CloudDayPreviewPart,
        download: suspend (File) -> Unit,
        use: suspend (File) -> T,
    ): T = lock.withLock {
        require(part.sha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(part.sizeBytes in 1..(16L * 1024L * 1024L))
        directory.mkdirs()
        check(directory.isDirectory)
        val target = File(directory, "week-${part.sha256.lowercase()}.alkeenc")
        if (!matches(target, part)) {
            target.delete()
            val incoming = File.createTempFile("fetch-", ".part", directory)
            try {
                download(incoming)
                check(matches(incoming, part)) {
                    "Encrypted week preview failed SHA-256/length verification."
                }
                check(incoming.renameTo(target)) { "Could not atomically publish verified week." }
            } finally {
                incoming.delete()
            }
        }
        check(matches(target, part)) { "Verified week cache was changed." }
        target.setLastModified(System.currentTimeMillis())
        try {
            use(target)
        } finally {
            pruneKeeping(target)
        }
    }

    /**
     * Cache is ciphertext-only. Never evict the in-use object before the
     * authenticated decryption callback has returned.
     */
    private fun pruneKeeping(inUse: File) {
        val files = directory.listFiles()
            ?.filter { it.isFile && namePattern.matches(it.name) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        var kept = 0
        var bytes = 0L
        for (item in files) {
            val itemBytes = item.length()
            if (item != inUse && (kept >= MAX_PARTS ||
                    itemBytes > MAX_BYTES - bytes)) {
                item.delete()
            } else {
                kept++
                bytes += itemBytes
            }
        }
        // Stale interrupted download files never contain trusted user data.
        val cutoff = System.currentTimeMillis() - 6L * 60L * 60L * 1000L
        directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("fetch-") &&
                it.name.endsWith(".part") && it.lastModified() < cutoff
        }?.forEach(File::delete)
    }

    private fun matches(file: File, part: CloudDayPreviewPart): Boolean {
        if (!file.isFile || file.length() != part.sizeBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val bytes = ByteArray(8192)
            while (true) {
                val n = stream.read(bytes)
                if (n == -1) break
                if (n > 0) digest.update(bytes, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(part.sha256, ignoreCase = true)
    }
}
