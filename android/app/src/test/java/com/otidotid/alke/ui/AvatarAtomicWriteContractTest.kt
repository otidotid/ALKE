package com.otidotid.alke.ui
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class AvatarAtomicWriteContractTest { @Test fun avatarChecksCompressionAndTempFile() { val s=File("src/main/java/com/otidotid/alke/ui/ProfileAvatar.kt").readText(); assertTrue(s.contains("check(scaled.compress")); assertTrue(s.contains(".tmp")); assertTrue(s.contains("decodeFile(temp.absolutePath)")) } }
