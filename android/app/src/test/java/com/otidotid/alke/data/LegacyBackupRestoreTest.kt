package com.otidotid.alke.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Legacy-format regression tests use FAKE SQLite bytes and invented settings only.
 * Never commit a real user's historical health archive or any extracts of its records.
 * This suite exercises exactly the same extraction, verification and preflight paths
 * called by DataBackup.importFrom, without touching a user's live Room database.
 */
class LegacyBackupRestoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
    private val sample = magic + "public-synthetic-fixture".toByteArray()

    private fun archive(entry: String, withSettings: Boolean = true): File {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            zip.write(sample)
            zip.closeEntry()
            if (withSettings) {
                zip.putNextEntry(ZipEntry("settings.json"))
                zip.write("""{"units.system":"metric"}""".toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun stage(backup: File, settings: File? = null): Pair<DataBackup.StageResult, File> {
        val staged = tmp.newFile()
        val result = DataBackup.stageBackupSqlite(
            backup.inputStream(), DataBackup.peekHeader(backup), staged, settings,
        )
        return result to staged
    }

    @Test fun historicNoopZipIsRecognizedForMigration() {
        val file = archive("noop-backup.sqlite")
        val settings = tmp.newFile()
        val (result, staged) = stage(file, settings)
        assertEquals(DataBackup.StageResult.OK, result)
        assertArrayEquals(sample, staged.readBytes())
        assertTrue(DataBackup.isValidSqliteHeader(staged))
        assertTrue(DataBackup.backupStreamIsIntact(file.inputStream()))
        assertEquals(sample.size.toLong(), DataBackup.restoreDatabaseBytes(file))
        assertEquals("""{"units.system":"metric"}""", settings.readText())
    }

    @Test fun historicThoopZipIsRecognizedForMigration() {
        val file = archive("thoop-backup.sqlite", withSettings = false)
        val (result, staged) = stage(file)
        assertEquals(DataBackup.StageResult.OK, result)
        assertArrayEquals(sample, staged.readBytes())
        assertTrue(DataBackup.backupStreamIsIntact(file.inputStream()))
        assertEquals(sample.size.toLong(), DataBackup.restoreDatabaseBytes(file))
    }

    @Test fun newAlkeZipRemainsSupported() {
        val file = archive("alke-backup.sqlite")
        val (result, staged) = stage(file)
        assertEquals(DataBackup.StageResult.OK, result)
        assertArrayEquals(sample, staged.readBytes())
        assertTrue(DataBackup.backupStreamIsIntact(file.inputStream()))
    }

    @Test fun unrelatedSqliteAndForgedNameAreRejected() {
        assertFalse(DataBackup.supportedHealthBackupEntry("unrelated.sqlite"))
        assertFalse(DataBackup.supportedHealthBackupEntry("my-noop-backup.sqlite"))
        assertFalse(DataBackup.supportedHealthBackupEntry("noop-backup.sqlite.exe"))
        for (name in listOf("unrelated.sqlite", "my-noop-backup.sqlite")) {
            val backup = archive(name)
            assertEquals(DataBackup.StageResult.NO_DB_IN_ZIP, stage(backup).first)
            assertFalse(DataBackup.backupStreamIsIntact(backup.inputStream()))
            assertEquals(null, DataBackup.restoreDatabaseBytes(backup))
        }
    }
}
