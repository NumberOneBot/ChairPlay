package io.github.numberonebot.chairplay.network

import com.shilapi.xcertplay.network.WirelessHotspotBackend
import com.shilapi.xcertplay.network.WirelessHotspotInfo
import com.shilapi.xcertplay.network.WirelessHotspotManager
import java.net.InetAddress
import java.net.NetworkInterface

/** Desktop counterpart of upstream's explicit interface profile (#1113). Android ExistingWifiManager cannot
 * enumerate Windows NICs. Remove this adapter when upstream offers a platform-neutral shared-LAN manager.
 * Wi-Fi remains preferred; Ethernet uses the iPhone's router credentials without inventing AP radio metadata.
 */
class DesktopNetwork(
    private val saved: SavedWifi,
    private val selected: String,
    private val wlan: () -> List<WlanConnection> = { runCatching { WlanApi.currentConnections() }.getOrDefault(emptyList()) },
    private val nics: () -> List<NicSnapshot> = {
        NetworkInterface.getNetworkInterfaces().toList().map {
            NicSnapshot(it.displayName, it.name, it.inetAddresses.toList(), it.isUp)
        }
    },
    private val ethernet: () -> List<EthernetAdapter> = EthernetAdapters::current,
) : WirelessHotspotManager {
    private var published: WirelessHotspotInfo? = null
    private var closed = false

    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        check(!closed && published == null) { "Network already started or closed" }
        return describe().also { published = it }
    }

    override fun validateReady() {
        check(!closed) { "Network is closed" }
        val before = checkNotNull(published) { "Network is not started" }
        val after = describe()
        check(before.interfaceName == after.interfaceName && before.hostAddresses == after.hostAddresses &&
            before.ssid == after.ssid && before.channel == after.channel &&
            before.accessPointBssid.contentEquals(after.accessPointBssid)) {
            "The network changed during startup; reconnect"
        }
    }

    override fun close() { closed = true }

    private fun describe(): WirelessHotspotInfo {
        val connections = wlan()
        if (connections.isNotEmpty()) return hotspotInfoFor(connections, nics(), saved)
        val adapters = ethernet()
        val adapter = checkNotNull(selectEthernet(adapters, selected)) {
            if (adapters.isEmpty()) "No Ethernet address is available; connect a network cable"
            else "Select an available Ethernet adapter in Connection settings"
        }
        val addresses = adapter.addresses.map(InetAddress::getByName)
        return WirelessHotspotInfo(
            ssid = saved.ssid, passphrase = saved.passphrase, security = saved.security,
            channel = 0, frequencyMHz = null, bssid = null, interfaceName = adapter.interfaceName,
            hostAddress = addresses.first(), hostAddresses = addresses, accessPointBssid = null,
            bandLabel = "Auto", backend = WirelessHotspotBackend.EXISTING_WIFI,
        )
    }
}
