package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class WasapiOutputTest {
    @Before fun hardwareRunRequested() {
        assumeTrue(System.getProperty("chairplay.wasapi.hardwareTest") == "true")
    }

    @Test fun `Windows output probes and renders silence without changing the device configuration`() {
        val devices = WasapiOutput.devices()
        assertTrue(devices.isNotEmpty())
        val before = WasapiOutput.defaultDevice()
        WasapiOutput.open(before.id, 2).use { output ->
            assertEquals(48000, output.format.sampleRate)
            assertEquals(2, output.format.channels)
            assertEquals(3, output.format.mask)
            output.write(ShortArray(4800 * 2))
            assertTrue(output.playedFrames() >= 0)
            output.cancel()
            assertThrows(IOException::class.java) { output.write(ShortArray(2)) }
        }
        assertEquals(before, WasapiOutput.defaultDevice())
    }

    @Test fun `explicit surround refuses a stereo endpoint rather than silently converting`() {
        val device = WasapiOutput.defaultDevice()
        if (device.channels != 6 || device.mask !in listOf(0x60f, 0x3f)) {
            assertThrows(IOException::class.java) { WasapiOutput.open(device.id, 6) }
        }
    }

    @Test fun `an underrun does not advance the media playhead through samples never submitted`() {
        WasapiOutput.open(null, 2).use { output ->
            output.write(ShortArray(480 * 2))
            val deadline = System.nanoTime() + 1_000_000_000
            while (output.playedFrames() < 480 && System.nanoTime() < deadline) Thread.sleep(5)
            Thread.sleep(80)
            assertEquals(480L, output.playedFrames())
            output.write(ShortArray(4800 * 2))
            assertTrue(output.playedFrames() < 5280)
        }
    }
}
