package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import java.nio.ByteBuffer

/** Where [FrameOutput] publishes: each call gives null on success, else the error's text. */
internal interface FrameSink {
    /** Creates the shared side and the status file, in state 1 (waiting for the iPhone). */
    fun open(): String?

    /** One packed BGRA picture, stride `width * 4`, with its safe rect in its pixels; leaves state 2 (picture flowing). */
    fun publish(bgra: ByteBuffer, width: Int, height: Int, safe: AreaRect): String?

    /** 1 waiting for the iPhone, 3 picture frozen; publishing sets 2 again. */
    fun state(state: Int)

    /** Leaves state 0 and an empty texture name for a game still reading, and lets the shared side go. */
    fun close()
}
