package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.media.DefaultAudioDevices
import io.github.numberonebot.chairplay.media.apac.SpatialDevice

/** A sound output the multichannel path can open: the system's [id], the [name] the settings match, and its format. */
data class OutputDevice(val id: String, val name: String, val sampleRate: Int, val channels: Int, val mask: Int)

/** The system's multichannel sound outputs, which spatial audio plays through. */
internal interface SpatialOutputs {
    /** Every output that plays, in the system's order. */
    fun devices(): List<OutputDevice>

    /** The output the system plays through by default; throws when it has none. */
    fun defaultDevice(): OutputDevice

    /** Opens [deviceId] (the default for null) for [channels] channels: 0 chooses verified 5.1 or stereo, 6 never falls back. */
    fun open(deviceId: String?, channels: Int): SpatialDevice
}

/** The system's sound devices. */
internal interface SystemAudio {
    /** The names of the devices the system plays and records with by default, read when asked. */
    fun defaultDevices(): DefaultAudioDevices

    val spatial: SpatialOutputs
}
