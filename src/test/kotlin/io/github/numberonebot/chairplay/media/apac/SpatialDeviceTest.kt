package io.github.numberonebot.chairplay.media.apac

import io.github.numberonebot.chairplay.platform.OutputDevice
import io.github.numberonebot.chairplay.platform.SpatialOutputs
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpatialDeviceTest {
    private val speakers = OutputDevice("id-speakers", "Speakers (Realtek)", 48000, 2, 3)
    private val headset = OutputDevice("id-headset", "Headset (USB)", 48000, 2, 3)
    private val headphones = OutputDevice("id-headphones", "Headphones (Realtek)", 48000, 2, 3)

    private fun outputs(vararg devices: OutputDevice, default: OutputDevice = devices.first()) = object : SpatialOutputs {
        override fun devices() = devices.toList()
        override fun defaultDevice() = default
        override fun open(deviceId: String?, channels: Int): SpatialDevice = throw UnsupportedOperationException()
    }

    @Test
    fun `spatialDevice takes the default for a blank setting`() {
        val all = outputs(speakers, headset, default = headset)

        assertEquals(headset, spatialDevice(all, null))
        assertEquals(headset, spatialDevice(all, " "))
        assertEquals(headset, spatialDevice(all, "default"))
    }

    @Test
    fun `spatialDevice takes the one device a part of its name matches`() {
        assertEquals(headset, spatialDevice(outputs(speakers, headset), "usb"))
    }

    @Test
    fun `spatialDevice takes the default for a gone device`() {
        assertEquals(speakers, spatialDevice(outputs(speakers, headset), "Unplugged card"))
    }

    @Test
    fun `spatialDevice takes the default when two devices match`() {
        assertEquals(headset, spatialDevice(outputs(speakers, headphones, headset, default = headset), "Realtek"))
    }

    @Test
    fun `no spatial outputs reports spatial audio unavailable`() {
        val library = System.getProperty("chairplay.apac.testLibrary") ?: "build/native/chairplay_apac.dll"
        assumeTrue(Files.isRegularFile(Path.of(library)))
        val dir = Files.createTempDirectory("spatial-no-output")
        Files.createDirectories(dir.resolve("apac"))
        Files.copy(Path.of(library), dir.resolve("apac").resolve(io.github.numberonebot.chairplay.platform.NativeLibraries.fileName("chairplay_apac")))
        val missing = object : SpatialOutputs {
            override fun devices(): List<OutputDevice> = throw UnsatisfiedLinkError("no chairplay_audio")
            override fun defaultDevice(): OutputDevice = throw UnsatisfiedLinkError("no chairplay_audio")
            override fun open(deviceId: String?, channels: Int): SpatialDevice = throw UnsatisfiedLinkError("no chairplay_audio")
        }

        assertFalse(SpatialRuntime.capability(dir, null, fresh = true, outputs = missing).available)
    }
}
