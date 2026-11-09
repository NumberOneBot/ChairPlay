package io.github.numberonebot.chairplay.network

import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import io.github.numberonebot.chairplay.platform.FakeNetwork
import io.github.numberonebot.chairplay.platform.FakePlatform
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class DesktopNetworkTest {
    private val saved = SavedWifi("Router", "test-password", Iap2WirelessSecurity.WPA_WPA2)
    private fun adapter(id: String, ip: String) = EthernetAdapter(id, "Ethernet $id", "eth$id", listOf(ip))

    @Test fun `one adapter is automatic but multiple adapters require a selection`() {
        val first = adapter("a", "192.168.1.20")
        val second = adapter("b", "10.0.0.2")
        assertEquals(first, selectEthernet(listOf(first), ""))
        assertNull(selectEthernet(listOf(first, second), ""))
        assertEquals(second, selectEthernet(listOf(first, second), "b"))
        assertNull(selectEthernet(listOf(first), "missing"))
        assertNull(selectEthernet(emptyList(), ""))
    }

    @Test fun `adapter identity survives DHCP and does not fall back to another adapter`() {
        var adapters = listOf(adapter("a", "192.168.1.20"))
        val network = DesktopNetwork(saved, "a", { emptyList() }, { emptyList() }, { adapters })
        val info = network.start(1000)
        assertEquals("192.168.1.20", info.hostAddress?.hostAddress)
        assertEquals(saved.ssid, info.ssid)
        assertEquals(saved.passphrase, info.passphrase)
        assertNull(info.accessPointBssid)
        assertEquals(0, info.channel)
        adapters = listOf(adapter("a", "192.168.1.21"))
        assertThrows(IllegalStateException::class.java) { network.validateReady() }
        assertEquals("192.168.1.21", DesktopNetwork(saved, "a", { emptyList() }, { emptyList() }, { adapters })
            .start(1000).hostAddress?.hostAddress)
        adapters = listOf(adapter("b", "192.168.1.20"))
        assertThrows(IllegalStateException::class.java) { network.validateReady() }
    }

    // A Wi-Fi network Windows hides, with no cable either, fails as hidden rather than as no cable.
    @Test fun `a hidden Wi-Fi network without Ethernet fails as hidden`() {
        val hidden = DesktopNetwork(saved, "", { throw WifiHiddenException() }, { emptyList() }, { emptyList() })
        assertThrows(WifiHiddenException::class.java) { hidden.start(1000) }
        val cabled = DesktopNetwork(saved, "", { throw WifiHiddenException() }, { emptyList() }, { listOf(adapter("a", "192.168.1.20")) })
        assertEquals("192.168.1.20", cabled.start(1000).hostAddress?.hostAddress)
    }

    @Test fun `the platform's Wi-Fi wins over its cable`() {
        val platform = FakePlatform(network = FakeNetwork(
            wifi = { listOf(WlanConnection("Wi-Fi", "Router", byteArrayOf(1, 2, 3, 4, 5, 6), 36)) },
            adapters = listOf(adapter("a", "192.168.1.20")),
        ))
        val nic = NicSnapshot("Wi-Fi", "wlan0", listOf(InetAddress.getByName("192.168.1.2")), true)
        val info = DesktopNetwork(saved, "", platform.network::wifiConnections, { listOf(nic) },
            platform.network::ethernetAdapters).start(1000)
        assertEquals("wlan0", info.interfaceName)
    }

    @Test fun `the platform's hidden Wi-Fi without a cable fails as hidden`() {
        val platform = FakePlatform(network = FakeNetwork(wifi = { throw WifiHiddenException() }))
        val network = DesktopNetwork(saved, "", platform.network::wifiConnections, { emptyList() },
            platform.network::ethernetAdapters)
        assertThrows(WifiHiddenException::class.java) { network.start(1000) }
    }

    @Test fun `active Wi-Fi retains the existing path`() {
        val wifi = WlanConnection("Wi-Fi", "Router", byteArrayOf(1,2,3,4,5,6), 36)
        val nic = NicSnapshot("Wi-Fi", "wlan0", listOf(InetAddress.getByName("192.168.1.2")), true)
        val info = DesktopNetwork(saved, "", { listOf(wifi) }, { listOf(nic) },
            { error("Ethernet must not be queried") }).start(1000)
        assertEquals("wlan0", info.interfaceName)
        assertArrayEquals(wifi.bssid, info.accessPointBssid)
    }
}
