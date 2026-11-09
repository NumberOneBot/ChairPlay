package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.network.EthernetAdapter
import java.net.Inet4Address
import java.net.NetworkInterface

/** IF_TYPE_ETHERNET_CSMACD: wired Ethernet, as Windows types an interface. */
internal const val ETHERNET = 6

/** The wired ones among the [physicalAdapters]. */
internal fun physicalEthernet(rows: List<InterfaceRow>): List<InterfaceRow> =
    physicalAdapters(rows).filter { it.type == ETHERNET }

/** Windows owns the physical/virtual distinction. Do not guess from localized adapter names. */
internal object EthernetAdapters {
    fun current(): List<EthernetAdapter> {
        val interfaces = NetworkInterface.networkInterfaces().toList().associateBy { it.index }
        return physicalEthernet(interfaces.keys.mapNotNull(::interfaceRow)).mapNotNull { adapter ->
            val nic = interfaces[adapter.index] ?: return@mapNotNull null
            val addresses = nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
                .map { it.hostAddress }.sorted()
            if (!nic.isUp || addresses.isEmpty()) null
            else EthernetAdapter(adapter.guid, adapter.alias, nic.name, addresses)
        }.sortedBy { it.id }
    }
}
