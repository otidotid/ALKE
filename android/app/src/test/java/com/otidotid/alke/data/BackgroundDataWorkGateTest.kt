package com.otidotid.alke.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDataWorkGateTest {
    @Test fun independentBackgroundJobsNeverOverlap() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val first = async {
            BackgroundDataWorkGate.withExclusive {
                firstEntered.complete(Unit)
                releaseFirst.await()
                "snapshot"
            }
        }
        firstEntered.await()
        val second = async {
            BackgroundDataWorkGate.withExclusive {
                secondEntered.complete(Unit)
                "backup"
            }
        }
        yield()
        assertFalse("Full backup must not overlap a snapshot", secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        assertEquals("snapshot", withTimeout(2_000) { first.await() })
        assertEquals("backup", withTimeout(2_000) { second.await() })
        assertTrue(secondEntered.isCompleted)
    }

    @Test fun cancellationAndExceptionsNeverStrandLaterJobs() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val running = async {
            BackgroundDataWorkGate.withExclusive {
                started.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        started.await()
        running.cancelAndJoin()
        assertEquals(2, withTimeout(2_000) {
            BackgroundDataWorkGate.withExclusive { 2 }
        })
        val error = runCatching {
            BackgroundDataWorkGate.withExclusive<Int> {
                throw IllegalStateException("fixture error")
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(3, withTimeout(2_000) {
            BackgroundDataWorkGate.withExclusive { 3 }
        })
    }
}
