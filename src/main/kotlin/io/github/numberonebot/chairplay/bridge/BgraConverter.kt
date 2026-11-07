package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.media.DecodedFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer
import java.io.Closeable
import java.nio.ByteBuffer

/** Turns a decoded picture into packed BGRA, stride `width * 4`, in a direct buffer of at least that size. */
internal interface PictureConverter : Closeable {
    /** Fills [out] with the picture and returns it. */
    fun convert(frame: DecodedFrame, out: ByteBuffer): ByteBuffer
}

/**
 * Turns a decoded YUV420P picture into packed BGRA the way the window draws it: BT.709 limited range in,
 * full range out (see `PictureGl`). Not thread safe; the video decoder thread owns it.
 */
internal class BgraConverter : PictureConverter {
    private var scaler: SwsContext? = null
    private var out: ByteBuffer? = null
    private var address = 0L
    private var width = 0
    private var height = 0

    /** How often the colour space was configured; once per size, not per frame. */
    internal var setups = 0
        private set

    /** A direct buffer of `width * height * 4`, the same one while the size holds. */
    fun buffer(width: Int, height: Int): ByteBuffer {
        val size = width * height * 4
        out?.let { if (it.capacity() == size) return it }
        return ByteBuffer.allocateDirect(size).also { out = it }
    }

    /** Fills [out] (from [buffer]) with the picture and returns it; stride is `width * 4`. */
    override fun convert(frame: DecodedFrame, out: ByteBuffer): ByteBuffer {
        require(out.isDirect && out.capacity() >= frame.width * frame.height * 4) { "output buffer too small" }
        val context = sws_getCachedContext(scaler, frame.width, frame.height, AV_PIX_FMT_YUV420P,
            frame.width, frame.height, AV_PIX_FMT_BGRA, FLAGS, null, null, null as DoublePointer?)
        // A changed size frees the previous native context inside the call, so never keep a pointer to it.
        if (context == null || context.isNull) {
            scaler = null
            error("cannot convert video to BGRA")
        }
        // JavaCPP wraps the result anew each call: compare the native address (and size, as addresses can be reused).
        if (context.address() != address || frame.width != width || frame.height != height) {
            val table = sws_getCoefficients(SWS_CS_ITU709)
            scaler = context
            check(sws_setColorspaceDetails(context, table, 0, table, 1, 0, 1 shl 16, 1 shl 16) >= 0) {
                "cannot set the BT.709 colour space"
            }
            address = context.address(); width = frame.width; height = frame.height
            setups++
        }
        val planes = PointerPointer<BytePointer>(3L)
            .put(0, BytePointer(frame.y)).put(1, BytePointer(frame.u)).put(2, BytePointer(frame.v))
        val sourceStrides = IntPointer(*frame.strides)
        val target = PointerPointer<BytePointer>(1L).put(0, BytePointer(out))
        val targetStride = IntPointer(frame.width * 4)
        try {
            check(sws_scale(context, planes, sourceStrides, 0, frame.height, target, targetStride) == frame.height) {
                "incomplete BGRA frame"
            }
        } finally {
            planes.close(); sourceStrides.close(); target.close(); targetStride.close()
        }
        return out
    }

    override fun close() {
        scaler?.let { sws_freeContext(it) }
        scaler = null
    }

    private companion object {
        // Same size in and out, so only chroma is upsampled: accurate rounding and full horizontal chroma interpolation.
        const val FLAGS = SWS_BILINEAR or SWS_ACCURATE_RND or SWS_FULL_CHR_H_INT
    }
}
