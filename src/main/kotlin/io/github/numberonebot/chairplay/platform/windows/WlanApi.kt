package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import io.github.numberonebot.chairplay.network.WifiHiddenException
import io.github.numberonebot.chairplay.network.WlanConnection

/** What an adapter says of its network: connected to one, connected with the network hidden, or on none. */
internal sealed interface AdapterWifi {
    data class Connected(val connection: WlanConnection) : AdapterWifi
    data object Hidden : AdapterWifi
    data object None : AdapterWifi

    companion object {
        private const val ERROR_ACCESS_DENIED = 5

        /** A connected adapter whose network query failed with [status]: hidden when refused, else read as none. */
        fun refused(status: Int): AdapterWifi = if (status == ERROR_ACCESS_DENIED) Hidden else None
    }
}

/** The connections the adapters show; none shown while one is hidden throws [WifiHiddenException]. */
internal fun connectionsOf(answers: List<AdapterWifi>): List<WlanConnection> {
    val shown = answers.filterIsInstance<AdapterWifi.Connected>().map { it.connection }
    if (shown.isEmpty() && AdapterWifi.Hidden in answers) throw WifiHiddenException()
    return shown
}

/**
 * The connected Wi-Fi adapters as the Native Wifi API reports them. `netsh wlan` prints localised
 * labels, this API does not. Structure layouts are read by offset (x64, natural alignment) from
 * wlanapi.h and windot11.h:
 *
 * - WLAN_INTERFACE_INFO_LIST: DWORD count at 0, DWORD index at 4, then WLAN_INTERFACE_INFO[count].
 * - WLAN_INTERFACE_INFO (532 bytes): GUID at 0, WCHAR[256] description at 16, enum state at 528.
 * - WLAN_CONNECTION_ATTRIBUTES: WLAN_ASSOCIATION_ATTRIBUTES starts at 520 (after two enums and the
 *   WCHAR[256] profile name); inside it DOT11_SSID {ULONG length, UCHAR[32]} is at 0 and the
 *   DOT11_MAC_ADDRESS at 40.
 */
internal object WlanApi {
    private interface Wlan : Library {
        fun WlanOpenHandle(clientVersion: Int, reserved: Pointer?, negotiated: IntByReference, handle: PointerByReference): Int
        fun WlanCloseHandle(handle: Pointer, reserved: Pointer?): Int
        fun WlanEnumInterfaces(handle: Pointer, reserved: Pointer?, list: PointerByReference): Int
        fun WlanQueryInterface(
            handle: Pointer,
            interfaceGuid: Pointer,
            opcode: Int,
            reserved: Pointer?,
            dataSize: IntByReference,
            data: PointerByReference,
            opcodeValueType: Pointer?,
        ): Int
        fun WlanFreeMemory(memory: Pointer)
    }

    private val wlan by lazy { Native.load("wlanapi", Wlan::class.java) }

    private const val CLIENT_VERSION_VISTA = 2
    private const val OPCODE_CURRENT_CONNECTION = 7
    private const val OPCODE_CHANNEL_NUMBER = 8
    private const val STATE_CONNECTED = 1
    private const val LIST_HEADER = 8
    private const val INTERFACE_INFO_SIZE = 532
    private const val INTERFACE_DESCRIPTION = 16L
    private const val INTERFACE_STATE = 528L
    private const val ASSOCIATION = 520L
    private const val SSID_LENGTH = ASSOCIATION
    private const val SSID_BYTES = ASSOCIATION + 4
    private const val BSSID = ASSOCIATION + 40

    fun currentConnections(): List<WlanConnection> {
        val handleRef = PointerByReference()
        check(wlan.WlanOpenHandle(CLIENT_VERSION_VISTA, null, IntByReference(), handleRef) == 0) { "WlanOpenHandle failed" }
        val handle = handleRef.value
        try {
            val listRef = PointerByReference()
            val status = wlan.WlanEnumInterfaces(handle, null, listRef)
            check(status == 0) { "WlanEnumInterfaces failed: $status" }
            val list = listRef.value
            try {
                return connectionsOf((0 until list.getInt(0)).map { index ->
                    connectionOf(handle, list.share((LIST_HEADER + index * INTERFACE_INFO_SIZE).toLong()))
                })
            } finally {
                wlan.WlanFreeMemory(list)
            }
        } finally {
            wlan.WlanCloseHandle(handle, null)
        }
    }

    private fun connectionOf(handle: Pointer, info: Pointer): AdapterWifi {
        if (info.getInt(INTERFACE_STATE) != STATE_CONNECTED) return AdapterWifi.None
        val description = info.getWideString(INTERFACE_DESCRIPTION)
        val attributesRef = PointerByReference()
        val status = wlan.WlanQueryInterface(handle, info, OPCODE_CURRENT_CONNECTION, null, IntByReference(), attributesRef, null)
        if (status != 0) return AdapterWifi.refused(status)
        val attributes = attributesRef.value
        val ssid: String
        val bssid: ByteArray
        try {
            val length = attributes.getInt(SSID_LENGTH).coerceIn(0, 32)
            ssid = String(attributes.getByteArray(SSID_BYTES, length), Charsets.UTF_8)
            bssid = attributes.getByteArray(BSSID, 6)
        } finally {
            wlan.WlanFreeMemory(attributes)
        }
        val channelData = query(handle, info, OPCODE_CHANNEL_NUMBER) ?: return AdapterWifi.None
        val channel = try {
            channelData.getInt(0)
        } finally {
            wlan.WlanFreeMemory(channelData)
        }
        return AdapterWifi.Connected(WlanConnection(description, ssid, bssid, channel))
    }

    /** The caller frees the returned block with WlanFreeMemory; null when the query fails. */
    private fun query(handle: Pointer, info: Pointer, opcode: Int): Pointer? {
        val data = PointerByReference()
        val status = wlan.WlanQueryInterface(handle, info, opcode, null, IntByReference(), data, null)
        return if (status == 0) data.value else null
    }
}
