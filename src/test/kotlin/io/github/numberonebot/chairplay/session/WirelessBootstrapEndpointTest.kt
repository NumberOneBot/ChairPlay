package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import io.github.numberonebot.chairplay.platform.windows.FIREWALL_RULE
import io.github.numberonebot.chairplay.platform.windows.WindowsFirewall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/** WirelessBootstrap: the accessory's name, the Wi-Fi endpoint the iPhone joins, and the firewall hint. */
class WirelessBootstrapEndpointTest : WirelessBootstrapFixture() {
    private val config = AirPlayConfig(
        deviceName = "ChairPlay",
        deviceId = "02:11:22:33:44:55",
        btMac = "02:11:22:33:44:55",
        sourceVersion = "950.7.1",
        main = AirPlayDisplayConfig(widthPixels = 1280, heightPixels = 720),
    )
    private val identity = AirPlayIdentity(
        privateKey = ByteArray(32),
        publicKey = byteArrayOf(0x01, 0x23, 0xab.toByte()),
        pairingId = "pairing-1",
    )

    // The iPhone lists the accessory under the iAP2 identification name.
    @Test
    fun `identifies as ChairPlay`() {
        assertEquals("ChairPlay", desktopIdentification("10:5A:95:75:B2:AA").name)
        assertEquals("Гараж", desktopIdentification("10:5A:95:75:B2:AA", "Гараж").name)
    }

    @Test
    fun `endpoint carries listener port and address`() {
        val host = InetAddress.getByName("192.168.1.20")

        val endpoint = buildEndpoint(info(listOf(host)), host, 7001, config, identity)

        assertEquals(listOf("192.168.1.20"), endpoint.ipAddresses)
        assertEquals(7001, endpoint.airPlayPort)
        assertEquals(identity.publicKeyHex, endpoint.publicKey)
        assertEquals(36, endpoint.channel)
        assertEquals("Home", endpoint.ssid)
    }

    @Test
    fun `link-local IPv6 endpoint drops scope`() {
        val host = InetAddress.getByName("fe80::1%12")

        val endpoint = buildEndpoint(info(listOf(host)), host, 7000, config, identity)

        assertEquals(listOf("fe80::1"), endpoint.ipAddresses)
    }

    @Test
    fun `firewall hint names the port`() {
        val hint = WindowsFirewall("C:\\x\\chairplay.exe").hint(7000, null)

        assertTrue(hint, "localport=7000" in hint)
        assertTrue(hint, "C:\\x\\chairplay.exe" in hint)
        assertTrue(hint, "localport=7000-7010,49152-65535" in hint)
        assertTrue(hint, "protocol=UDP localport=5353" in hint)
        // The start-up check looks for this name; Windows names its own prompt rules after the exe.
        assertTrue(hint, "name=\"ChairPlay CarPlay inbound\" dir=in" in hint)
        assertEquals(3, Regex("name=\"$FIREWALL_RULE\"").findAll(hint).count())
    }
}
