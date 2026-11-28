package com.otidotid.alke.multidevice.huawei

import java.io.IOException

/** Explicit authenticated device rejection, not a transport or cryptographic failure. */
internal class HuaweiFitnessRejected(val service: Int, val command: Int) : IOException("Huawei fitness command rejected")

/** Same authenticated socket/key for serialized read-only fitness queries. */
internal class HuaweiFitnessSession(
    private val reads: HuaweiSessionReads,
    private val transport: HuaweiHiChainTransport,
    private val commands: Set<Int>,
    private val workoutCommands: Set<Int> = emptySet(),
    private val fileCommands: Set<Int> = emptySet(),
) {
    fun supports(command: Int) = command in commands

    /** Diagnostic-only API; no automatic file retrieval or scoring. */
    fun nativeFiles() = HuaweiFileSession(reads, transport, fileCommands)

    fun supportsMinuteHistory() = supports(HuaweiFitnessRead.COUNT)
    // Gadgetbridge initiates legacy sleep count for the fitness-history service;
    // older firmware does not list the sleep/count pair in capability discovery.
    fun supportsLegacySleep() = supports(HuaweiHistoryRead.SLEEP_COUNT) || supportsMinuteHistory()
    fun supportsWorkoutSummaries() = HuaweiHistoryRead.WORKOUT_LIST in workoutCommands

    private fun permits(service: Int, command: Int): Boolean = when (service) {
        7 -> when (command) {
            HuaweiFitnessRead.PAGE -> supportsMinuteHistory()
            HuaweiHistoryRead.SLEEP_COUNT, HuaweiHistoryRead.SLEEP_PAGE -> supportsLegacySleep()
            else -> supports(command)
        }
        0x17 -> when (command) {
            HuaweiHistoryRead.WORKOUT_TOTALS, HuaweiWorkoutDetail.DATA, HuaweiWorkoutDetail.PACE ->
                supportsWorkoutSummaries()
            else -> command in workoutCommands
        }
        else -> false
    }

    private fun <T> query(command: Int, body: ByteArray, service: Int = 7, parse: (ByteArray) -> T): T {
        require(permits(service, command))
        transport.resetTrace()
        transport.send(reads.requestInitialization(service, command, body))
        return reads.receiveInitialization(transport.receive(command, service, 8000)).use {
            // Health records must be GCM-authenticated; never trust plaintext for scores.
            if (!it.encrypted) throw IOException("Unauthenticated fitness response")
            val status = HuaweiTlv.one(HuaweiTlv.decode(it.payload), 0x7f)
            if (status != null) {
                require(status.size == 4)
                if (!status.contentEquals(byteArrayOf(0, 1, 0x86.toByte(), 0xa0.toByte())))
                    throw HuaweiFitnessRejected(service,command)
            }
            parse(it.payload)
        }
    }

    /** Battery is the known mixed-mode read; only after authenticated ProductInfo. */
    fun batteryPercent(): Int? {
        val service = HuaweiDeviceInfo.SERVICE
        val command = HuaweiDeviceInfo.BATTERY
        transport.resetTrace()
        transport.send(reads.requestInitialization(service, command, HuaweiDeviceInfo.query(command)))
        val response = try {
            transport.receive(command, service, 3000)
        } catch (_: java.net.SocketTimeoutException) {
            reads.finishInitializationWithoutReply(service, command)
            transport.ignoreLateReply(service, command)
            return null
        }
        return reads.receiveInitialization(response).use { reply ->
            val fields = HuaweiTlv.decode(reply.payload)
            val status = HuaweiTlv.one(fields, 0x7f)
            if (status != null) {
                require(status.size == 4)
                if (!status.contentEquals(byteArrayOf(0,1,0x86.toByte(),0xa0.toByte()))) {
                    if (!reply.encrypted) require(fields.size == 1)
                    return@use null
                }
            }
            if (!reply.encrypted) require(fields.all { it.tag in 1..3 || it.tag == 0x7f } &&
                HuaweiTlv.one(fields, 1)?.size == 1)
            HuaweiDeviceInfo.battery(HuaweiLinkFrames.Message(service, command, reply.payload))
        }
    }

    fun totals(): HuaweiFitnessRead.Totals =
        query(HuaweiFitnessRead.TOTALS, HuaweiFitnessRead.totalsRequest(), parse = HuaweiFitnessRead::totals)

    fun minutes(start: Long, end: Long, maxPages: Int = 96): List<HuaweiFitnessRead.Minute> {
        require(supportsMinuteHistory() && maxPages in 1..256 && end - start in 1..86400)
        val count = query(HuaweiFitnessRead.COUNT, HuaweiFitnessRead.countRequest(start, end), parse = HuaweiFitnessRead::count)
        require(count <= maxPages) { "Fitness page limit; use a shorter time window" }
        val records = sortedMapOf<Long, HuaweiFitnessRead.Minute>()
        for (index in 0 until count) {
            val page = query(HuaweiFitnessRead.PAGE, HuaweiFitnessRead.pageRequest(index), parse = HuaweiFitnessRead::page)
            require(page.index == index) { "Mismatched fitness page" }
            for (minute in page.minutes) {
                if (minute.epochSeconds !in start until end) continue
                val previous = records.put(minute.epochSeconds, minute)
                require(previous == null || previous == minute) { "Conflicting Huawei minute records" }
            }
        }
        return records.values.toList()
    }

    fun legacySleep(start: Long, end: Long): List<HuaweiHistoryRead.SleepInterval> {
        require(supportsLegacySleep() && end - start in 1..86400)
        val count = query(HuaweiHistoryRead.SLEEP_COUNT, HuaweiFitnessRead.countRequest(start, end),
            parse = HuaweiFitnessRead::count)
        require(count <= 96)
        val rows = linkedMapOf<Long, HuaweiHistoryRead.SleepInterval>()
        for (index in 0 until count) {
            val page = query(HuaweiHistoryRead.SLEEP_PAGE, HuaweiFitnessRead.pageRequest(index),
                parse = HuaweiHistoryRead::sleepPage)
            require(page.index == index)
            for (row in page.rows) {
                if (row.start >= end || row.end <= start) continue
                val old = rows.put(row.start, row)
                require(old == null || old == row) { "Conflicting sleep intervals" }
            }
        }
        return rows.values.sortedBy { it.start }
    }

    data class WorkoutBundle(val summary: HuaweiHistoryRead.WorkoutSummary,
        val samples: List<HuaweiWorkoutDetail.Sample>, val splits: List<HuaweiWorkoutDetail.Split>,
        val detailsComplete: Boolean = false)

    fun workoutSummaries(start: Long, end: Long): List<HuaweiHistoryRead.WorkoutSummary> =
        workoutBundles(start, end, includeDetails = false).map { it.summary }

    fun workoutBundles(start: Long, end: Long, includeDetails: Boolean = true): List<WorkoutBundle> {
        require(supportsWorkoutSummaries() && end - start in 1..86400)
        val refs = query(HuaweiHistoryRead.WORKOUT_LIST, HuaweiHistoryRead.workoutListRequest(start, end),
            service = 0x17, parse = HuaweiHistoryRead::workoutList)
        require(refs.size <= 64)
        var remainingPages = 256
        return refs.mapNotNull { ref ->
            val row = query(HuaweiHistoryRead.WORKOUT_TOTALS, HuaweiHistoryRead.workoutTotalsRequest(ref.number),
                service = 0x17, parse = HuaweiHistoryRead::workoutSummary)
            require(row.number == ref.number)
            if (row.start >= end || row.end <= start) return@mapNotNull null
            val samples = sortedMapOf<Long, HuaweiWorkoutDetail.Sample>()
            val splits = mutableListOf<HuaweiWorkoutDetail.Split>()
            var dataComplete = false
            var paceComplete = false
            if (includeDetails && ref.dataPages <= remainingPages) {
                remainingPages -= ref.dataPages
                try {
                    repeat(ref.dataPages) { index ->
                        val page = query(HuaweiWorkoutDetail.DATA, HuaweiWorkoutDetail.request(ref.number, index, false),
                            service = 0x17, parse = HuaweiWorkoutDetail::data)
                        require(page.number == ref.number && page.page == index)
                        for (sample in page.samples) {
                            if (sample.timestamp !in row.start..row.end) continue
                            val previous = samples.put(sample.timestamp, sample)
                            require(previous == null || previous == sample)
                        }
                    }
                    dataComplete = true
                } catch (_: HuaweiFitnessRejected) {
                    // An explicitly rejected optional stream must not discard the verified summary.
                    // Never persist a truncated set of detail pages.
                    samples.clear()
                }
            }
            if (includeDetails && ref.pacePages <= remainingPages) {
                remainingPages -= ref.pacePages
                try {
                    repeat(ref.pacePages) { index ->
                        val page = query(HuaweiWorkoutDetail.PACE, HuaweiWorkoutDetail.request(ref.number, index, true),
                            service = 0x17, parse = HuaweiWorkoutDetail::pace)
                        require(page.number == ref.number && page.page == index)
                        splits.addAll(page.splits)
                    }
                    paceComplete = true
                } catch (_: HuaweiFitnessRejected) {
                    splits.clear()
                }
            }
            WorkoutBundle(row, samples.values.toList(), splits.toList(),dataComplete && paceComplete)
        }
    }
}
