package com.otidotid.alke.multidevice.huawei

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID
import java.net.SocketTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
) : HuaweiHiChainTransport.Link {
    private val closed = AtomicBoolean(false)
    private val guard = Any()
    @Volatile private var socket: BluetoothSocket? = null
    private val reader = Executors.newSingleThreadExecutor()
    // Accessed only by the serialized protocol worker. A deadline never starts a second socket read.
    private var inFlightRead: Future<ByteArray>? = null

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
    override fun read(buffer: ByteArray): Int {
        require(buffer.isNotEmpty())
        return connectedSocket().inputStream.read(buffer)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, timeoutMillis: Long): Int {
        require(buffer.isNotEmpty() && timeoutMillis > 0)
        val future = inFlightRead ?: reader.submit(Callable {
            val chunk = ByteArray(buffer.size)
            val n = connectedSocket().inputStream.read(chunk)
            if (n <= 0) byteArrayOf() else chunk.copyOf(n)
        }).also { inFlightRead = it }
        val chunk = try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // Keep the pending reader. The next request can drain a late response.
            throw SocketTimeoutException("Huawei request deadline")
        } catch (e: ExecutionException) {
            inFlightRead = null
            throw IOException("Huawei socket read failed", e.cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Huawei socket read interrupted", e)
        } catch (e: java.util.concurrent.CancellationException) {
            throw IOException("Huawei socket closed", e)
        }
        inFlightRead = null
        if (chunk.isEmpty()) return -1
        require(chunk.size <= buffer.size)
        chunk.copyInto(buffer)
        return chunk.size
    }

    @Throws(IOException::class)
    @Synchronized
    override fun write(frame: ByteArray) {
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
            reader.shutdownNow()
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
