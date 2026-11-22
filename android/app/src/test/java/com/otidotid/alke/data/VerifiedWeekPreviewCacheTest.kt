package com.otidotid.alke.data

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedWeekPreviewCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun part(bytes: ByteArray) =
        CloudDayPreviewPart("2026-10-01", "2026-10-07", "drive-id",
            sha(bytes), bytes.size.toLong(), 4, "a".repeat(64))

    @Test fun downloadsOnceAndReturnsOnlyVerifiedEncryptedCache() = runBlocking {
        val encrypted = ByteArray(2048) { (it % 128).toByte() }
        var downloads = 0
        val store = VerifiedWeekPreviewCache(tmp.root)
        val item = part(encrypted)
        repeat(2) {
            store.withVerified(item, { file ->
                downloads++
                file.writeBytes(encrypted)
            }, { file ->
                assertArrayEquals(encrypted, file.readBytes())
                assertTrue(file.name.endsWith(".alkeenc"))
            })
        }
        assertEquals(1, downloads)
        assertEquals(1, tmp.root.listFiles()!!.size)
    }

    @Test fun corruptedCiphertextIsDiscardedAndFetchedAgain() = runBlocking {
        val bytes = "encrypted-fixture".toByteArray()
        val item = part(bytes)
        val cache = VerifiedWeekPreviewCache(tmp.root)
        var calls = 0
        suspend fun obtain() = cache.withVerified(item, { destination ->
            calls++
            destination.writeBytes(bytes)
        }, { it.readBytes() })
        assertArrayEquals(bytes, obtain())
        tmp.root.listFiles()!!.single().writeText("BAD")
        assertArrayEquals(bytes, obtain())
        assertEquals(2, calls)
    }

    @Test fun failedIntegrityCheckNeverPublishesAReuseCandidate() = runBlocking {
        val bytes = "ciphertext".toByteArray()
        val item = part(bytes)
        val cache = VerifiedWeekPreviewCache(tmp.root)
        val failure = runCatching {
            cache.withVerified(item, { it.writeBytes("tampered".toByteArray()) }, { it.length() })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(tmp.root.listFiles().orEmpty().none { it.name.endsWith(".alkeenc") })
    }

    @Test fun cacheEvictsOldEncryptedEntriesAndNeverTouchesUnknownFiles() = runBlocking {
        val cache = VerifiedWeekPreviewCache(tmp.root)
        val unrelated = File(tmp.root, "user-important.sqlite").apply { writeText("leave me") }
        for (i in 1..10) {
            val data = "encrypted-preview-$i".toByteArray()
            cache.withVerified(part(data), { it.writeBytes(data) }, { })
        }
        assertTrue(unrelated.exists())
        assertTrue(tmp.root.listFiles()!!.count { it.name.endsWith(".alkeenc") } <=
            VerifiedWeekPreviewCache.MAX_PARTS)
    }
}
