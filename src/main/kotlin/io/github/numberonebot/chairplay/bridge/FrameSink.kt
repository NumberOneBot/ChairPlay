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

/** chairplay_bridge.dll through [BridgeNative]. */
internal object NativeFrameSink : FrameSink {
    override fun open(): String? {
        BridgeNative.loadError?.let { return it }
        val lib = BridgeNative.lib
        if (lib.cpb_frame_open(0) == 0) return null
        return lib.cpb_last_error()
    }

    override fun publish(bgra: ByteBuffer, width: Int, height: Int, safe: AreaRect): String? {
        val stride = width * 4
        // The DLL reads this much from the buffer's address and cannot check it.
        val needed = stride.toLong() * (height - 1) + stride
        if (width <= 0 || height <= 0 || !bgra.isDirect || bgra.remaining() < needed) {
            return "a ${width}x$height picture needs a direct buffer of $needed bytes, not ${bgra.remaining()}"
        }
        val lib = BridgeNative.lib
        val result = lib.cpb_frame_publish(bgra, width, height, stride, safe.x, safe.y, safe.width, safe.height)
        if (result == 0) return null
        return lib.cpb_last_error()
    }

    override fun state(state: Int) = BridgeNative.lib.cpb_frame_state(state)

    override fun close() = BridgeNative.lib.cpb_frame_close()
}
