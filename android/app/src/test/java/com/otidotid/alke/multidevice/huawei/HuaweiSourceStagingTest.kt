package com.otidotid.alke.multidevice.huawei

import com.otidotid.alke.multidevice.WearableSourcePolicy
import org.junit.Assert.*
import org.junit.Test

class HuaweiSourceStagingTest {
    @Test fun repeatedSyncUpdatesWithoutAddingOrErasingMissingFields() {
        val first = HuaweiFitnessRead.Minute(120, 12, 72, 98)
        val next = HuaweiFitnessRead.Minute(120, 14, null, null)
        val merged = HuaweiFitnessStore.mergeMinute(first, next)
        assertEquals(HuaweiFitnessRead.Minute(120, 14, 72, 98), merged)
        assertEquals(merged, HuaweiFitnessStore.mergeMinute(merged, next))
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiFitnessStore.mergeMinute(first, next.copy(epochSeconds = 180))
        }
    }

    @Test fun deviceIdentityIsStableAndScoped() {
        val a = HuaweiFitnessStore.deviceKey("AA:BB:CC:DD:EE:FF")
        assertEquals(64, a.length)
        assertEquals(a, HuaweiFitnessStore.deviceKey("aa:bb:cc:dd:ee:ff"))
        assertNotEquals(a, HuaweiFitnessStore.deviceKey("AA:BB:CC:DD:EE:FE"))
    }

    @Test fun huaweiWinsOnlyWhenAlignedAndValidAndWhoopFillsGaps() {
        val huawei = listOf(
            HuaweiFitnessRead.Minute(60, null, 80, null),
            HuaweiFitnessRead.Minute(120, null, null, null),
            HuaweiFitnessRead.Minute(181, null, 85, null),
            HuaweiFitnessRead.Minute(240, null, 255, null),
        )
        val whoop = listOf(
            HuaweiHrSelection.WhoopMinute(60, 73.0),
            HuaweiHrSelection.WhoopMinute(120, 72.0),
            HuaweiHrSelection.WhoopMinute(180, 74.0),
            HuaweiHrSelection.WhoopMinute(240, 75.0),
        )
        val points = HuaweiHrSelection.select(huawei, whoop)
        assertEquals(listOf(60L, 120L, 180L, 240L), points.map { it.start })
        assertEquals(listOf(80.0, 72.0, 74.0, 75.0), points.map { it.bpm })
        assertEquals(WearableSourcePolicy.Source.HUAWEI, points.first().source)
        assertTrue(points.drop(1).all { it.source == WearableSourcePolicy.Source.WHOOP })
    }
}
