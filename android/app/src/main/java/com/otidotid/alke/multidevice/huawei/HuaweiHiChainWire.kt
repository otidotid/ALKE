package com.otidotid.alke.multidevice.huawei

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Independent codec for the documented 01/28 ISO exchange fields.
 * Reference: public LPv2 interoperability observations and DeviceConfig.HiChain.
 * No credential acquisition, device mutation, or logging. Unknown terminal
 * responses remain unverified until a GT5 trace establishes the ACK contract.
 */
internal class HuaweiHiChainWire(
    private val operation: HuaweiIsoExchange.Operation,
    private val requestId: Long,
    private val identity: String,
    private val group: String,
    private val frameBudget: Int,
) {
    init {
        require(requestId > 0)
        require(identity.matches(Regex("[0-9a-fA-F]{32}")))
        require(group.matches(Regex("[0-9a-fA-F]{64}")))
        require(frameBudget in 64..32772)
    }

    private val bind = operation == HuaweiIsoExchange.Operation.BIND
    private val op = if (bind) 1 else 2
    private val selfHex = hex(identity.toByteArray(Charsets.US_ASCII))

    fun start(data: HuaweiIsoExchange.Start): List<ByteArray> {
        require(data.salt.size == 16 && data.seed.size == 32)
        val p = JSONObject().put("isoSalt", hex(data.salt))
            .put("seed", hex(data.seed)).put("peerAuthId", selfHex)
            .put("operationCode", op).put("peerUserType", 0)
        if (!bind) p.put("pkgName", "com.huawei.devicegroupmanage")
            .put("serviceType", group).put("keyLength", 32)
        return request(1, p)
    }

    fun proof(token: ByteArray): List<ByteArray> {
        require(token.size == 32)
        return request(2, JSONObject().put("peerAuthId", selfHex).put("token", hex(token)))
    }

    fun challenge(value: HuaweiIsoExchange.Encrypted): List<ByteArray> {
        require(bind && value.nonce.size == 12 && value.bytes.size == 32)
        return request(3, JSONObject().put("nonce", hex(value.nonce)).put("encData", hex(value.bytes)))
    }

    fun result(value: HuaweiIsoExchange.Encrypted): List<ByteArray> {
        require(value.nonce.size == 12 && value.bytes.size == 20)
        return request(4, JSONObject().put("nonce", hex(value.nonce))
            .put("encResult", hex(value.bytes)).put("operationCode", op))
    }

    private fun request(step: Int, payload: JSONObject): List<ByteArray> {
        payload.put("version", JSONObject().put("minVersion", "1.0.0").put("currentVersion", "2.0.16"))
        val envelope = JSONObject().put("authForm", 0).put("payload", payload)
            .put("groupAndModuleVersion", "2.0.1").put("message", if (bind) step else step or 16)
        if (bind) {
            envelope.put("requestId", requestId.toString()).put("groupId", group)
                .put("groupName", "health_group_name").put("groupOp", 2).put("groupType", 256)
                .put("peerDeviceId", identity).put("connDeviceId", identity)
                .put("appId", "com.huawei.health").put("ownerName", "")
        } else if (step <= 2) {
            envelope.put("isDeviceLevel", false)
        }
        val fields = ByteArrayOutputStream()
        append(fields, 1, envelope.toString().toByteArray(Charsets.UTF_8))
        append(fields, 2, byteArrayOf(op.toByte()))
        append(fields, 3, ByteBuffer.allocate(8).putLong(requestId).array())
        return HuaweiLinkFrames.encodeWithin(1, 0x28, fields.toByteArray(), frameBudget)
    }

    sealed class Reply {
        class Peer(val salt: ByteArray, val identity: ByteArray, val proof: ByteArray) : Reply()
        class Mac(val bytes: ByteArray) : Reply()
        class Token(val nonce: ByteArray, val bytes: ByteArray) : Reply()
        object TerminalUnverified : Reply()
    }

    /** Structural decoding only. Cryptographic verification is required separately. */
    fun decode(message: HuaweiLinkFrames.Message): Reply {
        require(message.service == 1 && message.command == 0x28)
        val fields = HuaweiTlv.decode(message.payload)
        require(HuaweiTlv.one(fields, 0x7f) == null) { "HiChain rejected" }
        HuaweiTlv.unsigned(fields, 2, 1)?.let { require(it == op) { "Operation mismatch" } }
        HuaweiTlv.one(fields, 3)?.let {
            require(it.size == 8 && ByteBuffer.wrap(it).long == requestId) { "Request mismatch" }
        }
        val type = requireNotNull(HuaweiTlv.unsigned(fields, 4, 1))
        if (type == 2) return Reply.TerminalUnverified
        require(type == 0) { "Unknown HiChain response type" }
        val raw = requireNotNull(HuaweiTlv.one(fields, 1))
        require(raw.size in 2..8192)
        val json = JSONObject(raw.toString(Charsets.UTF_8))
        if (json.has("requestId")) require(json.getString("requestId") == requestId.toString())
        val payload = json.getJSONObject("payload")
        for (part in listOf(json, payload)) {
            if (part.has("errorCode")) require(part.getInt("errorCode") == 0) { "HiChain rejected" }
        }
        val variants = listOf("isoSalt", "returnCodeMac", "encAuthToken").filter(payload::has)
        require(variants.size == 1) { "Ambiguous HiChain response" }
        return when (variants.single()) {
            "isoSalt" -> Reply.Peer(parseHex(payload.getString("isoSalt"), 16..16),
                parseHex(payload.getString("peerAuthId"), 1..128),
                parseHex(payload.getString("token"), 32..32))
            "returnCodeMac" -> Reply.Mac(parseHex(payload.getString("returnCodeMac"), 32..32))
            else -> {
                require(bind)
                val token = parseHex(payload.getString("encAuthToken"), 32..48)
                require(token.size == 32 || token.size == 48)
                Reply.Token(parseHex(payload.getString("nonce"), 12..12), token)
            }
        }
    }

    companion object {
        /** No raw payload, PIN, token, identity, nonce or key is exposed. */
        fun safeResponseMetadata(message: HuaweiLinkFrames.Message): String {
            require(message.service == 1 && message.command == 0x28)
            return runCatching {
                val fields = HuaweiTlv.decode(message.payload)
                val tags = fields.joinToString(", ") {
                    "%02X:%d".format(java.util.Locale.ROOT, it.tag, it.value.size)
                }
                val type = HuaweiTlv.unsigned(fields, 4, 1)
                val status = HuaweiTlv.one(fields, 0x7f)?.let {
                    if (it.size in 1..4) it.fold(0L) { n, b -> (n shl 8) or (b.toLong() and 255) } else null
                }
                val jsonError = runCatching {
                    val bytes = HuaweiTlv.one(fields, 1) ?: return@runCatching null
                    if (bytes.size !in 2..8192 || type != 0) return@runCatching null
                    val json = JSONObject(bytes.toString(Charsets.UTF_8))
                    val payload = json.optJSONObject("payload")
                    when {
                        payload?.has("errorCode") == true -> payload.getInt("errorCode")
                        json.has("errorCode") -> json.getInt("errorCode")
                        else -> null
                    }
                }.getOrNull()
                "HiChain response tags:lengths: $tags; type=${type ?: "unknown"}; status=${status ?: "none"}; errorCode=${jsonError ?: "none"}"
            }.getOrDefault("HiChain response malformed (payload withheld)")
        }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
        private fun parseHex(value: String, sizes: IntRange): ByteArray {
            require(value.length % 2 == 0 && value.length / 2 in sizes &&
                value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
            return ByteArray(value.length / 2) { value.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
        }
        private fun append(out: ByteArrayOutputStream, tag: Int, value: ByteArray) {
            out.write(tag)
            var n = value.size
            do {
                val low = n and 127
                n = n ushr 7
                out.write(low or if (n == 0) 0 else 128)
            } while (n != 0)
            out.write(value)
        }
    }
}
