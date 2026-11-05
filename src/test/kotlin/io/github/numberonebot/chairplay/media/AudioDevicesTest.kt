package io.github.numberonebot.chairplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDevicesTest {
    // Java Sound on Windows names DirectSound devices, cut to 31 characters, plus "Primary Sound Driver".
    private val outputs = listOf(
        "Primary Sound Driver",
        "Speakers (Realtek(R) Audio)",
        "LG ULTRAGEAR+ (NVIDIA High Defi",
        "SPDIF Interface (4- FX-AUDIO-DA",
    )

    @Test
    fun `default or blank keeps the Windows default device`() {
        assertNull(matchDevice("audio.output", "default", outputs))
        assertNull(matchDevice("audio.output", "  ", outputs))
        assertNull(matchDevice("audio.output", null, outputs))
    }

    @Test
    fun `a device is found by its whole name or a part of it, in any case`() {
        assertEquals("Speakers (Realtek(R) Audio)", matchDevice("audio.output", "speakers (realtek(r) audio)", outputs))
        assertEquals("LG ULTRAGEAR+ (NVIDIA High Defi", matchDevice("audio.output", "ultragear", outputs))
        assertEquals("SPDIF Interface (4- FX-AUDIO-DA", matchDevice("audio.output", "FX-AUDIO", outputs))
    }

    // An unplugged headset or USB card named in the settings stopped the start; the Windows default plays instead.
    @Test
    fun `a device that is gone gives way to the Windows default, with a warning`() {
        val warnings = mutableListOf<String>()

        assertNull(matchDeviceOrDefault("audio.output", "Headset", outputs, warnings::add))
        assertEquals(1, warnings.size)
        assertEquals("Speakers (Realtek(R) Audio)", matchDeviceOrDefault("audio.output", "speakers", outputs, warnings::add))
        assertEquals(1, warnings.size)
    }

    @Test
    fun `no match or several matches are refused with the devices listed`() {
        val none = runCatching { matchDevice("audio.input", "headset", outputs) }.exceptionOrNull()
        assertTrue(none is IllegalArgumentException)
        assertTrue(none!!.message!!, "audio.input" in none.message!! && "Speakers (Realtek(R) Audio)" in none.message!!)

        val several = runCatching { matchDevice("audio.output", "(", outputs) }.exceptionOrNull()
        assertTrue(several is IllegalArgumentException)
        assertTrue(several!!.message!!, "audio.output" in several.message!!)
    }
}
