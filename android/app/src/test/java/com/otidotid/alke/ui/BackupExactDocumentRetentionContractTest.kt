package com.otidotid.alke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExactDocumentRetentionContractTest {
    @Test fun retainManualFilesAndDeleteOnlyTheOlderDuplicate() {
        data class Doc(val name: String, val id: Int)
        val first = Doc("alke-backup-20261120-090000.alkebak", 1)
        val duplicate = Doc(first.name, 2)
        val manual = Doc("alke-backup-20261120-090000-copy.alkebak", 3)

        assertEquals(listOf(duplicate), BackupSync.pruneCandidates(listOf(manual, first, duplicate), 1, Doc::name))
    }
}
