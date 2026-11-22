package com.otidotid.alke.data

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes expensive app-local maintenance I/O (compact snapshots, verified
 * monthly archives, and complete Google backups), including user-triggered jobs.
 *
 * This is deliberately a process-wide coroutine mutex, not a queue of raw health
 * records and not a scoring cache. It protects the shared SQLite/WAL, disk
 * working space and Drive throughput without changing data, score calculations,
 * backup cadence or export/restore semantics.
 *
 * The normal Android WorkManager configuration runs these jobs in ALKE's main
 * process. Inter-process coordination would need a separate lock if that changes.
 */
internal object BackgroundDataWorkGate {
    private val mutex = Mutex()

    suspend fun <T> withExclusive(block: suspend () -> T): T {
        mutex.lock() // cancellable while waiting; never block an Android thread
        try {
            return block()
        } finally {
            mutex.unlock() // also covers exceptions and coroutine cancellation
        }
    }
}
