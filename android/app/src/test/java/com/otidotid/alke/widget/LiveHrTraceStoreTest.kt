package com.otidotid.alke.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiveHrTraceStoreTest {
    @Test fun everyMinuteWritesOneDurablePointIndependentOfSnapshot() {
        val context = RuntimeEnvironment.getApplication()
        val t = 1_800_000_020_000L
        LiveHrTraceStore.record(context, 71, t)
        LiveHrTraceStore.record(context, 75, t + 2_000L)
        LiveHrTraceStore.record(context, 80, t + 60_000L)
        val series = LiveHrTraceStore.load(context, nowMs = t + 61_000L)
        assertTrue(series.any { it.bpm == 71 })
        assertTrue(series.any { it.bpm == 80 })
        assertEquals(2, series.size)
    }
}
