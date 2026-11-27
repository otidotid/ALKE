package com.otidotid.alke.multidevice.huawei

/**
 * Wire-field decoder for LPv2. Independent implementation of documented tag/length
 * facts. Supports little-endian base-128 lengths and retains repeated fields.
 * Treat container values as opaque until a command-specific parser examines them.
 */
internal object HuaweiTlv {
    class Field(val tag: Int, val value: ByteArray)

    fun decode(payload: ByteArray): List<Field> {
        require(payload.size <= HuaweiLinkFrames.MAX_BODY)
        val fields = ArrayList<Field>()
        var pos = 0
        while (pos < payload.size) {
            val tag = payload[pos++].toInt() and 255
            var length = 0
            var shift = 0
            while (true) {
                require(pos < payload.size && shift <= 21) { "Invalid TLV length" }
                val octet = payload[pos++].toInt() and 255
                length = length or ((octet and 127) shl shift)
                if (octet and 128 == 0) break
                shift += 7
            }
            require(length <= payload.size - pos) { "Truncated TLV value" }
            fields.add(Field(tag, payload.copyOfRange(pos, pos + length)))
            pos += length
        }
        return fields
    }

    fun one(fields: List<Field>, tag: Int): ByteArray? {
        val matches = fields.filter { it.tag == tag }
        require(matches.size <= 1) { "Ambiguous TLV tag" }
        return matches.singleOrNull()?.value
    }

    fun unsigned(fields: List<Field>, tag: Int, size: Int): Int? {
        val value = one(fields, tag) ?: return null
        require(value.size == size && size in 1..2)
        return value.fold(0) { result, octet -> (result shl 8) or (octet.toInt() and 255) }
    }
}
