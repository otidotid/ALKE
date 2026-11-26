package com.otidotid.alke.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NapInsertOverlapContractTest {
    @Test fun napChecksPersistedSleepBeforeInsertAndOnlyReconcilesWhenSaved() {
        var dir=File(System.getProperty("user.dir")?:".").canonicalFile
        val path="android/app/src/main/java/com/otidotid/alke/data/WhoopRepository.kt"
        repeat(7) { if(!File(dir,path).isFile) dir=dir.parentFile?:dir }
        val method=File(dir,path).readText().substringAfter("suspend fun addManualNap(").substringBefore("/** Asleep fraction")
        val check=method.indexOf("dao.sleepSessionsOverlapping(safeStartTs, safeEndTs)")
        val insert=method.indexOf("dao.insertSleepSession(")
        val guard=method.indexOf("if (!inserted) return false")
        val reconcile=method.indexOf("reconcileAutoWorkoutsForSleep(")
        assertTrue(check>=0 && check<insert && insert<guard && guard<reconcile)
        assertTrue(method.contains("val inserted = transactor.run"))
    }
}
