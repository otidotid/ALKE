package com.otidotid.alke.multidevice.huawei

/** Bounded metadata only: never stores packet content, identifiers or credentials. */
internal class HuaweiHandshakeTrace {
    private var sentFrames = 0
    private var sentBytes = 0L
    private var readBytes = 0L
    private var lastFailure: String? = null
    private val other = ArrayList<String>()
    var unmatched = 0
        private set

    fun sent(size: Int) { sentFrames++; sentBytes += size }
    fun received(size: Int) { readBytes += size }
    fun failure(category: String) {
        require(category in setOf("socket read failed", "socket EOF", "read interrupted", "invalid LPv2 frame"))
        lastFailure = category
    }

    fun matches(message: HuaweiLinkFrames.Message, command: Int): Boolean {
        if (message.service == 1 && message.command == command) return true
        unmatched++
        if (other.size < 8) other.add("%02X/%02X (%d bytes)".format(
            java.util.Locale.ROOT, message.service, message.command, message.payload.size))
        return false
    }

    fun summary(): String = "Transport: sent=$sentFrames frames/$sentBytes bytes; received=$readBytes bytes; " +
        "unmatched=$unmatched; other=${other.joinToString(", ").ifEmpty { "none" }}; failure=${lastFailure ?: "none"}"
}
