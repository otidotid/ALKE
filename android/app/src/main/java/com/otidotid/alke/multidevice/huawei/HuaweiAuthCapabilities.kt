package com.otidotid.alke.multidevice.huawei

/**
 * LinkParams metadata for newer devices, based on documented field IDs in the
 * Huawei DeviceConfig protocol. Unknown values stay unknown, never default to
 * legacy authentication. This is capability discovery, not authentication.
 */
internal object HuaweiAuthCapabilities {
    data class Parameters(
        val protocolVersion: Int?,
        val sliceSize: Int?,
        val mtu: Int?,
        val authVersion: Int?,
        val supportType: Int?,
        val authAlgorithm: Int?,
        val bondState: Int?,
        val encryptMethod: Int?,
    )

    fun parse(message: HuaweiLinkFrames.Message): Parameters {
        require(message.service == 1 && message.command == 1)
        val fields = HuaweiTlv.decode(message.payload)
        val auth = HuaweiTlv.one(fields, 5)
        require(auth == null || auth.size >= 18) { "Incomplete authentication parameters" }
        return Parameters(
            protocolVersion = HuaweiTlv.unsigned(fields, 1, 1),
            sliceSize = HuaweiTlv.unsigned(fields, 2, 2),
            mtu = HuaweiTlv.unsigned(fields, 3, 2),
            authVersion = auth?.get(1)?.toInt()?.and(255),
            supportType = HuaweiTlv.unsigned(fields, 7, 1),
            authAlgorithm = HuaweiTlv.unsigned(fields, 8, 1),
            bondState = HuaweiTlv.unsigned(fields, 9, 1),
            encryptMethod = HuaweiTlv.unsigned(fields, 12, 1),
        )
    }

    /** Status query only; never sends the alternative status-notification command. */
    fun statusRequest(): ByteArray =
        HuaweiLinkFrames.encode(1, 0x16, byteArrayOf(1, 0))

    fun parseStatus(message: HuaweiLinkFrames.Message): Int? {
        require(message.service == 1 && message.command == 0x16)
        return HuaweiTlv.unsigned(HuaweiTlv.decode(message.payload), 1, 1)
    }
}
