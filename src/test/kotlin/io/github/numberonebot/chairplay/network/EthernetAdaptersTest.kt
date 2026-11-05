package io.github.numberonebot.chairplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EthernetAdaptersTest {
    private fun row(
        guid: String = "{6A0F2C55-1E2B-4C47-9F5E-4B1D7C0E9A11}",
        type: Int = ETHERNET,
        up: Boolean = true,
        connector: Boolean = true,
        filter: Boolean = false,
    ) = InterfaceRow(12, guid, "Ethernet 2", type, up, connector, filter)

    // Windows says which adapters are physical: an Ethernet adapter that is up, with a connector, and not a filter.
    @Test
    fun `a wired adapter that is up is kept, by its lowercase GUID without braces`() {
        assertEquals(listOf("6a0f2c55-1e2b-4c47-9f5e-4b1d7c0e9a11"), physicalEthernet(listOf(row())).map { it.guid })
    }

    @Test
    fun `Wi-Fi, a disconnected cable, a virtual switch, and a filter layer are left out`() {
        val wifi = row(type = 71)
        val unplugged = row(up = false)
        val virtualSwitch = row(connector = false)
        val filterLayer = row(filter = true)

        assertTrue(physicalEthernet(listOf(wifi, unplugged, virtualSwitch, filterLayer)).isEmpty())
    }

    @Test
    fun `an adapter seen twice is listed once`() {
        assertEquals(1, physicalEthernet(listOf(row(), row())).size)
    }

    /** Asks this PC's adapters (never changes them): the call must at least answer. */
    @Test
    fun `the lookup runs against this PC`() {
        EthernetAdapters.current()
    }
}
