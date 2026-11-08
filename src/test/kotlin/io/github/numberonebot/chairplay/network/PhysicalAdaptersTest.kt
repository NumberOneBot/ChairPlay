package io.github.numberonebot.chairplay.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

/** The settings' local network addresses come from the adapters Windows counts as physical, never a virtual switch's. */
class PhysicalAdaptersTest {
    private fun row(index: Int, type: Int, connector: Boolean = true, up: Boolean = true) =
        InterfaceRow(index, "{00000000-0000-0000-0000-00000000000$index}", "Adapter $index", type, up, connector, false)

    private val addresses = mapOf(
        1 to listOf(InetAddress.getByName("192.168.0.142")),
        2 to listOf(InetAddress.getByName("172.20.0.1")),
        3 to listOf(InetAddress.getByName("10.0.0.15")),
        4 to listOf(InetAddress.getByName("10.0.0.16")),
    )

    @Test
    fun `a Wi-Fi or Ethernet adapter's addresses are listed, a virtual switch's and a down adapter's are not`() {
        val wifi = row(1, type = 71)
        val vEthernet = row(2, type = ETHERNET, connector = false)
        val ethernet = row(3, type = ETHERNET)
        val unplugged = row(4, type = ETHERNET, up = false)

        val listed = physicalAddresses(listOf(wifi, vEthernet, ethernet, unplugged)) { addresses[it].orEmpty() }

        assertEquals(listOf("192.168.0.142", "10.0.0.15"), listed.map { it.hostAddress })
    }

    /** Asks this PC's adapters (never changes them): the call must at least answer. */
    @Test
    fun `the lookup runs against this PC`() {
        physicalInterfaceAddresses()
    }
}
