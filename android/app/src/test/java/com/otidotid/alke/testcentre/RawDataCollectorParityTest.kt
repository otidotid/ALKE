package com.otidotid.alke.testcentre

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Android half of the shared feature-level raw-data collector parity guard. */
class RawDataCollectorParityTest {
    private val oracleBytes: ByteArray
        get() = requireNotNull(javaClass.classLoader!!.getResourceAsStream("raw_data_collector_parity.json"))
            .use { it.readBytes() }

    private fun repoRoot(): File {
        var cursor = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(4) {
            if (File(cursor, "android/app/src/main").isDirectory) return cursor
            cursor = cursor.parentFile ?: cursor
        }
        error("repository root not found from ${System.getProperty("user.dir")}")
    }

    @Test fun androidSurfaceStillImplementsEveryDeclaredCapability() {
        val root = repoRoot()
        val source = listOf(
            "android/app/src/main/java/com/otidotid/alke/testcentre/GroundTruthCollector.kt",
            "android/app/src/main/java/com/otidotid/alke/testcentre/ImuSessionFileStore.kt",
            "android/app/src/main/java/com/otidotid/alke/testcentre/ImuSessionFileStore.kt",
            "android/app/src/main/java/com/otidotid/alke/data/WhoopDatabase.kt",
            "android/app/src/main/java/com/otidotid/alke/data/Entities.kt",
            "android/app/src/main/java/com/otidotid/alke/data/WhoopRepository.kt",
            "android/app/src/main/java/com/otidotid/alke/ui/GroundTruthCollectorScreen.kt",
            "android/app/src/main/java/com/otidotid/alke/ble/WhoopBleClient.kt",
            "android/app/src/main/res/values/strings.xml",
        ).joinToString("\n") { File(root, it).readText() }
        val capabilities = JSONObject(oracleBytes.toString(Charsets.UTF_8)).getJSONObject("capabilities")
        for (name in capabilities.keys()) {
            val markers = capabilities.getJSONObject(name).getJSONArray("kotlin")
            for (index in 0 until markers.length()) {
                val marker = markers.getString(index)
                assertTrue("Android collector lost $name marker: $marker", source.contains(marker))
            }
        }
    }

    @Test fun androidAndAppleOracleCopiesMatchExceptBrandedImuMagic() {
        val apple = File(repoRoot(), "StrandTests/Resources/raw_data_collector_parity.json")
        val referenceText = apple.readText()
        val androidText = oracleBytes.toString(Charsets.UTF_8)
        val referenceImu = JSONObject(referenceText).getJSONObject("capabilities")
            .getJSONObject("session_bound_imu_file")
        val alkeImu = JSONObject(androidText).getJSONObject("capabilities")
            .getJSONObject("session_bound_imu_file")
        val referenceMagic = referenceImu.getJSONArray("swift").getString(2)
        val actualMagic = alkeImu.getJSONArray("swift").getString(2)
        assertEquals(referenceMagic, referenceImu.getJSONArray("kotlin").getString(2))
        assertEquals("ALKEIMU2", actualMagic)
        assertEquals(actualMagic, alkeImu.getJSONArray("kotlin").getString(2))
        // Exactly two identity bytestrings (Swift + Kotlin) can differ. All
        // numerical rules, capabilities, markers and other bytes must match.
        assertEquals(2, referenceText.split("\"$referenceMagic\"").size - 1)
        val normalizedReference = referenceText.replace("\"$referenceMagic\"", "\"$actualMagic\"")
        assertEquals(oracleBytes.toList(), normalizedReference.toByteArray().toList())
    }
}
