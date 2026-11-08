package io.github.numberonebot.chairplay.phone

/** The codec of a phone's screen stream. */
enum class VideoCodecKind { H264, H265 }

/** Which screen a phone's stream draws: the car's main display, or its instrument cluster (the AltScreen). */
enum class ScreenKind { MAIN, CLUSTER }
