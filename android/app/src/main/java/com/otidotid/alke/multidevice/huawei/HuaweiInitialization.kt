package com.otidotid.alke.multidevice.huawei

import java.io.IOException
import java.net.SocketTimeoutException

internal class HuaweiAccountSelectionRequired : IOException("Watch requires account selection; no automatic reset")

/**
 * Post-HiChain setup, based on public Huawei interoperability command schemas.
 * Never accepts legal agreements, changes an existing account or resets a watch.
 * Command replies are scoped to the already authenticated RFCOMM session.
 */
internal class HuaweiInitialization(
    private val reads: HuaweiSessionReads,
    private val transport: HuaweiHiChainTransport,
    private val onStage: (String) -> Unit,
    private val onMetadata: (String) -> Unit,
) {
    data class Report(
        val services: Set<Int>,
        val connectSupported: Boolean,
        val connectReply: Boolean,
        val connectEncrypted: Boolean,
        val notes: List<String>,
        val fitnessCommands: Set<Int> = emptySet(),
    )

    private val notes = mutableListOf<String>()

    fun run(deviceName: String): Report {
        val serviceFlags = query(1, 2, fields(1 to SERVICES), "Supported services", true) { f, _ ->
            val flags = requireNotNull(HuaweiTlv.one(f, 2))
            require(flags.size <= SERVICES.size)
            require(flags.all { it.toInt() in 0..1 })
            SERVICES.filterIndexed { i, _ -> flags.getOrNull(i) == 1.toByte() }
                .map { it.toInt() and 255 }.toSet() + 1
        } ?: error("Required service discovery")
        val requested = linkedMapOf(1 to DEVICE_COMMANDS)
        if (7 in serviceFlags) requested[7] = byteArrayOf(3, 10, 11)
        if (0x1a in serviceFlags) requested[0x1a] = byteArrayOf(5, 6)
        val commands = query(1, 3, commandQuery(requested), "Supported commands", true) { f, _ ->
            parseCommands(f, requested)
        } ?: error("Required command discovery")
        fun supports(service: Int, command: Int) = command in commands[service].orEmpty()
        val expand = if (supports(1, 0x37))
            query(1, 0x37, fields(1 to byteArrayOf()), "Expanded capability", false) { f, _ ->
                requireNotNull(HuaweiTlv.one(f, 1)).also { require(it.size <= 1024) }.copyOf()
            } else null
        fun bit(n: Int) = expand?.let { data ->
            data.getOrNull(n / 8)?.let { (it.toInt() and (1 shl (n % 8))) != 0 } ?: false
        } ?: false

        if (supports(0x1a, 5) && supports(0x1a, 6)) {
            // Empty account only: owner explicitly requested vendor-account-free pairing.
            val body = if (bit(172)) fields(1 to byteArrayOf(0), 3 to byteArrayOf(1))
                else fields(1 to byteArrayOf(0))
            query(0x1a, 5, body, "Vendor-free account judgment", true) { f, _ ->
                val result = HuaweiTlv.one(f, 2)
                if (result != null) {
                    require(result.size == 1)
                    when (result[0].toInt() and 255) {
                        1 -> throw HuaweiAccountSelectionRequired()
                        0, 4 -> Unit
                        else -> throw IOException("Unsupported account judgment result")
                    }
                }
                require(result != null || HuaweiTlv.one(f, 0x7f) != null)
                true
            }
        }
        if (supports(1, 0x31)) query(1, 0x31,
            fields(*Array(6) { (it + 1) to byteArrayOf() }), "Settings capabilities", false) { _, _ -> true }

        if (supports(1, 0x30)) notes += "Agreement command advertised; not sent without explicit consent."
        val connectSupported = supports(1, 0x35)
        val connect = if (connectSupported) query(1, 0x35, fields(1 to byteArrayOf(1)),
            "ConnectStatus finalization", false) { _, encrypted -> encrypted } else null

        if (bit(109)) {
            // The reference marks this device setup command as one-way (no response expected).
            onStage("Device setup status")
            onMetadata("")
            transport.resetTrace()
            val setup = fields(1 to byteArrayOf(1), 2 to deviceName.toByteArray(Charsets.UTF_8),
                3 to byteArrayOf(0))
            transport.send(reads.requestInitialization(1, 0x3e, setup))
            reads.finishInitializationWithoutReply(1, 0x3e)
            transport.ignoreLateReply(1, 0x3e)
            notes += "Device setup sent (one-way)."
            query(1, 0x3d, fields(1 to byteArrayOf()), "Wear status", false) { _, _ -> true }
        }
        return Report(serviceFlags, connectSupported, connect != null, connect == true, notes.toList(), commands[7].orEmpty())
    }

    private fun <T : Any> query(service: Int, command: Int, body: ByteArray, label: String,
        required: Boolean, parse: (List<HuaweiTlv.Field>, Boolean) -> T): T? {
        onStage(label)
        onMetadata("")
        transport.resetTrace()
        transport.send(reads.requestInitialization(service, command, body))
        val response = try {
            transport.receive(command, service, if (required) 8000 else 3000)
        } catch (e: SocketTimeoutException) {
            if (required) throw e
            reads.finishInitializationWithoutReply(service, command)
            transport.ignoreLateReply(service, command)
            notes += "$label: no reply."
            return null
        }
        try {
            reads.receiveInitialization(response).use { reply ->
                val f = HuaweiTlv.decode(reply.payload)
                val status = HuaweiTlv.one(f, 0x7f)
                if (status != null) {
                    require(status.size == 4)
                    if (!status.contentEquals(SUCCESS)) {
                        if (required) throw IOException("$label rejected")
                        notes += "$label: device rejected command."
                        return null
                    }
                }
                return parse(f, reply.encrypted)
            }
        } finally { onMetadata(reads.lastMetadata) }
    }

    companion object {
        private val SERVICES = byteArrayOf(1, 7, 0x0a, 0x17, 0x19, 0x1a, 0x20, 0x24, 0x2c, 0x34)
        private val DEVICE_COMMANDS = byteArrayOf(0x30, 0x31, 0x35, 0x37)
        private val SUCCESS = byteArrayOf(0, 1, 0x86.toByte(), 0xa0.toByte())

        fun allowed(service: Int, command: Int) =
            (service == 1 && command in setOf(2, 3, 0x31, 0x35, 0x37, 0x3d, 0x3e)) ||
                (service == 0x1a && command == 5) || (service == 7 && command in setOf(3, 10, 11))

        private fun fields(vararg values: Pair<Int, ByteArray>) =
            HuaweiTlv.encode(*values.map { HuaweiTlv.Field(it.first, it.second) }.toTypedArray())

        fun commandQuery(requested: Map<Int, ByteArray>): ByteArray {
            val nested = requested.flatMap { (service, commands) ->
                listOf(HuaweiTlv.Field(2, byteArrayOf(service.toByte())), HuaweiTlv.Field(3, commands))
            }
            return fields(0x81 to HuaweiTlv.encode(*nested.toTypedArray()))
        }

        fun parseCommands(f: List<HuaweiTlv.Field>, requested: Map<Int, ByteArray>): Map<Int, Set<Int>> {
            val nested = HuaweiTlv.decode(requireNotNull(HuaweiTlv.one(f, 0x81)))
            require(nested.size % 2 == 0)
            val result = mutableMapOf<Int, Set<Int>>()
            for (pair in nested.chunked(2)) {
                require(pair[0].tag == 2 && pair[0].value.size == 1 && pair[1].tag == 4)
                val service = pair[0].value[0].toInt() and 255
                val candidates = requireNotNull(requested[service])
                require(service !in result)
                val flags = pair[1].value
                require(flags.size <= candidates.size && flags.all { it.toInt() in 0..1 })
                result[service] = candidates.filterIndexed { i, _ -> flags.getOrNull(i) == 1.toByte() }
                    .map { it.toInt() and 255 }.toSet()
            }
            require(1 in result)
            return result
        }
    }
}
