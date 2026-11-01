package io.github.numberonebot.chairplay.media

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.DoublePointer
import java.io.Closeable
import java.nio.ByteBuffer

/** Downloads are usually NV12 (or P010 for Main 10); keep the renderer's existing packed YUV420 planes. */
internal class VideoFrameCopy(private val pool: FramePool) : Closeable {
    private var scaler: SwsContext? = null
    private val converted = av_frame_alloc()

    fun copy(source: AVFrame): DecodedFrame {
        val frame = if (source.format() == AV_PIX_FMT_YUV420P || source.format() == AV_PIX_FMT_YUVJ420P) source else {
            if (converted.width() != source.width() || converted.height() != source.height()) {
                av_frame_unref(converted)
                converted.format(AV_PIX_FMT_YUV420P).width(source.width()).height(source.height())
                check(av_frame_get_buffer(converted, 32) >= 0) { "cannot allocate converted video frame" }
            }
            scaler = sws_getCachedContext(scaler, source.width(), source.height(), source.format(),
                source.width(), source.height(), AV_PIX_FMT_YUV420P, SWS_BILINEAR, null, null, null as DoublePointer?)
            check(scaler != null && !scaler!!.isNull) { "cannot convert video pixel format ${source.format()}" }
            check(sws_scale(scaler, source.data(), source.linesize(), 0, source.height(),
                converted.data(), converted.linesize()) == source.height()) { "incomplete converted video frame" }
            converted
        }
        val picture = pool.take(frame.width(), frame.height())
        fill(frame, picture.y, 0, picture.strides[0], frame.height())
        fill(frame, picture.u, 1, picture.strides[1], (frame.height() + 1) / 2)
        fill(frame, picture.v, 2, picture.strides[2], (frame.height() + 1) / 2)
        return picture
    }

    private fun fill(frame: AVFrame, packed: ByteBuffer, plane: Int, width: Int, height: Int) {
        val stride = frame.linesize(plane)
        val source = frame.data(plane).capacity(stride.toLong() * (height - 1) + width).asByteBuffer()
        packed.clear()
        for (row in 0 until height) {
            source.limit(row * stride + width).position(row * stride)
            packed.put(source)
        }
        packed.flip()
    }

    override fun close() {
        scaler?.let { sws_freeContext(it) }
        av_frame_free(converted)
    }
}
