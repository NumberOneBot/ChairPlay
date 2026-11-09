package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.bridge.FrameSink
import io.github.numberonebot.chairplay.bridge.TelemetrySource

/**
 * What the app shares with games on this PC: the picture as a shared texture with its status, and the telemetry record a
 * game writes. The names and layout are in docs/INTEGRATIONS.md; the system's library loads when the sink or the source
 * is first opened.
 */
internal interface GameBridge {
    /** Where the main screen's picture is published. */
    val frames: FrameSink

    /** Where a game's telemetry record is read from. */
    val telemetry: TelemetrySource
}
