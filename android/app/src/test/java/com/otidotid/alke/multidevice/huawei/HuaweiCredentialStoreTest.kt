package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class HuaweiCredentialStoreTest {
    private val key = SecretKeySpec(ByteArray(32) { 42 }, "AES")
    private val scope = HuaweiCredentialStore.scope("00:11:22:33:44:55")
    private val identity = "00112233445566778899aabbccddeeff"

    @Test fun addressScopeIsCaseInsensitiveAndDeviceSpecific() {
        assertArrayEquals(HuaweiCredentialStore.scope("aa:bb:cc:dd:ee:ff"),
            HuaweiCredentialStore.scope("AA:BB:CC:DD:EE:FF"))
        assertFalse(scope.contentEquals(HuaweiCredentialStore.scope("00:11:22:33:44:56")))
        assertTrue(runCatching { HuaweiCredentialStore.scope("not a device") }.isFailure)
    }

    @Test fun encryptedBindingRoundTripUsesFreshIvAndNoPlaintext() {
        for (size in listOf(16, 32)) {
            HuaweiCredentialStore.Binding(identity, ByteArray(size) { 23 }).use { binding ->
                val a = HuaweiCredentialStore.seal(key, scope, binding)
                val b = HuaweiCredentialStore.seal(key, scope, binding)
                assertFalse(a.contentEquals(b))
                assertFalse(a.toString(Charsets.ISO_8859_1).contains(identity))
                HuaweiCredentialStore.open(key, scope, a).use { restored ->
                    assertEquals(identity, restored.identity)
                    assertArrayEquals(binding.credential, restored.credential)
                }
            }
        }
    }

    @Test fun tamperedOrCrossDeviceBindingsAreRejected() {
        HuaweiCredentialStore.Binding(identity, ByteArray(32) { 23 }).use { binding ->
            val a = HuaweiCredentialStore.seal(key, scope, binding)
            assertTrue(runCatching { HuaweiCredentialStore.open(key,
                HuaweiCredentialStore.scope("00:11:22:33:44:56"), a) }.isFailure)
            assertTrue(runCatching { HuaweiCredentialStore.open(
                SecretKeySpec(ByteArray(32), "AES"), scope, a) }.isFailure)
            a[a.lastIndex] = (a.last().toInt() xor 1).toByte()
            assertTrue(runCatching { HuaweiCredentialStore.open(key, scope, a) }.isFailure)
            assertTrue(runCatching { HuaweiCredentialStore.open(key, scope, a.copyOf(10)) }.isFailure)
        }
    }

    @Test fun closingBindingClearsCredentialBytes() {
        val raw = ByteArray(32) { 23 }
        val binding = HuaweiCredentialStore.Binding(identity, raw)
        binding.close()
        assertTrue(raw.all { it == 0.toByte() })
    }
}
