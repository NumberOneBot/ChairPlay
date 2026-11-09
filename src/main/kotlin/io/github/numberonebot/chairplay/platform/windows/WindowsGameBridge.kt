package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.bridge.FrameSink
import io.github.numberonebot.chairplay.bridge.TelemetrySource
import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.platform.GameBridge
import java.nio.ByteBuffer

/** chairplay_bridge.dll through [BridgeNative]; it loads when the sink or the source is first opened. */
internal object WindowsGameBridge : GameBridge {
    override val frames: FrameSink = NativeFrameSink
    override val telemetry: TelemetrySource = NativeTelemetrySource
}

private object NativeFrameSink : FrameSink {
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

private object NativeTelemetrySource : TelemetrySource {
    override fun open(): String? {
        BridgeNative.loadError?.let { return it }
        val lib = BridgeNative.lib
        // The DLL keeps each thread's last failure: read it on this thread, right after the call.
        return if (lib.cpb_telemetry_open() == 0) null else lib.cpb_last_error()
    }

    override fun read(into: ByteBuffer): Boolean = BridgeNative.lib.cpb_telemetry_read(into) == 1

    override fun close() {
        if (BridgeNative.loadError == null) BridgeNative.lib.cpb_telemetry_close()
    }
}
