package io.github.numberonebot.chairplay.platform

/**
 * The GPU video decoding FFmpeg offers on a system: its device [deviceType] (an `AV_HWDEVICE_TYPE_*`), the
 * [pixelFormat] (an `AV_PIX_FMT_*`) of the frames it decodes into, and the [name] and [apiName] the log uses.
 */
internal data class HardwareVideo(val deviceType: Int, val pixelFormat: Int, val name: String, val apiName: String)
