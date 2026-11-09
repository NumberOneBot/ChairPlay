package io.github.numberonebot.chairplay.discovery

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class DesktopBonjourTest {
    private val config = AirPlayConfig(
        deviceName = "xcertplay",
        deviceId = "02:00:00:00:00:02",
        btMac = "02:00:00:00:00:02",
        sourceVersion = "366.0",
        main = AirPlayDisplayConfig(widthPixels = 1280, heightPixels = 720),
        model = "LIVI",
    )
    private val identity = AirPlayIdentity(
        privateKey = ByteArray(32),
        publicKey = byteArrayOf(0x01, 0x23, 0xab.toByte()),
        pairingId = "pairing-1",
    )

    // Same inputs and expectations as upstream CarPlayBonjourTest, so the copy stays pinned to it.
    @Test
    fun `txt records match upstream`() {
        assertEquals(
            linkedMapOf(
                "deviceid" to "02:00:00:00:00:02",
                "features" to "0x5653aee2,0x61",
                "flags" to "0x4",
                "model" to "LIVI",
                "srcvers" to "366.0",
                "protovers" to "1.1",
                "pi" to "pairing-1",
                "pk" to "0123ab",
            ),
            CarPlayTxt.airPlayTxtRecords(config, identity),
        )
        for (disabled in listOf(false, true)) {
            val receiver = config.copy(disableAudioOutput = disabled)
            val parts = CarPlayTxt.airPlayTxtRecords(receiver, identity).getValue("features")
                .split(',').map { it.removePrefix("0x").toLong(16) }
            assertEquals(AirPlayInfoPlist.build(receiver)["features"], parts[0] or (parts.getOrElse(1) { 0L } shl 32))
        }
        assertEquals("0xffffffff", CarPlayTxt.featuresTxt(0xffffffffL))
    }

    // Same inputs and expectation as upstream CarPlayBonjourTest.connectProbeRequestMatchesExactRequestLineAndHeaders.
    @Test
    fun `connect request matches upstream`() {
        assertEquals(
            "GET /ctrl-int/1/connect HTTP/1.1\r\n" +
                "Host: [fe80::1]:7000\r\n" +
                "User-Agent: AirPlay/366.0\r\n" +
                "AirPlay-Receiver-Device-ID: 020000000002\r\n" +
                "Connection: close\r\n" +
                "\r\n",
            CarPlayTxt.connectRequest(host = "fe80::1%wlan0", port = 7000, sourceVersion = "366.0", deviceId = "02:00:00:00:00:02"),
        )
    }

    // Any host on the LAN can publish _carplay-ctrl._tcp; only a unicast peer in the registry's scope is probed.
    @Test
    fun `probes only unicast peers in the registry's scope`() {
        val lan = InetAddress.getByName("192.168.0.141")
        val loopback = InetAddress.getLoopbackAddress()
        assertTrue(isProbeTarget(InetAddress.getByName("192.168.0.211"), lan))
        assertTrue(isProbeTarget(InetAddress.getByName("fe80::1"), InetAddress.getByName("fe80::2")))
        assertFalse(isProbeTarget(loopback, lan))
        assertFalse(isProbeTarget(InetAddress.getByName("224.0.0.251"), lan))
        assertFalse(isProbeTarget(InetAddress.getByName("0.0.0.0"), lan))
        assertTrue(isProbeTarget(loopback, loopback))
    }

    // A peer that never ends its status line must not grow the read without bound. The probe reads its answer with
    // readStatusLine (the test below has it read a real one), so the bound is checked on the read itself, with no mDNS.
    @Test
    fun `gives up on an endless status line`() {
        val endless = ByteArrayInputStream(ByteArray(4096) { 'A'.code.toByte() })

        val error = assertThrows(IOException::class.java) { readStatusLine(endless) }
        assertTrue(error.message, "too long" in error.message.orEmpty())
    }

    @Test
    fun `reads a status line up to its line end`() {
        val answer = ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))

        assertEquals("HTTP/1.1 200 OK", readStatusLine(answer))
    }

    // The iPhone publishes _carplay-ctrl._tcp after it joins Wi-Fi; the receiver must ask it to connect.
    @Test
    fun `asks a discovered CarPlay control service to connect`() {
        val loopback = InetAddress.getLoopbackAddress()
        val logged = CopyOnWriteArrayList<String>()
        MulticastSocket(null).use { holder ->
            holder.reuseAddress = true
            holder.bind(InetSocketAddress(5353))
            ServerSocket(0, 1, loopback).use { phone ->
                phone.soTimeout = 20_000
                JmDNS.create(loopback, "phone").use { phoneDns ->
                    phoneDns.registerService(ServiceInfo.create(
                        "${CarPlayTxt.CARPLAY_CONTROL_SERVICE_TYPE}.local.", "iPhone", phone.localPort, 0, 0,
                        mapOf("id" to "98:0D:AF:F1:12:48"),
                    ))
                    DesktopBonjour(config, identity, listOf(loopback), logged::add).use { bonjour ->
                        bonjour.start()
                        phone.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                            val request = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                            socket.getOutputStream().flush()
                            assertEquals("GET /ctrl-int/1/connect HTTP/1.1", request.first())
                            assertTrue(request.toString(), "AirPlay-Receiver-Device-ID: 020000000002" in request)
                        }
                        val deadline = System.nanoTime() + 5_000_000_000L
                        while (logged.none { "HTTP/1.1 200 OK" in it } && System.nanoTime() < deadline) Thread.sleep(50)
                        assertTrue(logged.toString(), logged.any { "HTTP/1.1 200 OK" in it })
                    }
                }
            }
        }
    }

    // JmDNS answers a PTR listing on loopback; getServiceInfo alone does not, as it only queries SRV/TXT.
    @Test
    fun `publishes on loopback while port 5353 is shared`() {
        val publishConfig = config.copy(port = 47123)
        val loopback = InetAddress.getLoopbackAddress()
        MulticastSocket(null).use { holder ->
            holder.reuseAddress = true
            holder.bind(InetSocketAddress(5353))
            DesktopBonjour(publishConfig, identity, listOf(loopback)).use { bonjour ->
                bonjour.start()
                val found = JmDNS.create(loopback, "probe").use { probe ->
                    probe.list("${CarPlayTxt.AIRPLAY_SERVICE_TYPE}.local.", 5000)
                        .firstOrNull { it.name == publishConfig.deviceName }
                }
                assertNotNull("service not resolved on $loopback", found)
                assertEquals(publishConfig.port, found!!.port)
                assertEquals("0x4", found.getPropertyString("flags"))
            }
        }
    }
}
