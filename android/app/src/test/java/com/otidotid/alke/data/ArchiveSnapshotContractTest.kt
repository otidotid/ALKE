package com.otidotid.alke.data
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class ArchiveSnapshotContractTest { @Test fun archiveUsesVacuumInto() { val s=File("src/main/java/com/otidotid/alke/data/GoogleDriveArchive.kt").readText(); assertTrue(s.contains("VACUUM INTO")); assertTrue(!s.contains("source.copyTo(destination")) } }
