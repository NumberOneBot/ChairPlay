package io.github.numberonebot.chairplay.platform.macos

import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class MacAudioTest {

    @Test
    fun defaultDevicesLoadsNativeDylibAndReturnsWithoutThrowing() {
        val os = System.getProperty("os.name").orEmpty()
        assumeTrue("Mac only", os.startsWith("Mac"))

        val audio = MacAudio()
        val devices = audio.defaultDevices()
        assertNotNull(devices)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun spatialDevicesThrowsUnsupportedOperationException() {
        val audio = MacAudio()
        audio.spatial.devices()
    }
}
