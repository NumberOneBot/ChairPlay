package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.media.DefaultAudioDevices
import io.github.numberonebot.chairplay.media.apac.SpatialDevice
import io.github.numberonebot.chairplay.platform.OutputDevice
import io.github.numberonebot.chairplay.platform.SpatialOutputs
import io.github.numberonebot.chairplay.platform.SystemAudio

/** Windows' sound devices from its device enumerator, and its multichannel output through chairplay_audio.dll. */
internal class WindowsAudio : SystemAudio {
    override fun defaultDevices(): DefaultAudioDevices = WindowsDefaultAudio.read()

    override val spatial: SpatialOutputs = WindowsSpatialOutputs
}

private object WindowsSpatialOutputs : SpatialOutputs {
    override fun devices(): List<OutputDevice> = WasapiOutput.devices()

    override fun defaultDevice(): OutputDevice = WasapiOutput.defaultDevice()

    override fun open(deviceId: String?, channels: Int): SpatialDevice = WasapiOutput.open(deviceId, channels)
}
