package com.otidotid.alke.ui
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class BackupExactDocumentRetentionContractTest {
 @Test fun pruneUsesExactDocumentRows() { val s=File("src/main/java/com/otidotid/alke/ui/BackupSync.kt").readText(); assertTrue(s.contains("children.drop(keep.coerceAtLeast(0))")); assertTrue(!s.contains("snapshotsToPrune(children.map { it.name }, keep).toSet()")) }
}
