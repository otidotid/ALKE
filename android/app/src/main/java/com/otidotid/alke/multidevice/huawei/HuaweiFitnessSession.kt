package com.otidotid.alke.multidevice.huawei

import java.io.IOException

/** Same authenticated socket/key for serialized read-only fitness queries. */
internal class HuaweiFitnessSession(
    private val reads: HuaweiSessionReads,
    private val transport: HuaweiHiChainTransport,
    private val commands: Set<Int>,
) {
    fun supports(command: Int) = command in commands

    private fun <T> query(command: Int, body: ByteArray, parse: (ByteArray) -> T): T {
        require(supports(command))
        transport.resetTrace()
        transport.send(reads.requestInitialization(7, command, body))
        return reads.receiveInitialization(transport.receive(command, 7, 8000)).use {
            // Health records must be GCM-authenticated; never trust plaintext for scores.
            if (!it.encrypted) throw IOException("Unauthenticated fitness response")
            parse(it.payload)
        }
    }

    fun totals(): HuaweiFitnessRead.Totals =
        query(HuaweiFitnessRead.TOTALS, HuaweiFitnessRead.totalsRequest(), HuaweiFitnessRead::totals)

    fun minutes(start: Long, end: Long, maxPages: Int = 96): List<HuaweiFitnessRead.Minute> {
        require(maxPages in 1..256 && end - start in 1..86400)
        val count = query(HuaweiFitnessRead.COUNT, HuaweiFitnessRead.countRequest(start, end), HuaweiFitnessRead::count)
        require(count <= maxPages) { "Fitness page limit; use a shorter time window" }
        val records = sortedMapOf<Long, HuaweiFitnessRead.Minute>()
        for (index in 0 until count) {
            val page = query(HuaweiFitnessRead.PAGE, HuaweiFitnessRead.pageRequest(index), HuaweiFitnessRead::page)
            require(page.index == index) { "Mismatched fitness page" }
            for (minute in page.minutes) {
                if (minute.epochSeconds !in start until end) continue
                val previous = records.put(minute.epochSeconds, minute)
                require(previous == null || previous == minute) { "Conflicting Huawei minute records" }
            }
        }
        return records.values.toList()
    }
}
