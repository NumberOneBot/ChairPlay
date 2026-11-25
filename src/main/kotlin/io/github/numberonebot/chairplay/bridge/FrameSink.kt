package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import java.nio.ByteBuffer

/** Where [FrameOutput] publishes: each call gives null on success, else the error's text. */
internal interface FrameSink {
    /**
     * Creates the status file in state 0 when it is not there yet, kept from then on for the whole run, and writes the
     * port of ChairPlay's local API and its version (empty for a development build) into it, for a launcher to read.
     */
    fun announce(uiPort: Int, version: String): String?

    /** Creates the shared side, and the status file when needed, in state 1 (waiting for the iPhone). */
    fun open(): String?

    /** One packed BGRA picture, stride `width * 4`, with its safe rect in its pixels; leaves state 2 (picture flowing). */
    fun publish(bgra: ByteBuffer, width: Int, height: Int, safe: AreaRect): String?

    /** 1 waiting for the iPhone, 3 picture frozen; publishing sets 2 again. */
    fun state(state: Int)

    /** Leaves state 0 and an empty texture name in the status file, which stays, and lets the textures go. */
    fun close()

    /** Writes whose screen a game shows to the status: 0 CarPlay's, 1 the car's own; nothing when closed. */
    fun screen(screen: Int)
}
