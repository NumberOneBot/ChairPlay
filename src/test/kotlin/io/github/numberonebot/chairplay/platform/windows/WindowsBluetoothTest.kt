package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class WindowsBluetoothTest {
    @Test
    fun `address survives a format and parse round trip`() {
        val address = "AA:BB:CC:00:0E:F1"
        assertEquals(0xAABBCC000EF1L, WindowsBluetooth.parseAddress(address))
        assertEquals(address, WindowsBluetooth.formatAddress(WindowsBluetooth.parseAddress(address)))
    }

    @Test
    fun `guid stores the first three fields little-endian`() {
        val bytes = WindowsBluetooth.guidBytes(UUID.fromString("00000000-deca-fade-deca-deafdecacafe"))
        val expected = intArrayOf(
            0x00, 0x00, 0x00, 0x00, 0xca, 0xde, 0xde, 0xfa, 0xde, 0xca, 0xde, 0xaf, 0xde, 0xca, 0xca, 0xfe,
        ).map { it.toByte() }.toByteArray()
        assertArrayEquals(expected, bytes)
    }
}
