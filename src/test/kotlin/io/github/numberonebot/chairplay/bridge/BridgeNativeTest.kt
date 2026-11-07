package io.github.numberonebot.chairplay.bridge

import com.sun.jna.ptr.IntByReference
import io.github.numberonebot.chairplay.config.AreaRect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BridgeNativeTest {
    private val lib get() = BridgeNative.lib

    @Before fun open() {
        assumeTrue(BridgeNative.loadError ?: "", BridgeNative.loadError == null)
        // Hardware first; WARP keeps the tests running on a machine without a D3D11 GPU.
        assumeTrue(lib.cpb_last_error(), lib.cpb_frame_open(0) == 0 || lib.cpb_frame_open(1) == 0)
    }

    @After fun close() {
        if (BridgeNative.loadError == null) lib.cpb_frame_close()
    }

    @Test fun `a published frame is readable by name from another device`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480, AreaRect(10, 20, 700, 400)))
        val status = status()
        assertEquals(2, status.state)
        assertEquals(1, status.generation)
        assertEquals(800, status.width)
        assertEquals(480, status.height)
        assertEquals(1L, status.frameCounter)
        assertEquals(AreaRect(10, 20, 700, 400), status.safe)
        assertEquals(87, status.dxgiFormat)
        assertEquals(ProcessHandle.current().pid().toInt(), status.producerPid)
        assertEquals(BridgeLayout.textureName(1), status.textureName)

        val texture = openTexture(status.textureName)
        assertEquals(800, texture.width)
        assertEquals(480, texture.height)
        assertEquals(PIXEL, texture.firstPixel)
    }

    @Test fun `a new size starts a new generation`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        assertEquals(lib.cpb_last_error(), 0, publish(640, 360))
        val status = status()
        assertEquals(2, status.generation)
        assertTrue(status.textureName, status.textureName.endsWith(".2"))
        assertEquals(640 to 360, status.width to status.height)
        assertEquals(2L, status.frameCounter)
        val texture = openTexture(status.textureName)
        assertEquals(640 to 360, texture.width to texture.height)
        // The previous texture lives on for a second, so a reader can switch over.
        assertEquals(800 to 480, openTexture(BridgeLayout.textureName(1)).let { it.width to it.height })
    }

    @Test fun `the state call is written to the status`() {
        assertEquals(1, status().state)
        lib.cpb_frame_state(3)
        assertEquals(3, status().state)
    }

    @Test fun `closing removes the status file`() {
        status()
        lib.cpb_frame_close()
        assertEquals(0, lib.cpb_test_read_status(ByteBuffer.allocateDirect(BridgeLayout.SIZE)))
    }

    private class Texture(val width: Int, val height: Int, val firstPixel: Int)

    private fun publish(width: Int, height: Int, safe: AreaRect = AreaRect(0, 0, width, height)): Int {
        val bgra = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(width * height) { bgra.putInt(PIXEL) }
        bgra.flip()
        return lib.cpb_frame_publish(bgra, width, height, width * 4, safe.x, safe.y, safe.width, safe.height)
    }

    /** The status file as another process reads it; fails when it is missing, torn, or not version 1. */
    private fun status(): BridgeStatus {
        val out = ByteBuffer.allocateDirect(BridgeLayout.SIZE)
        assertEquals("consistent status read", 1, lib.cpb_test_read_status(out))
        return BridgeStatus.parse(out) ?: throw AssertionError("status has no magic or another version")
    }

    private fun openTexture(name: String): Texture {
        val width = IntByReference()
        val height = IntByReference()
        val pixel = IntByReference()
        assertEquals(lib.cpb_last_error(), 0, lib.cpb_test_open_texture(name, width, height, pixel))
        return Texture(width.value, height.value, pixel.value)
    }

    private companion object {
        /** B, G, R, A bytes C0 40 20 FF. */
        const val PIXEL = 0xFF2040C0.toInt()
    }
}
