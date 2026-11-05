package io.github.numberonebot.chairplay.network

import com.sun.jna.platform.win32.IPHlpAPI
import java.net.Inet4Address
import java.net.NetworkInterface

/** Stable Windows interface GUID; addresses are refreshed, never stored as the selection. */
data class EthernetAdapter(val id: String, val label: String, val interfaceName: String, val addresses: List<String>)

fun selectEthernet(adapters: List<EthernetAdapter>, selected: String): EthernetAdapter? =
    if (selected.isNotBlank()) adapters.singleOrNull { it.id == selected }
    else adapters.singleOrNull()

/** IF_TYPE_ETHERNET_CSMACD: wired Ethernet, as Windows types an interface. */
internal const val ETHERNET = 6

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
 * The wired adapters Windows counts as physical (Get-NetAdapter -Physical: a connector present), up, and not one of the
 * filter layers stacked on an adapter; by GUID, lowercase and without braces.
 */
internal fun physicalEthernet(rows: List<InterfaceRow>): List<InterfaceRow> =
    rows.filter { it.type == ETHERNET && it.up && it.connectorPresent && !it.filter }
        .map { it.copy(guid = it.guid.trim('{', '}').lowercase()) }
        .distinctBy { it.guid }

/** Windows owns the physical/virtual distinction. Do not guess from localized adapter names. */
object EthernetAdapters {
    private const val IF_OPER_STATUS_UP = 1
    private const val FILTER_INTERFACE = 0x02
    private const val CONNECTOR_PRESENT = 0x04

    fun current(): List<EthernetAdapter> {
        val interfaces = NetworkInterface.networkInterfaces().toList().associateBy { it.index }
        return physicalEthernet(interfaces.keys.mapNotNull(::row)).mapNotNull { adapter ->
            val nic = interfaces[adapter.index] ?: return@mapNotNull null
            val addresses = nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
                .map { it.hostAddress }.sorted()
            if (!nic.isUp || addresses.isEmpty()) null
            else EthernetAdapter(adapter.guid, adapter.alias, nic.name, addresses)
        }.sortedBy { it.id }
    }

    /** The interface with this index as Windows' IP Helper reports it; null when it is gone. */
    private fun row(index: Int): InterfaceRow? {
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
}
