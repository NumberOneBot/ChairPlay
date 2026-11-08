package io.github.numberonebot.chairplay.network

import com.sun.jna.platform.win32.IPHlpAPI
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/** One interface as GetIfEntry2 reports it: its index and GUID, its name in Settings, and what Windows says it is. */
internal data class InterfaceRow(
    val index: Int,
    val guid: String,
    val alias: String,
    val type: Int,
    val up: Boolean,
    val connectorPresent: Boolean,
    val filter: Boolean,
)

/**
 * The adapters Windows counts as physical (Get-NetAdapter -Physical: a connector present, as a Wi-Fi card or a network
 * port has and Hyper-V's or WSL's vEthernet has not), up, and not one of the filter layers stacked on an adapter; by
 * GUID, lowercase and without braces. Windows owns the physical/virtual distinction: never guess from adapter names.
 */
internal fun physicalAdapters(rows: List<InterfaceRow>): List<InterfaceRow> =
    rows.filter { it.up && it.connectorPresent && !it.filter }
        .map { it.copy(guid = it.guid.trim('{', '}').lowercase()) }
        .distinctBy { it.guid }

/** The addresses of the physical adapters among [rows], as [addresses] gives them by interface index. */
internal fun physicalAddresses(rows: List<InterfaceRow>, addresses: (Int) -> List<InetAddress>): List<InetAddress> =
    physicalAdapters(rows).flatMap { addresses(it.index) }

/** Every address of this PC's physical adapters that are up; none when Windows does not answer. */
internal fun physicalInterfaceAddresses(): List<InetAddress> = try {
    val interfaces = NetworkInterface.networkInterfaces().toList().filter { it.isUp }.associateBy { it.index }
    physicalAddresses(interfaces.keys.mapNotNull(::interfaceRow)) { interfaces[it]?.inetAddresses()?.toList().orEmpty() }
} catch (_: SocketException) {
    emptyList()
}

private const val IF_OPER_STATUS_UP = 1
private const val FILTER_INTERFACE = 0x02
private const val CONNECTOR_PRESENT = 0x04

/** The interface with this index as Windows' IP Helper reports it; null when it is gone. */
internal fun interfaceRow(index: Int): InterfaceRow? {
    val row = IPHlpAPI.MIB_IF_ROW2()
    row.InterfaceIndex = index
    if (IPHlpAPI.INSTANCE.GetIfEntry2(row) != 0) return null
    val flags = row.InterfaceAndOperStatusFlags.toInt()
    return InterfaceRow(
        index = index,
        guid = row.InterfaceGuid.toGuidString(),
        alias = String(row.Alias).substringBefore('\u0000'),
        type = row.Type,
        up = row.OperStatus == IF_OPER_STATUS_UP,
        connectorPresent = flags and CONNECTOR_PRESENT != 0,
        filter = flags and FILTER_INTERFACE != 0,
    )
}
