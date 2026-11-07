package io.github.numberonebot.chairplay.bridge

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import io.github.numberonebot.chairplay.config.AreaRect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BridgeNativeTest {
    private val lib get() = BridgeNative.lib
    private var warp = 0
    /** A game's handle on the status file, which keeps it alive across a close. */
    private var held: WinNT.HANDLE? = null

    @Before fun open() {
        assumeTrue(BridgeNative.loadError ?: "", BridgeNative.loadError == null)
        // Hardware first; WARP keeps the tests running on a machine without a D3D11 GPU.
        warp = if (lib.cpb_frame_open(0) == 0) 0 else 1
        assumeTrue(lib.cpb_last_error(), warp == 0 || lib.cpb_frame_open(1) == 0)
    }

    @After fun close() {
        if (BridgeNative.loadError == null) lib.cpb_frame_close()
        held?.let { Kernel32.INSTANCE.CloseHandle(it) }
    }

    @Test fun `a reader holding the status never sees a generation again after a reopen`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        val before = status()
        hold()
        lib.cpb_frame_close()
        assertEquals(lib.cpb_last_error(), 0, lib.cpb_frame_open(warp))
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        val after = status()
        assertTrue("generation ${after.generation} after ${before.generation}", after.generation > before.generation)
        assertNotEquals(before.textureName, after.textureName)
        assertEquals(800, openTexture(after.textureName).width)
    }

    // A game may still hold the last texture by name after the status file went: a new file never reuses it.
    @Test fun `a reopen with no reader goes on past the last generation`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        val before = status()
        lib.cpb_frame_close()
        assertEquals(lib.cpb_last_error(), 0, lib.cpb_frame_open(warp))
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        val after = status()
        assertTrue("generation ${after.generation} after ${before.generation}", after.generation > before.generation)
        assertEquals(800, openTexture(after.textureName).width)
    }

    @Test fun `closing tells a reader holding the status that the picture is gone`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        hold()
        lib.cpb_frame_close()
        val status = status()
        assertEquals(0, status.state)
        assertEquals("", status.textureName)
    }

    @Test fun `a published frame is readable by name from another device`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480, AreaRect(10, 20, 700, 400)))
        val status = status()
        assertEquals(2, status.state)
        // 1 for the process' first picture; tests before this one in the process went on from there.
        assertTrue("generation ${status.generation}", status.generation >= 1)
        assertEquals(800, status.width)
        assertEquals(480, status.height)
        assertEquals(1L, status.frameCounter)
        assertEquals(AreaRect(10, 20, 700, 400), status.safe)
        assertEquals(87, status.dxgiFormat)
        assertEquals(ProcessHandle.current().pid().toInt(), status.producerPid)
        assertEquals(BridgeLayout.textureName(status.generation), status.textureName)

        val texture = openTexture(status.textureName)
        assertEquals(800, texture.width)
        assertEquals(480, texture.height)
        assertEquals(PIXEL, texture.firstPixel)
    }

    @Test fun `a new size starts a new generation`() {
        assertEquals(lib.cpb_last_error(), 0, publish(800, 480))
        val first = status().generation
        assertEquals(lib.cpb_last_error(), 0, publish(640, 360))
        val status = status()
        assertEquals(first + 1, status.generation)
        assertEquals(BridgeLayout.textureName(first + 1), status.textureName)
        assertEquals(640 to 360, status.width to status.height)
        assertEquals(2L, status.frameCounter)
        val texture = openTexture(status.textureName)
        assertEquals(640 to 360, texture.width to texture.height)
        // The previous texture lives on for a second, so a reader can switch over.
        assertEquals(800 to 480, openTexture(BridgeLayout.textureName(first)).let { it.width to it.height })
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

    @Test fun `a telemetry record written by a game is read back whole`() {
        val sent = TelemetryRecord(7, false, 3, 12.5, 270.0, 48.85, 2.29, 3000.0, "Test")
        val bytes = ByteBuffer.allocateDirect(BridgeLayout.SIZE)
        TelemetryRecord.write(sent, bytes)
        assertEquals(lib.cpb_last_error(), 0, lib.cpb_telemetry_open())
        try {
            assertEquals(lib.cpb_last_error(), 0, lib.cpb_test_write_telemetry(bytes))
            val out = ByteBuffer.allocateDirect(BridgeLayout.SIZE)
            assertEquals(1, lib.cpb_telemetry_read(out))
            assertEquals(sent, TelemetryRecord.parse(out))
            // Opening again must not clear what a game wrote.
            assertEquals(0, lib.cpb_telemetry_open())
            assertEquals(1, lib.cpb_telemetry_read(out))
            assertEquals(sent, TelemetryRecord.parse(out))
        } finally {
            lib.cpb_telemetry_close()
        }
        assertEquals(0, lib.cpb_telemetry_read(ByteBuffer.allocateDirect(BridgeLayout.SIZE)))
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

    private fun hold() {
        held = Kernel32.INSTANCE.OpenFileMapping(WinBase.FILE_MAP_READ, false, "Local\\${BridgeLayout.STATUS_FILE}")
            ?: throw AssertionError("no status file to hold")
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
