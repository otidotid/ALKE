package com.otidotid.alke.multidevice.huawei

/**
 * Read-only LPv2 command facts from DeviceConfig.ProductInfo/BatteryLevel/
 * SupportedServices. No device settings or account modifications.
 * These parsers accept plaintext only AFTER the session layer decrypts it.
 */
internal object HuaweiDeviceInfo {
    const val SERVICE = 1
    const val PRODUCT = 7
    const val BATTERY = 8
    const val SERVICES = 2

    data class Product(val model: String, val firmware: String)

    fun query(command: Int): ByteArray = when (command) {
        PRODUCT -> byteArrayOf(7, 0, 10, 0)
        BATTERY -> byteArrayOf(1, 0)
        // Only advertise service IDs needed by ALKE; not a claim of device support.
        SERVICES -> byteArrayOf(1, 3, 1, 7, 0x24)
        else -> error("Unsupported Huawei read command")
    }

    private fun fields(message: HuaweiLinkFrames.Message, expected: Int): List<HuaweiTlv.Field> {
        require(message.service == SERVICE && message.command == expected)
        return HuaweiTlv.decode(message.payload).also {
            require(HuaweiTlv.one(it, 0x7f) == null) { "Huawei command rejected" }
        }
    }

    fun product(message: HuaweiLinkFrames.Message): Product {
        val f = fields(message, PRODUCT)
        fun label(tag: Int): String {
            val raw = requireNotNull(HuaweiTlv.one(f, tag))
            require(raw.size in 1..128)
            val value = raw.toString(Charsets.UTF_8).trim()
            require(value.isNotEmpty() && value.all { it.code >= 32 && it.code != 127 && it != '\uFFFD' })
            return value
        }
        return Product(label(10), label(7))
    }

    fun battery(message: HuaweiLinkFrames.Message): Int {
        val f = fields(message, BATTERY)
        return requireNotNull(HuaweiTlv.unsigned(f, 1, 1)).also { require(it in 0..100) }
    }

    fun services(message: HuaweiLinkFrames.Message): Set<Int> {
        val f = fields(message, SERVICES)
        val raw = requireNotNull(HuaweiTlv.one(f, 2))
        require(raw.size <= 255)
        return raw.map { it.toInt() and 255 }.filter { it != 0 }.toSet()
    }
}
