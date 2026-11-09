package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import io.github.numberonebot.chairplay.platform.Bluetooth
import io.github.numberonebot.chairplay.platform.PairedDevice
import io.github.numberonebot.chairplay.platform.RfcommLink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Windows Bluetooth through BluetoothApis and Winsock, each loaded at its first call. Structure layouts are written by
 * offset: the radio and device structures follow natural x64 alignment, while SOCKADDR_BTH is packed
 * (ws2bth.h wraps it in pshpack1.h), which a JNA Structure would pad.
 */
internal object WindowsBluetooth : Bluetooth {
    private interface BluetoothApis : Library {
        fun BluetoothFindFirstRadio(params: Pointer, radio: PointerByReference): Pointer?
        fun BluetoothFindRadioClose(find: Pointer): Boolean
        fun BluetoothGetRadioInfo(radio: Pointer, info: Pointer): Int
        fun BluetoothFindFirstDevice(params: Pointer, info: Pointer): Pointer?
        fun BluetoothFindNextDevice(find: Pointer, info: Pointer): Boolean
        fun BluetoothFindDeviceClose(find: Pointer): Boolean
    }

    private interface Kernel32 : Library {
        fun CloseHandle(handle: Pointer): Boolean
    }

    private interface Winsock : Library {
        fun WSAStartup(version: Short, data: Pointer): Int
        fun WSAGetLastError(): Int
        fun socket(af: Int, type: Int, protocol: Int): Long
        fun connect(socket: Long, address: Pointer, length: Int): Int
        fun recv(socket: Long, buffer: ByteArray, length: Int, flags: Int): Int
        fun send(socket: Long, buffer: ByteArray, length: Int, flags: Int): Int
        fun closesocket(socket: Long): Int
    }

    private val bluetooth by lazy { Native.load("BluetoothApis", BluetoothApis::class.java) }
    private val kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
    private val winsock by lazy {
        Native.load("Ws2_32", Winsock::class.java).also {
            val result = it.WSAStartup(0x0202, Memory(512))
            check(result == 0) { "WSAStartup failed: $result" }
        }
    }

    private const val AF_BTH = 32
    private const val SOCK_STREAM = 1
    private const val BTHPROTO_RFCOMM = 3
    private const val INVALID_SOCKET = -1L
    private const val RADIO_INFO_SIZE = 520
    private const val DEVICE_INFO_SIZE = 560
    private const val DEVICE_SEARCH_SIZE = 40
    private const val SOCKADDR_BTH_SIZE = 30

    override fun radioAddress(): String {
        val params = Memory(4).apply { setInt(0, 4) }
        val radio = PointerByReference()
        val find = bluetooth.BluetoothFindFirstRadio(params, radio)
            ?: throw IOException("No Bluetooth radio is available")
        try {
            val info = Memory(RADIO_INFO_SIZE.toLong()).apply { clear(); setInt(0, RADIO_INFO_SIZE) }
            val result = bluetooth.BluetoothGetRadioInfo(radio.value, info)
            check(result == 0) { "BluetoothGetRadioInfo failed: $result" }
            return formatAddress(info.getLong(8))
        } finally {
            kernel32.CloseHandle(radio.value)
            bluetooth.BluetoothFindRadioClose(find)
        }
    }

    override fun pairedDevices(): List<PairedDevice> {
        val params = Memory(DEVICE_SEARCH_SIZE.toLong()).apply {
            clear()
            setInt(0, DEVICE_SEARCH_SIZE)
            setInt(4, 1) // fReturnAuthenticated
            setInt(8, 1) // fReturnRemembered
            setInt(16, 1) // fReturnConnected
        }
        val info = Memory(DEVICE_INFO_SIZE.toLong()).apply { clear(); setInt(0, DEVICE_INFO_SIZE) }
        val find = bluetooth.BluetoothFindFirstDevice(params, info) ?: return emptyList()
        val devices = mutableListOf<PairedDevice>()
        try {
            do {
                devices += PairedDevice(
                    name = info.getWideString(64),
                    address = formatAddress(info.getLong(8)),
                    classOfDevice = info.getInt(16),
                )
                info.clear()
                info.setInt(0, DEVICE_INFO_SIZE)
            } while (bluetooth.BluetoothFindNextDevice(find, info))
        } finally {
            bluetooth.BluetoothFindDeviceClose(find)
        }
        return devices
    }

    /** Port 0 makes Windows resolve the RFCOMM channel through SDP from the service UUID. */
    override fun connectRfcomm(address: String, service: UUID): RfcommLink {
        val socket = winsock.socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
        if (socket == INVALID_SOCKET) throw IOException("RFCOMM socket failed: WSA ${winsock.WSAGetLastError()}")
        val sockaddr = Memory(SOCKADDR_BTH_SIZE.toLong()).apply {
            clear()
            setShort(0, AF_BTH.toShort())
            setLong(2, parseAddress(address))
            write(10, guidBytes(service), 0, 16)
            setInt(26, 0)
        }
        if (winsock.connect(socket, sockaddr, SOCKADDR_BTH_SIZE) != 0) {
            val error = winsock.WSAGetLastError()
            winsock.closesocket(socket)
            throw IOException("RFCOMM connect to $address failed: WSA $error")
        }
        return RfcommSocket(socket)
    }

    private class RfcommSocket(private val socket: Long) : RfcommLink {
        override val input: InputStream = object : InputStream() {
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val buffer = ByteArray(len)
                val count = winsock.recv(socket, buffer, len, 0)
                if (count < 0) throw IOException("RFCOMM recv failed: WSA ${winsock.WSAGetLastError()}")
                if (count == 0) return -1
                System.arraycopy(buffer, 0, b, off, count)
                return count
            }
        }

        override val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                var sent = 0
                while (sent < len) {
                    val chunk = b.copyOfRange(off + sent, off + len)
                    val count = winsock.send(socket, chunk, chunk.size, 0)
                    if (count < 0) throw IOException("RFCOMM send failed: WSA ${winsock.WSAGetLastError()}")
                    sent += count
                }
            }
        }

        override fun close() {
            winsock.closesocket(socket)
        }
    }

    internal fun formatAddress(value: Long): String =
        (5 downTo 0).joinToString(":") { "%02X".format((value shr (it * 8)) and 0xff) }

    internal fun parseAddress(address: String): Long =
        address.split(":").fold(0L) { acc, part -> (acc shl 8) or part.toLong(16) }

    /** GUID stores Data1..Data3 little-endian and Data4 as bytes. */
    internal fun guidBytes(uuid: UUID): ByteArray {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val data1 = (msb ushr 32).toInt()
        val data2 = (msb ushr 16).toInt()
        val data3 = msb.toInt()
        return byteArrayOf(
            data1.toByte(), (data1 ushr 8).toByte(), (data1 ushr 16).toByte(), (data1 ushr 24).toByte(),
            data2.toByte(), (data2 ushr 8).toByte(),
            data3.toByte(), (data3 ushr 8).toByte(),
        ) + ByteArray(8) { (lsb ushr (56 - it * 8)).toByte() }
    }
}
