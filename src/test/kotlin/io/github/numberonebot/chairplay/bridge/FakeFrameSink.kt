package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.media.DecodedFrame
import io.github.numberonebot.chairplay.media.FramePool
import java.nio.ByteBuffer

internal class Published(val width: Int, val height: Int, val safe: AreaRect, val firstPixel: Int)

/** Records what a [FrameOutput] asks of the native side; a publish leaves state 2, as cpb_frame_publish does. */
internal class FakeFrameSink(
    private val openError: String? = null,
    private val publishError: String? = null,
    /** Thrown by each publish instead of returning. */
    private val publishThrows: Throwable? = null,
) : FrameSink {
    val events = mutableListOf<String>()
    val published = mutableListOf<Published>()
    var state = 0
    var onPublish: (ByteBuffer) -> Unit = {}

    override fun open(): String? {
        events += "open"
        if (openError == null) state = 1
        return openError
    }

    override fun publish(bgra: ByteBuffer, width: Int, height: Int, safe: AreaRect): String? {
        events += "publish"
        publishThrows?.let { throw it }
        published += Published(width, height, safe, bgra.getInt(0))
        onPublish(bgra)
        if (publishError == null) state = 2
        return publishError
    }

    override fun state(state: Int) {
        events += "state $state"
        this.state = state
    }

    override fun close() {
        events += "close"
        state = 0
    }

    fun count(event: String) = events.count { it == event }
}

/** A grey YUV420P picture of [luma], chroma neutral. */
internal fun testFrame(width: Int = 64, height: Int = 64, luma: Int = 16): DecodedFrame {
    val frame = FramePool().take(width, height)
    for (i in 0 until frame.y.capacity()) frame.y.put(i, luma.toByte())
    for (i in 0 until frame.u.capacity()) frame.u.put(i, 128.toByte())
    for (i in 0 until frame.v.capacity()) frame.v.put(i, 128.toByte())
    return frame
}
