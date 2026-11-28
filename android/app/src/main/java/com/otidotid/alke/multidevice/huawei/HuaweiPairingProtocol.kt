package com.otidotid.alke.multidevice.huawei

/** Runs the GT 5 handshake and verifies the resulting session on one RFCOMM connection. */
internal class HuaweiPairingProtocol(private val transport: HuaweiHiChainTransport) {
    var stage = "Opening Bluetooth"
        private set
    var lastResponse = ""
        private set
    private var lastRequestId = 0L

    class BindingRequired : IllegalStateException()

    fun authenticate(
        identity: String,
        model: String,
        credential: ByteArray?,
        pending: Boolean,
        allowFirstBind: Boolean,
        savePending: (ByteArray) -> Unit,
        saveVerified: (ByteArray) -> Unit,
        deviceName: String = "Huawei Watch GT 5",
        onSession: ((HuaweiSessionReads.Readback, HuaweiFitnessSession) -> Unit)? = null,
    ): HuaweiSessionReads.Readback {
        require(!pending || credential != null)
        stage = "Link parameters"
        transport.send(listOf(HuaweiLinkNegotiation.request()))
        val parameters = HuaweiAuthCapabilities.parse(transport.receive(1))
        require(parameters.supportType == 4)
        require(parameters.authVersion in 1..3 && parameters.effectiveEncryptMethod in 0..1)
        parameters.mtu?.let(transport::setMtu)
        val frameBudget = HuaweiAuthCapabilities.hiChainFrameBudget(parameters)

        stage = "Device status"
        transport.send(listOf(HuaweiAuthCapabilities.statusRequest()))
        require(HuaweiAuthCapabilities.parseStatus(transport.receive(0x16)) in listOf(0, 1, 255))

        stage = "Security negotiation"
        transport.send(listOf(HuaweiSecurityNegotiation.request(parameters, identity, model)))
        val mode = HuaweiSecurityNegotiation.parse(transport.receive(0x33))
        require(mode != HuaweiSecurityNegotiation.AuthMode.UNSUPPORTED)
        val operation = HuaweiSecurityNegotiation.operation(mode, credential != null, allowFirstBind)
            ?: throw BindingRequired()

        val bound = if (operation == HuaweiIsoExchange.Operation.BIND) {
            stage = "First-bind credential"
            transport.send(listOf(HuaweiFirstBind.request(parameters, allowFirstBind)))
            val pin = HuaweiFirstBind.credential(parameters, transport.receive(HuaweiFirstBind.COMMAND))
            try {
                exchange(operation, pin, identity, frameBudget, savePending)
            } finally {
                pin.fill(0)
            }
        } else null

        try {
            val reconnectCredential = requireNotNull(bound ?: credential)
            val key = exchange(HuaweiIsoExchange.Operation.RECONNECT, reconnectCredential, identity, frameBudget)
            val info = try {
                HuaweiSessionReads(key, frameBudget).use { reads ->
                    fun read(command: Int, label: String) {
                        stage = label
                        lastResponse = ""
                        transport.resetTrace()
                        transport.send(reads.request(command))
                        try {
                            reads.receive(transport.receive(command, timeoutMillis = if (command == HuaweiDeviceInfo.TIME) 3000 else 8000))
                        } finally {
                            lastResponse = reads.lastMetadata
                        }
                    }
                    read(HuaweiDeviceInfo.PRODUCT, "Encrypted ProductInfo")
                    try {
                        read(HuaweiDeviceInfo.TIME, "Watch clock initialization")
                    } catch (_: java.net.SocketTimeoutException) {
                        reads.skipClockTimeout()
                        transport.ignoreLateReply(1, HuaweiDeviceInfo.TIME)
                    }
                    try {
                        read(HuaweiDeviceInfo.BATTERY, "BatteryLevel readback")
                    } catch (_: java.net.SocketTimeoutException) {
                        reads.skipBatteryTimeout()
                        transport.ignoreLateReply(1, HuaweiDeviceInfo.BATTERY)
                    }
                    val init = HuaweiInitialization(reads, transport,
                        { stage = it }, { lastResponse = it }).run(deviceName)
                    val verified = requireNotNull(reads.readback()).copy(
                        connectStatusAcknowledged = init.connectReply,
                        connectStatusEncrypted = init.connectEncrypted,
                        initialization = init,
                    )
                    if (bound != null || pending) {
                        stage = "Saving verified binding"
                        saveVerified(reconnectCredential)
                    }
                    // Never expose the key or socket beyond the serialized owner.
                    onSession?.invoke(verified, HuaweiFitnessSession(reads, transport, init.fitnessCommands, init.workoutCommands))
                    verified
                }
            } finally {
                key.fill(0)
            }
            return info
        } finally {
            bound?.fill(0)
        }
    }

    private fun exchange(
        operation: HuaweiIsoExchange.Operation,
        credential: ByteArray,
        identity: String,
        frameBudget: Int,
        savePending: ((ByteArray) -> Unit)? = null,
    ): ByteArray {
        val label = if (operation == HuaweiIsoExchange.Operation.BIND) "First bind — confirm on watch"
            else "Reconnect proof"
        stage = label
        lastResponse = ""
        transport.resetTrace()
        lastRequestId = maxOf(System.currentTimeMillis(), lastRequestId + 1)
        HuaweiHiChainSession(operation, credential, identity, GROUP, lastRequestId, frameBudget,
            allowProvisionalReadback = true, savePendingBind = savePending).use { session ->
            transport.send(session.begin())
            while (session.phase != HuaweiHiChainSession.Phase.TERMINAL_UNVERIFIED) {
                stage = "$label (${session.phase})"
                val reply = transport.receive(0x28)
                lastResponse = HuaweiHiChainWire.safeResponseMetadata(reply)
                transport.send(session.receive(reply))
            }
            return session.takeProvisionalForVerification()
        }
    }

    private companion object {
        const val GROUP = "7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989"
    }
}
