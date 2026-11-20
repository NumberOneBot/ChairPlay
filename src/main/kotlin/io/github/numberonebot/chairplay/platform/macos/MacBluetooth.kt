package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.Bluetooth
import io.github.numberonebot.chairplay.platform.PairedDevice
import io.github.numberonebot.chairplay.platform.RfcommLink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

internal object MacBluetooth : Bluetooth {

    interface NativeTransport {
        fun read(handle: Long, buf: ByteArray, len: Int): Int
        fun write(handle: Long, buf: ByteArray, len: Int): Int
        fun close(handle: Long)
    }

    private object RealTransport : NativeTransport {
        override fun read(handle: Long, buf: ByteArray, len: Int): Int {
            val errBuf = ByteArray(256)
            val res = MacNative.INSTANCE.cp_bt_read(handle, buf, len, errBuf, errBuf.size)
            if (res < 0) {
                val msg = String(errBuf).trimEnd('\u0000').ifEmpty { "Bluetooth read error ($res)" }
                throw IOException(msg)
            }
            return res
        }

        override fun write(handle: Long, buf: ByteArray, len: Int): Int {
            val errBuf = ByteArray(256)
            val res = MacNative.INSTANCE.cp_bt_write(handle, buf, len, errBuf, errBuf.size)
            if (res < 0) {
                val msg = String(errBuf).trimEnd('\u0000').ifEmpty { "Bluetooth write error ($res)" }
                throw IOException(msg)
            }
            return res
        }

        override fun close(handle: Long) {
            MacNative.INSTANCE.cp_bt_close(handle)
        }
    }

    override fun radioAddress(): String {
        val outBuf = ByteArray(64)
        val errBuf = ByteArray(256)
        val res = MacNative.INSTANCE.cp_bt_radio_address(outBuf, outBuf.size, errBuf, errBuf.size)
        if (res != 0) {
            val msg = String(errBuf).trimEnd('\u0000').ifEmpty { "Failed to read Bluetooth radio address" }
            throw IOException(msg)
        }
        return normalizeAddress(String(outBuf).trimEnd('\u0000'))
    }

    override fun pairedDevices(): List<PairedDevice> {
        val maxDevices = 64
        val dev = MacNative.CpBtDevice()
        val devs = dev.toArray(maxDevices) as Array<MacNative.CpBtDevice>
        val errBuf = ByteArray(256)
        val count = MacNative.INSTANCE.cp_bt_paired_devices(devs[0], maxDevices, errBuf, errBuf.size)
        if (count < 0) return emptyList()
        val list = mutableListOf<PairedDevice>()
        for (i in 0 until count) {
            val d = devs[i]
            val name = String(d.name).trimEnd('\u0000')
            val addr = normalizeAddress(String(d.address).trimEnd('\u0000'))
            list.add(PairedDevice(name, addr, d.classOfDevice))
        }
        return list
    }

    override fun connectRfcomm(address: String, service: UUID): RfcommLink {
        val norm = normalizeAddress(address)
        val uuidBytes = uuidToBytes(service)
        val errBuf = ByteArray(256)
        val handle = MacNative.INSTANCE.cp_bt_connect_rfcomm(norm, uuidBytes, errBuf, errBuf.size)
        if (handle == 0L) {
            val msg = String(errBuf).trimEnd('\u0000').ifEmpty { "Failed to connect RFCOMM to $address" }
            throw IOException(msg)
        }
        return createRfcommLink(handle, RealTransport, mtu = 1024)
    }

    internal fun normalizeAddress(address: String): String {
        val parts = address.split(':', '-')
        if (parts.size == 6) {
            return parts.joinToString(":") { "%02X".format(it.toInt(16)) }
        }
        return address.uppercase()
    }

    internal fun uuidToBytes(uuid: UUID): ByteArray {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val buf = ByteArray(16)
        for (i in 0..7) {
            buf[i] = ((msb ushr (56 - i * 8)) and 0xFF).toByte()
        }
        for (i in 0..7) {
            buf[8 + i] = ((lsb ushr (56 - i * 8)) and 0xFF).toByte()
        }
        return buf
    }

    internal fun createRfcommLink(
        handle: Long,
        transport: NativeTransport,
        mtu: Int = 1024,
    ): RfcommLink = object : RfcommLink {
        private var closed = false

        override val input: InputStream = object : InputStream() {
            override fun read(): Int {
                val b = ByteArray(1)
                val n = read(b, 0, 1)
                return if (n <= 0) -1 else b[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (closed) return -1
                val chunk = ByteArray(len)
                val n = transport.read(handle, chunk, len)
                if (n == 0) return -1 // EOF
                if (n < 0) return -1
                System.arraycopy(chunk, 0, b, off, n)
                return n
            }
        }

        override val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) {
                write(byteArrayOf(b.toByte()), 0, 1)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (closed) throw IOException("RFCOMM stream closed")
                var sent = 0
                while (sent < len) {
                    val chunkSize = minOf(len - sent, mtu)
                    val chunk = ByteArray(chunkSize)
                    System.arraycopy(b, off + sent, chunk, 0, chunkSize)
                    val n = transport.write(handle, chunk, chunkSize)
                    if (n <= 0) throw IOException("RFCOMM write failed ($n)")
                    sent += n
                }
            }
        }

        override fun close() {
            if (!closed) {
                closed = true
                transport.close(handle)
            }
        }
    }
}
