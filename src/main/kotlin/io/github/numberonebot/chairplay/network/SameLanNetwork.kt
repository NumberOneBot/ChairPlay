package io.github.numberonebot.chairplay.network

import com.shilapi.xcertplay.network.HotspotAddressPolicy
import com.shilapi.xcertplay.network.WirelessHotspotBackend
import com.shilapi.xcertplay.network.WirelessHotspotInfo
import com.shilapi.xcertplay.network.wifiBandLabel
import com.shilapi.xcertplay.network.wifiChannelToFrequencyMhz
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.net.InetAddress

data class SavedWifi(val ssid: String, val passphrase: String, val security: Iap2WirelessSecurity)

data class NicSnapshot(
    val displayName: String,
    val name: String,
    val addresses: List<InetAddress>,
    val up: Boolean,
)

/**
 * Describes the Wi-Fi network the PC is already on. WLAN API gives a channel number without a
 * band, so channels 1-14 are read as 2.4 GHz and 32-177 as 5 GHz; 6 GHz is not detected.
 */
fun hotspotInfoFor(
    connections: List<WlanConnection>,
    interfaces: List<NicSnapshot>,
    saved: SavedWifi,
): WirelessHotspotInfo {
    val connection = connections.firstOrNull()
        ?: error("This PC is not connected to Wi-Fi; connect it to the network in wifi.ssid (an Ethernet link is not enough)")
    check(connection.ssid == saved.ssid) {
        "This PC is on Wi-Fi \"${connection.ssid}\", but wifi.ssid is \"${saved.ssid}\""
    }
    val matches = interfaces.filter { it.up && it.displayName == connection.interfaceDescription }
    check(matches.size == 1) {
        "Wi-Fi adapter \"${connection.interfaceDescription}\" matches ${matches.size} network interfaces, not one"
    }
    val nic = matches.single()
    val addresses = nic.addresses.filterNot { it.isLoopbackAddress }
    val hostAddress = HotspotAddressPolicy.select(addresses)
        ?: error("Wi-Fi adapter \"${connection.interfaceDescription}\" has no usable IPv4 or link-local IPv6 address")
    val band = when (connection.channel) {
        in 1..14 -> 1
        in 32..177 -> 2
        else -> null
    }
    return WirelessHotspotInfo(
        ssid = saved.ssid,
        passphrase = saved.passphrase,
        security = saved.security,
        channel = connection.channel,
        frequencyMHz = wifiChannelToFrequencyMhz(connection.channel, band),
        bssid = null,
        interfaceName = nic.name,
        hostAddress = hostAddress,
        bandLabel = wifiBandLabel(band) ?: "unknown",
        backend = WirelessHotspotBackend.EXISTING_WIFI,
        hostAddresses = addresses,
        accessPointBssid = connection.bssid,
    )
}
