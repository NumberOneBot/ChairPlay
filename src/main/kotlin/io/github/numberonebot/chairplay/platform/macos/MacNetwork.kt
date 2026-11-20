package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WifiHiddenException
import io.github.numberonebot.chairplay.network.WlanConnection
import io.github.numberonebot.chairplay.platform.PcNetwork
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.HexFormat

/** macOS network information via CoreWLAN (MacNative) and NetworkInterface. */
internal class MacNetwork(
    private val wifiProvider: () -> WifiDetails? = ::defaultWifiDetails,
    private val nicProvider: () -> List<NetworkNic> = ::defaultNics,
) : PcNetwork {

    data class WifiDetails(
        val interfaceName: String,
        val ssid: String?,
        val bssid: ByteArray?,
        val channel: Int,
        val powerOn: Boolean,
        val serviceActive: Boolean,
        val wifiHidden: Boolean,
    )

    data class NetworkNic(
        val name: String,
        val isUp: Boolean,
        val isLoopback: Boolean,
        val isVirtual: Boolean,
        val addresses: List<InetAddress> = emptyList(),
    )

    override fun wifiConnections(): List<WlanConnection> {
        val info = wifiProvider() ?: return emptyList()
        if (info.wifiHidden) {
            throw WifiHiddenException()
        }
        val ssid = info.ssid ?: return emptyList()
        val bssid = info.bssid ?: ByteArray(0)
        return listOf(WlanConnection(info.interfaceName, ssid, bssid, info.channel))
    }

    override fun ethernetAdapters(): List<EthernetAdapter> {
        val activeWifiInf = wifiProvider()?.interfaceName
        val physicalNics = filterPhysicalInterfaces(nicProvider())
        return physicalNics.filter { it.name != activeWifiInf }.mapNotNull { nic ->
            val ipv4s = nic.addresses.filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
                .map { it.hostAddress }
                .sorted()
            if (ipv4s.isEmpty()) null
            else EthernetAdapter(id = nic.name, label = nic.name, interfaceName = nic.name, addresses = ipv4s)
        }.sortedBy { it.id }
    }

    override fun physicalAddresses(): List<InetAddress> {
        return filterPhysicalInterfaces(nicProvider()).flatMap { it.addresses }
    }
}

internal fun filterPhysicalInterfaces(nics: List<MacNetwork.NetworkNic>): List<MacNetwork.NetworkNic> {
    return nics.filter { nic ->
        nic.isUp && !nic.isLoopback && !nic.isVirtual && nic.name.startsWith("en")
    }
}

private fun defaultWifiDetails(): MacNetwork.WifiDetails? {
    val info = MacNative.CpWifiInfo()
    val errBuf = ByteArray(256)
    val res = MacNative.INSTANCE.cp_net_wifi_info(info, errBuf, errBuf.size)
    if (res != 0) {
        val msg = String(errBuf).trimEnd('\u0000').ifEmpty { "Failed to query CoreWLAN wifi info ($res)" }
        throw IOException(msg)
    }

    val ifName = String(info.interfaceName).trimEnd('\u0000')
    if (ifName.isEmpty()) return null

    val ssidRaw = String(info.ssid).trimEnd('\u0000')
    val ssid = ssidRaw.ifEmpty { null }

    val bssidRaw = String(info.bssid).trimEnd('\u0000')
    val bssid = parseBssid(bssidRaw)

    return MacNetwork.WifiDetails(
        interfaceName = ifName,
        ssid = ssid,
        bssid = bssid,
        channel = info.channel,
        powerOn = info.powerOn != 0,
        serviceActive = info.serviceActive != 0,
        wifiHidden = info.wifiHidden != 0,
    )
}

private fun parseBssid(bssidStr: String): ByteArray? {
    if (bssidStr.isBlank()) return null
    val clean = bssidStr.replace(":", "").replace("-", "")
    return runCatching { HexFormat.of().parseHex(clean) }.getOrNull()?.takeIf { it.size == 6 }
}

private fun defaultNics(): List<MacNetwork.NetworkNic> = try {
    NetworkInterface.networkInterfaces().toList().map { nic ->
        MacNetwork.NetworkNic(
            name = nic.name,
            isUp = nic.isUp,
            isLoopback = nic.isLoopback,
            isVirtual = nic.isVirtual,
            addresses = nic.inetAddresses().toList(),
        )
    }
} catch (_: SocketException) {
    emptyList()
}
