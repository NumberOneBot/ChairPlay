package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.media.DefaultAudioDevices
import io.github.numberonebot.chairplay.media.apac.SpatialDevice
import io.github.numberonebot.chairplay.platform.OutputDevice
import io.github.numberonebot.chairplay.platform.SpatialOutputs
import io.github.numberonebot.chairplay.platform.SystemAudio

internal class MacAudio : SystemAudio {

    override fun defaultDevices(): DefaultAudioDevices {
        val outBuf = ByteArray(256)
        val inBuf = ByteArray(256)
        MacNative.INSTANCE.cp_audio_default_devices(outBuf, outBuf.size, inBuf, inBuf.size)
        val outName = String(outBuf).trimEnd('\u0000').ifEmpty { null }
        val inName = String(inBuf).trimEnd('\u0000').ifEmpty { null }
        return DefaultAudioDevices(outName, inName)
    }

    override val spatial: SpatialOutputs = MacSpatialOutputs
}

private object MacSpatialOutputs : SpatialOutputs {
    override fun devices(): List<OutputDevice> =
        throw UnsupportedOperationException("Spatial audio not supported on macOS; use stereo Java Sound")

    override fun defaultDevice(): OutputDevice =
        throw UnsupportedOperationException("Spatial audio not supported on macOS; use stereo Java Sound")

    override fun open(deviceId: String?, channels: Int): SpatialDevice =
        throw UnsupportedOperationException("Spatial audio not supported on macOS; use stereo Java Sound")
}
