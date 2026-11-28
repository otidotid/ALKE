package com.otidotid.alke.multidevice.huawei

import java.io.ByteArrayOutputStream

/**
 * LPv2 encodes lengths as base-128 groups, most significant group first.
 * Treat container values as opaque until a command-specific parser examines them.
 */
internal object HuaweiTlv {
    class Field(val tag: Int, val value: ByteArray)

    fun encode(vararg fields: Field): ByteArray {
        val output = ByteArrayOutputStream()
        for (field in fields) {
            val length = field.value.size
            require(field.tag in 0..255 && length <= HuaweiLinkFrames.MAX_BODY)
            output.write(field.tag)
            var highestShift = 0
            while (length ushr (highestShift + 7) != 0) highestShift += 7
            for (shift in highestShift downTo 7 step 7) {
                output.write(((length ushr shift) and 127) or 128)
            }
            output.write(length and 127)
            output.write(field.value)
            require(output.size() <= HuaweiLinkFrames.MAX_BODY)
        }
        return output.toByteArray()
    }

    fun decode(payload: ByteArray): List<Field> {
        require(payload.size <= HuaweiLinkFrames.MAX_BODY)
        val fields = ArrayList<Field>()
        var pos = 0
        while (pos < payload.size) {
            val tag = payload[pos++].toInt() and 255
            var length = 0
            while (true) {
                require(pos < payload.size) { "Invalid TLV length" }
                val octet = payload[pos++].toInt() and 255
                require(length != 0 || octet != 128) { "Noncanonical TLV length" }
                length = (length shl 7) or (octet and 127)
                require(length <= payload.size - pos) { "Truncated TLV value" }
                if (octet and 128 == 0) break
            }
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
