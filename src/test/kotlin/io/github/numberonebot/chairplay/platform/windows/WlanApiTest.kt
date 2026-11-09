package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.network.WifiHiddenException
import io.github.numberonebot.chairplay.network.WlanConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WlanApiTest {
    private val home = WlanConnection("Wi-Fi", "Home", ByteArray(6), 36)

    // Windows 11 24H2 and later hide a connected adapter's network from desktop apps that may not use location: that
    // is not a PC on no Wi-Fi.
    @Test fun `a connected adapter whose network Windows hides is not read as no Wi-Fi`() {
        assertThrows(WifiHiddenException::class.java) { connectionsOf(listOf(AdapterWifi.None, AdapterWifi.Hidden)) }
    }

    @Test fun `a network Windows shows is the connection, whatever another adapter hides`() {
        assertEquals(listOf(home), connectionsOf(listOf(AdapterWifi.Hidden, AdapterWifi.Connected(home))))
    }

    @Test fun `adapters on no network are no Wi-Fi`() {
        assertEquals(emptyList<WlanConnection>(), connectionsOf(listOf(AdapterWifi.None)))
        assertEquals(emptyList<WlanConnection>(), connectionsOf(emptyList()))
    }

    // Only access denied means hidden; any other failed query reads as no connection, as before.
    @Test fun `only a refused query means hidden`() {
        assertEquals(AdapterWifi.Hidden, AdapterWifi.refused(5))
        assertEquals(AdapterWifi.None, AdapterWifi.refused(1168))
    }
}
