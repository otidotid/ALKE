package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer

/**
 * Independent bounded codec for Huawei 0x2c file-read messages.
 * Not a live session: callers must authenticate each response before decoding.
 * No plaintext fallback, vendor file deletion, or filesystem path handling.
 */
internal object HuaweiFileRead {
    const val SERVICE = 0x2c
    const val INIT = 1
    const val INFO = 3
    const val REQUEST = 4
    const val BLOCK = 5
    const val COMPLETE = 6
    const val MAX_FILE_BYTES = 16 * 1024 * 1024

    enum class Kind(val wire: Int) { SLEEP_STATE(14), SLEEP_DATA(15), GPS(17) }

    data class Manifest(val id: Int, val size: Int)
    data class Parameters(val id: Int, val burst: Int, val maxBlock: Int)
    data class Block(val id: Int, val offset: Int, val bytes: ByteArray)

    private fun u8(tag: Int, value: Int): HuaweiTlv.Field {
        require(value in 0..255)
        return HuaweiTlv.Field(tag, byteArrayOf(value.toByte()))
    }

    private fun u32(tag: Int, value: Long): HuaweiTlv.Field {
        require(value in 0..0xffffffffL)
        return HuaweiTlv.Field(tag, ByteBuffer.allocate(4).putInt(value.toInt()).array())
    }

    private fun read32(fields: List<HuaweiTlv.Field>, tag: Int): Long {
        val bytes = requireNotNull(HuaweiTlv.one(fields, tag))
        require(bytes.size == 4)
        return ByteBuffer.wrap(bytes).int.toLong() and 0xffffffffL
    }

    fun filename(kind: Kind, workout: Int? = null): String = when (kind) {
        Kind.SLEEP_STATE -> { require(workout == null); "sleep_state.bin" }
        Kind.SLEEP_DATA -> { require(workout == null); "sleep_data.bin" }
        Kind.GPS -> { require(workout != null && workout in 0..65535); "${workout}_gps.bin" }
    }

    fun initRequest(kind: Kind, start: Long, end: Long, workout: Int? = null): ByteArray {
        require(start in 0..0xffffffffL && end in start..0xffffffffL)
        return HuaweiTlv.encode(HuaweiTlv.Field(1, filename(kind, workout).toByteArray(Charsets.US_ASCII)),
            u8(2, kind.wire), u32(5, start), u32(6, end))
    }

    fun manifest(body: ByteArray, kind: Kind, workout: Int? = null): Manifest {
        val fields = HuaweiTlv.decode(body)
        val expected = filename(kind, workout).toByteArray(Charsets.US_ASCII)
        require(requireNotNull(HuaweiTlv.one(fields, 1)).contentEquals(expected))
        require(HuaweiTlv.unsigned(fields, 2, 1) == kind.wire)
        val id = requireNotNull(HuaweiTlv.unsigned(fields, 3, 1))
        val size = read32(fields, 4)
        require(size <= MAX_FILE_BYTES)
        return Manifest(id, size.toInt())
    }

    fun infoRequest(id: Int): ByteArray = HuaweiTlv.encode(u8(1, id),
        *Array(4) { HuaweiTlv.Field(it + 2, byteArrayOf()) })

    fun parameters(body: ByteArray, id: Int): Parameters {
        val fields = HuaweiTlv.decode(body)
        require(id in 0..255 && HuaweiTlv.unsigned(fields, 1, 1) == id)
        // This foundation deliberately rejects a firmware request for cleartext block requests.
        require(HuaweiTlv.unsigned(fields, 5, 1) == 0)
        val burst = requireNotNull(HuaweiTlv.unsigned(fields, 3, 2))
        val max = read32(fields, 4)
        require(burst in 1..HuaweiLinkFrames.MAX_BODY && max in 1..MAX_FILE_BYTES.toLong())
        return Parameters(id, burst, max.toInt())
    }

    fun blockRequest(id: Int, offset: Int, size: Int): ByteArray {
        require(offset >= 0 && size > 0 && offset.toLong() + size <= MAX_FILE_BYTES)
        return HuaweiTlv.encode(u8(1, id), u32(2, offset.toLong()), u32(3, size.toLong()))
    }

    /** Raw, already GCM-verified block body, not a TLV container. */
    fun block(body: ByteArray): Block {
        require(body.size in 7..HuaweiLinkFrames.MAX_BODY)
        val buffer = ByteBuffer.wrap(body)
        val id = buffer.get().toInt() and 255
        val offset = buffer.int
        require(offset in 0 until MAX_FILE_BYTES)
        buffer.get() // Reserved field; do not interpret.
        return Block(id, offset, ByteArray(buffer.remaining()).also { buffer.get(it) })
    }

    fun completeRequest(id: Int): ByteArray = HuaweiTlv.encode(u8(1, id), u8(2, 1))

    /**
     * Serial, exact-offset assembler. A complete file alone is exposed.
     * No retries on the same assembler after an invalid frame.
     */
    class Assembly(private val manifest: Manifest) : AutoCloseable {
        init { require(manifest.id in 0..255 && manifest.size in 0..MAX_FILE_BYTES) }
        private val storage = ByteArray(manifest.size)
        private var position = 0
        private var closed = false
        val nextOffset: Int get() = position

        fun append(block: Block) {
            check(!closed)
            if (block.id != manifest.id || block.offset != position ||
                block.bytes.isEmpty() || block.bytes.size > storage.size - position) {
                close()
                throw IllegalArgumentException("Invalid Huawei file block")
            }
            block.bytes.copyInto(storage, position)
            position += block.bytes.size
        }

        fun finish(): ByteArray {
            check(!closed && position == storage.size) { "Incomplete Huawei file" }
            return storage.copyOf().also { close() }
        }

        override fun close() {
            storage.fill(0)
            closed = true
        }
    }
}
