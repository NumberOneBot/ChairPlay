package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BridgeLayoutTest {
    private fun buffer(): ByteBuffer = ByteBuffer.allocate(BridgeLayout.SIZE).order(ByteOrder.LITTLE_ENDIAN)

    private fun text(b: ByteBuffer, at: Int, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) b.put(at + i, bytes[i])
    }

    private fun status(magic: Int = BridgeLayout.STATUS_MAGIC, version: Int = 1): ByteBuffer {
        val b = buffer()
        b.putInt(0, magic)
        b.putShort(4, version.toShort())
        b.putShort(6, 136)
        b.putInt(12, 2)
        b.putInt(16, 7)
        b.putInt(20, 800)
        b.putInt(24, 480)
        b.putInt(28, 0); b.putInt(32, 0); b.putInt(36, 800); b.putInt(40, 480)
        b.putInt(44, 87)
        b.putInt(48, 0x1234)
        b.putInt(52, 1)
        b.putLong(56, 99)
        b.putInt(64, 30)
        b.putInt(68, 4242)
        text(b, 72, "ChairPlay.Main.v1.7")
        b.putInt(136, 1)
        b.putInt(140, 7529)
        text(b, 144, "1.1.3")
        return b
    }

    @Test
    fun `status parses every field at its offset`() {
        val s = BridgeStatus.parse(status())!!
        assertEquals(2, s.state)
        assertEquals(7, s.generation)
        assertEquals(800, s.width)
        assertEquals(480, s.height)
        assertEquals(AreaRect(0, 0, 800, 480), s.safe)
        assertEquals(87, s.dxgiFormat)
        assertEquals((1L shl 32) or 0x1234L, s.adapterLuid)
        assertEquals(99L, s.frameCounter)
        assertEquals(30, s.streamFps)
        assertEquals(4242, s.producerPid)
        assertEquals("ChairPlay.Main.v1.7", s.textureName)
        assertEquals(1, s.screen)
        assertEquals(7529, s.uiPort)
        assertEquals("1.1.3", s.appVersion)
    }

    @Test
    fun `status with another magic or version is not read`() {
        assertNull(BridgeStatus.parse(status(magic = 0)))
        assertNull(BridgeStatus.parse(status(version = 2)))
    }

    @Test
    fun `texture name follows the generation`() {
        assertEquals("ChairPlay.Main.v1.12", BridgeLayout.textureName(12))
    }
}
