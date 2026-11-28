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
                stage = "Encrypted device readback"
                lastResponse = ""
                transport.resetTrace()
                HuaweiSessionReads(key, frameBudget).use { reads ->
                    for (command in listOf(HuaweiDeviceInfo.PRODUCT, HuaweiDeviceInfo.BATTERY)) {
                        transport.send(reads.request(command))
                        reads.receive(transport.receive(command))
                    }
                    requireNotNull(reads.readback())
                }
            } finally {
                key.fill(0)
            }
            if (bound != null || pending) {
                stage = "Saving verified binding"
                saveVerified(reconnectCredential)
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
