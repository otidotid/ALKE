package com.otidotid.alke.multidevice.huawei

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct Huawei Classic Bluetooth transport, independent of the WHOOP client.
 * Call blocking connect/read/write on a dedicated IO worker. Close from another
 * thread cancels pending socket IO. A connected socket is NOT authenticated.
 *
 * Wire identifier reference: Gadgetbridge HuaweiConstants UUID_SERVICE_HUAWEI_SDP.
 * Independently implemented using Android's public BluetoothSocket API.
 */
@SuppressLint("MissingPermission")
internal class HuaweiRfcommTransport(
    private val device: BluetoothDevice,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val guard = Any()
    @Volatile private var socket: BluetoothSocket? = null

    @Throws(IOException::class)
    fun connect() {
        val pending = synchronized(guard) {
            check(!closed.get()) { "Transport closed" }
            check(socket == null) { "Connection already attempted" }
            device.createRfcommSocketToServiceRecord(SERVICE_UUID).also { socket = it }
        }
        try {
            pending.connect()
            if (closed.get()) throw IOException("Transport closed")
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    @Throws(IOException::class)
    fun read(buffer: ByteArray): Int {
        require(buffer.isNotEmpty())
        return connectedSocket().inputStream.read(buffer)
    }

    @Throws(IOException::class)
    @Synchronized
    fun write(frame: ByteArray) {
        require(frame.isNotEmpty())
        val output = connectedSocket().outputStream
        output.write(frame)
        output.flush()
    }

    private fun connectedSocket(): BluetoothSocket {
        val s = socket
        if (closed.get() || s == null || !s.isConnected) throw IOException("No Huawei transport")
        return s
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            val pending = synchronized(guard) { socket.also { socket = null } }
            try { pending?.close() } catch (_: IOException) { }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("82ff3820-8411-400c-b85a-55bdb32cf060")

        /** Identification only; advertising names do not authenticate a device. */
        fun isGt5Name(name: String?): Boolean {
            val normalized = name?.trim() ?: return false
            return Regex("^HUAWEI WATCH GT 5(?:[- ].+)?$", RegexOption.IGNORE_CASE)
                .matches(normalized) &&
                !Regex("^HUAWEI WATCH GT 5 PRO(?:[- ].+)?$", RegexOption.IGNORE_CASE)
                    .matches(normalized)
        }

        fun bondedCandidates(adapter: BluetoothAdapter): List<BluetoothDevice> =
            adapter.bondedDevices.filter { isGt5Name(it.name) }
    }
}
