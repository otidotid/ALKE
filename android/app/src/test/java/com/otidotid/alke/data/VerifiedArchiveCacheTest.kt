package com.otidotid.alke.data

import java.io.File
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedArchiveCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun entry(name: String, length: Int = 10, age: Long = 10L): File =
        File(temp.root, name).apply {
            writeBytes(ByteArray(length) { 0x4a })
            setLastModified(age)
        }

    @Test fun sameMonthPreviewKeepsOnlyEncryptedDownload() {
        val encrypted = entry("2026-08.alkeenc")
        val unzipped = entry("2026-08.sqlite")
        val clearArchive = entry("2026-08.alkearc")
        val different = entry("2026-09.alkeenc")
        VerifiedArchiveCache.clearPlaintextForMonth(temp.root, YearMonth.of(2026, 8))
        assertTrue(encrypted.exists())
        assertFalse(unzipped.exists())
        assertFalse(clearArchive.exists())
        assertTrue(different.exists())
    }

    @Test fun retainsThreeMostRecentlyVerifiedEncryptedMonthsAndNeverTouchesOtherFiles() {
        val oldest = entry("2026-01.alkeenc", age = 100L)
        val second = entry("2026-02.alkeenc", age = 200L)
        val third = entry("2026-03.alkeenc", age = 300L)
        val newest = entry("2026-04.alkeenc", age = 400L)
        val unrelated = entry("snapshot.json", age = 0L)
        VerifiedArchiveCache.prune(temp.root)
        assertFalse(oldest.exists())
        assertTrue(second.exists())
        assertTrue(third.exists())
        assertTrue(newest.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun overBudgetEncryptedArchiveIsDiscardedWithoutDeletingOtherData() {
        val root = temp.root
        val large = File(root, "2026-05.alkeenc").apply {
            java.io.RandomAccessFile(this, "rw").use {
                it.setLength(VerifiedArchiveCache.MAX_TOTAL_BYTES + 1)
            }
        }
        val text = entry("2026-05.sqlite")
        VerifiedArchiveCache.prune(root)
        assertFalse(large.exists())
        // prune never deletes any SQLite file; the preview's explicit
        // clearPlaintextForMonth is responsible after the DB handle closes.
        assertTrue(text.exists())
        VerifiedArchiveCache.clearPlaintextForMonth(root, YearMonth.of(2026, 5))
        assertFalse(text.exists())
    }
}
