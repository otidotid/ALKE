package com.otidotid.alke.multidevice.huawei

/**
 * Read-only LPv2 command facts from DeviceConfig.ProductInfo/BatteryLevel/
 * SupportedServices. No device settings or account modifications.
 * The session layer requires GCM ProductInfo before any command-scoped
 * plaintext initialization response is accepted.
 */
internal object HuaweiDeviceInfo {
    const val SERVICE = 1
    const val TIME = 5
    const val PRODUCT = 7
    const val BATTERY = 8
    const val SERVICES = 2
    const val COMMANDS = 3
    const val CONNECT_STATUS = 0x35
    private val queriedServices = byteArrayOf(1, 7, 0x24)

    data class Product(val model: String, val firmware: String)

    fun query(command: Int): ByteArray = when (command) {
        TIME -> {
            val now = System.currentTimeMillis()
            val offset = java.util.TimeZone.getDefault().getOffset(now) / 1000
            val hours = if (offset < 0) (-offset / 3600) or 128 else offset / 3600
            val clock = java.nio.ByteBuffer.allocate(4).putInt((now / 1000).toInt()).array()
            HuaweiTlv.encode(HuaweiTlv.Field(1, clock),
                HuaweiTlv.Field(2, byteArrayOf(hours.toByte(), (offset / 60 % 60).toByte())))
        }
        PRODUCT -> byteArrayOf(7, 0, 10, 0)
        BATTERY -> byteArrayOf(1, 0)
        COMMANDS -> byteArrayOf(0x81.toByte(), 6, 2, 1, 1, 3, 1, CONNECT_STATUS.toByte())
        CONNECT_STATUS -> byteArrayOf(1, 1, 1)
        // Only advertise service IDs needed by ALKE; not a claim of device support.
        SERVICES -> byteArrayOf(1, queriedServices.size.toByte()) + queriedServices
        else -> error("Unsupported Huawei read command")
    }

    private fun fields(message: HuaweiLinkFrames.Message, expected: Int): List<HuaweiTlv.Field> {
        require(message.service == SERVICE && message.command == expected)
        return HuaweiTlv.decode(message.payload).also {
            require(successOrAbsent(it)) { "Huawei command rejected" }
        }
    }

    fun product(message: HuaweiLinkFrames.Message): Product {
        val f = fields(message, PRODUCT)
        fun label(tag: Int): String {
            val raw = requireNotNull(HuaweiTlv.one(f, tag))
            require(raw.size in 1..128)
            val value = raw.toString(Charsets.UTF_8)
                .trimEnd { it == '\u0000' || it.isWhitespace() }
                .trimStart()
            require(value.isNotEmpty() && value.all { it.code >= 32 && it.code != 127 && it != '\uFFFD' })
            return value
        }
        return Product(label(10), label(7))
    }

    fun battery(message: HuaweiLinkFrames.Message): Int {
        val f = fields(message, BATTERY)
        // Huawei's battery response reads the first byte and may carry a multi-battery array.
        val primary = requireNotNull(HuaweiTlv.one(f, 1))
        require(primary.isNotEmpty())
        val levels = HuaweiTlv.one(f, 2)?.takeIf { it.isNotEmpty() } ?: primary
        return (levels[0].toInt() and 255).also { require(it in 0..100) }
    }

    private fun successOrAbsent(fields: List<HuaweiTlv.Field>): Boolean =
        HuaweiTlv.one(fields, 0x7f)?.contentEquals(byteArrayOf(0, 1, 0x86.toByte(), 0xa0.toByte())) ?: true

    fun timeAcknowledged(message: HuaweiLinkFrames.Message) {
        val f = fields(message, TIME)
        val time = HuaweiTlv.one(f, 1)
        require(time?.size == 4 || HuaweiTlv.one(f, 0x7f) != null) { "Invalid clock response" }
    }

    fun supportsConnectStatus(message: HuaweiLinkFrames.Message): Boolean {
        val f = fields(message, COMMANDS)
        val nested = HuaweiTlv.decode(requireNotNull(HuaweiTlv.one(f, 0x81)))
        require(HuaweiTlv.one(nested, 2)?.contentEquals(byteArrayOf(1)) == true)
        val flags = requireNotNull(HuaweiTlv.one(nested, 4))
        require(flags.size == 1 && (flags[0].toInt() and 255) in 0..1)
        return flags[0] == 1.toByte()
    }

    fun connectAcknowledged(message: HuaweiLinkFrames.Message) {
        val f = fields(message, CONNECT_STATUS)
        require(HuaweiTlv.one(f, 0x7f)?.contentEquals(byteArrayOf(0, 1, 0x86.toByte(), 0xa0.toByte())) == true) {
            "Connect status acknowledgement absent"
        }
    }

    fun services(message: HuaweiLinkFrames.Message): Set<Int> {
        val f = fields(message, SERVICES)
        val flags = requireNotNull(HuaweiTlv.one(f, 2))
        require(flags.size <= queriedServices.size)
        // Each reply byte indicates support for the service at that request index.
        return buildSet {
            flags.forEachIndexed { index, flag ->
                if (flag == 1.toByte()) add(queriedServices[index].toInt() and 255)
            }
        }
    }
}
