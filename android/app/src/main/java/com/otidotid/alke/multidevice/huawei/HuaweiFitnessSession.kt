package com.otidotid.alke.multidevice.huawei

import java.io.IOException

/** Same authenticated socket/key for serialized read-only fitness queries. */
internal class HuaweiFitnessSession(
    private val reads: HuaweiSessionReads,
    private val transport: HuaweiHiChainTransport,
    private val commands: Set<Int>,
    private val workoutCommands: Set<Int> = emptySet(),
) {
    fun supports(command: Int) = command in commands

    fun supportsLegacySleep() = supports(HuaweiHistoryRead.SLEEP_COUNT) && supports(HuaweiHistoryRead.SLEEP_PAGE)
    fun supportsWorkoutSummaries() = setOf(7, 8).all { it in workoutCommands }

    private fun <T> query(command: Int, body: ByteArray, service: Int = 7, parse: (ByteArray) -> T): T {
        require(if (service == 7) supports(command) else service == 0x17 && command in workoutCommands)
        transport.resetTrace()
        transport.send(reads.requestInitialization(service, command, body))
        return reads.receiveInitialization(transport.receive(command, service, 8000)).use {
            // Health records must be GCM-authenticated; never trust plaintext for scores.
            if (!it.encrypted) throw IOException("Unauthenticated fitness response")
            parse(it.payload)
        }
    }

    fun totals(): HuaweiFitnessRead.Totals =
        query(HuaweiFitnessRead.TOTALS, HuaweiFitnessRead.totalsRequest(), parse = HuaweiFitnessRead::totals)

    fun minutes(start: Long, end: Long, maxPages: Int = 96): List<HuaweiFitnessRead.Minute> {
        require(maxPages in 1..256 && end - start in 1..86400)
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
        val samples: List<HuaweiWorkoutDetail.Sample>, val splits: List<HuaweiWorkoutDetail.Split>)

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
            if (includeDetails && HuaweiWorkoutDetail.DATA in workoutCommands && ref.dataPages <= remainingPages) {
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
                remainingPages -= ref.dataPages
            }
            if (includeDetails && HuaweiWorkoutDetail.PACE in workoutCommands && ref.pacePages <= remainingPages) {
                repeat(ref.pacePages) { index ->
                    val page = query(HuaweiWorkoutDetail.PACE, HuaweiWorkoutDetail.request(ref.number, index, true),
                        service = 0x17, parse = HuaweiWorkoutDetail::pace)
                    require(page.number == ref.number && page.page == index)
                    splits.addAll(page.splits)
                }
                remainingPages -= ref.pacePages
            }
            WorkoutBundle(row, samples.values.toList(), splits.toList())
        }
    }
}
