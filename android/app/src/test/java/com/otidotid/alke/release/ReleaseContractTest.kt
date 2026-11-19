package com.otidotid.alke.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseContractTest {
    private fun root(): File {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(5) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Android project root not found")
    }

    @Test fun personalIdentityAndVersionAreExplicit() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(Regex("versionCode\\s*=\\s*\\d+").containsMatchIn(gradle))
        assertTrue(Regex("versionName\\s*=\\s*\"1\\.0\\.0-alpha\\.[^\"]+\"").containsMatchIn(gradle))
        assertTrue(gradle.contains("applicationIdSuffix = \".personal\""))
    }

    @Test fun sourceDoesNotEmbedReleaseSecretsOrBackupPayloads() {
        val project = root()
        val forbiddenNames = setOf("local.properties", "alke-private-backup.jks")
        val trackedRoots = listOf(File(project, "app/src"), File(project.parentFile, ".github/workflows"))
        val offenders = trackedRoots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown().filter { file ->
                file.isFile && (file.name in forbiddenNames || file.extension.lowercase() in setOf("p12", "jks", "keystore", "noopbak", "alkebak"))
            }.toList()
        }
        assertTrue("Forbidden release payloads: $offenders", offenders.isEmpty())
    }

    @Test fun personalBuildDoesNotHardcodeOldReleaseVersion() {
        val workflowDir = File(root().parentFile, ".github/workflows")
        val current = File(root(), "app/build.gradle.kts").readText()
        val currentCode = Regex("versionCode\\s*=\\s*(\\d+)").find(current)!!.groupValues[1]
        val canonical = File(workflowDir, "alke-personal-quality-gate.yml").readText()
        assertFalse(canonical.contains("versionCode = $currentCode"))
        assertFalse(canonical.contains("versionCode='$currentCode'"))
    }
}
