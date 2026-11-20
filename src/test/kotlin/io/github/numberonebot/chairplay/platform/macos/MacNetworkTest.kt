package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.network.WifiHiddenException
import io.github.numberonebot.chairplay.network.WlanConnection
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MacNetworkTest {

    @Test
    fun `when wifiInfo reports wifiHidden true wifiConnections throws WifiHiddenException`() {
        val network = MacNetwork(
            wifiProvider = {
                MacNetwork.WifiDetails(
                    interfaceName = "en0",
                    ssid = null,
                    bssid = null,
                    channel = 60,
                    powerOn = true,
                    serviceActive = true,
                    wifiHidden = true,
                )
            },
        )
        assertThrows(WifiHiddenException::class.java) {
            network.wifiConnections()
        }
    }

    @Test
    fun `when wifiInfo reports valid connection wifiConnections returns WlanConnection`() {
        val expectedBssid = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9a.toByte(), 0xbc.toByte())
        val network = MacNetwork(
            wifiProvider = {
                MacNetwork.WifiDetails(
                    interfaceName = "en0",
                    ssid = "HomeWiFi",
                    bssid = expectedBssid,
                    channel = 60,
                    powerOn = true,
                    serviceActive = true,
                    wifiHidden = false,
                )
            },
        )
        val connections = network.wifiConnections()
        assertEquals(1, connections.size)
        val conn = connections.first()
        assertEquals("en0", conn.interfaceDescription)
        assertEquals("HomeWiFi", conn.ssid)
        assertArrayEquals(expectedBssid, conn.bssid)
        assertEquals(60, conn.channel)
    }

    @Test
    fun `filterPhysicalInterfaces excludes virtual and non-en interfaces and keeps up en interfaces`() {
        val candidates = listOf(
            MacNetwork.NetworkNic(name = "lo0", isUp = true, isLoopback = true, isVirtual = false),
            MacNetwork.NetworkNic(name = "utun0", isUp = true, isLoopback = false, isVirtual = true),
            MacNetwork.NetworkNic(name = "utun7", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "awdl0", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "llw0", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "bridge0", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "gif0", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "stf0", isUp = false, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "anpi0", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "ap1", isUp = false, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "en0", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "en3", isUp = true, isLoopback = false, isVirtual = false),
            MacNetwork.NetworkNic(name = "en5", isUp = false, isLoopback = false, isVirtual = false),
        )

        val filtered = filterPhysicalInterfaces(candidates)
        assertEquals(listOf("en0", "en3"), filtered.map { it.name })
    }

    @Test
    fun `ethernetAdapters excludes active wifi interface and returns wired en interfaces with ipv4`() {
        val addrEn0 = InetAddress.getByName("192.168.1.100")
        val addrEn3 = InetAddress.getByName("10.0.0.5")

        val nics = listOf(
            MacNetwork.NetworkNic(
                name = "en0",
                isUp = true,
                isLoopback = false,
                isVirtual = false,
                addresses = listOf(addrEn0),
            ),
            MacNetwork.NetworkNic(
                name = "en3",
                isUp = true,
                isLoopback = false,
                isVirtual = false,
                addresses = listOf(addrEn3),
            ),
            MacNetwork.NetworkNic(
                name = "en4",
                isUp = true,
                isLoopback = false,
                isVirtual = false,
                addresses = emptyList(), // no ipv4
            ),
        )

        val network = MacNetwork(
            wifiProvider = {
                MacNetwork.WifiDetails(
                    interfaceName = "en0",
                    ssid = "HomeWiFi",
                    bssid = byteArrayOf(1, 2, 3, 4, 5, 6),
                    channel = 36,
                    powerOn = true,
                    serviceActive = true,
                    wifiHidden = false,
                )
            },
            nicProvider = { nics },
        )

        val ethernet = network.ethernetAdapters()
        assertEquals(1, ethernet.size)
        val adapter = ethernet.first()
        assertEquals("en3", adapter.id)
        assertEquals("en3", adapter.label)
        assertEquals("en3", adapter.interfaceName)
        assertEquals(listOf("10.0.0.5"), adapter.addresses)
    }
}
