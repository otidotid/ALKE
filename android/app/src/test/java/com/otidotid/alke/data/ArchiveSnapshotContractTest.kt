package com.otidotid.alke.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSnapshotContractTest {

    @Test
    fun archiveUsesVersionGatedDatabaseAwareSnapshot() {
        val source =
            File("src/main/java/com/otidotid/alke/data/GoogleDriveArchive.kt")
                .readText()

        // API 30+ retains SQLite VACUUM INTO.
        assertTrue(
            source.contains(
                "Build.VERSION.SDK_INT >= Build.VERSION_CODES.R"
            )
        )

        assertTrue(
            source.contains("VACUUM INTO")
        )

        // API 26-29 performs FULL WAL checkpoint first, then copies the
        // checkpointed main database into a private working file.
        assertTrue(
            source.contains("PRAGMA wal_checkpoint(FULL)")
        )

        assertTrue(
            source.contains(
                "source.copyTo(destination, overwrite = true)"
            )
        )

        // Destination must still be checked before filtering,
        // validation, upload, or any later pruning decision.
        assertTrue(
            source.contains(
                "check(destination.isFile && destination.length() > 0L)"
            )
        )
    }
}
