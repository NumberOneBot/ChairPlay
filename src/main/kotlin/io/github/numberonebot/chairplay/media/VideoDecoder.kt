package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.media.MediaCodecSupport
import com.shilapi.xcertplay.airplay.VideoCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.*
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * One decoded picture as tightly packed YUV420P planes (no row padding, so [strides] equal the plane
 * widths). The planes are copies: the decoder reuses its own frame, and the picture is handed to
 * another thread. That thread calls [release] once it no longer reads the planes; the decoder may then
 * fill them with a later picture. A frame never released is simply left to the GC.
 */
class DecodedFrame internal constructor(
    val width: Int,
    val height: Int,
    val y: ByteBuffer,
    val u: ByteBuffer,
    val v: ByteBuffer,
    val strides: IntArray,
    private val pool: FramePool,
) {
    fun release() = pool.recycle(this)
}

/**
 * Pictures handed back through [DecodedFrame.release], for the decoder to fill again. Direct memory is
 * returned only when the GC gets to a buffer: three fresh planes per picture were some 83 MB/s at
 * 1280x720x60, growing the process and forcing System.gc pauses on the way. A frame is reused only
 * after its consumer released it, so the decoder never writes planes another thread may still read.
 */
internal class FramePool {
    private val free = ArrayDeque<DecodedFrame>()

    /** Frames made because none of the right size was free. */
    @Volatile
    var allocations = 0
        private set

    @Synchronized
    fun take(width: Int, height: Int): DecodedFrame {
        // After a size change the old pictures can never be refilled; dropping them lets the GC have them.
        free.removeAll { it.width != width || it.height != height }
        free.removeFirstOrNull()?.let { return it }
        allocations++
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        return DecodedFrame(
            width, height,
            ByteBuffer.allocateDirect(width * height),
            ByteBuffer.allocateDirect(chromaWidth * chromaHeight),
            ByteBuffer.allocateDirect(chromaWidth * chromaHeight),
            intArrayOf(width, chromaWidth, chromaWidth),
            this,
        )
    }

    @Synchronized
    fun recycle(frame: DecodedFrame) {
        free.addLast(frame)
    }
}

/**
 * Software H.264/HEVC decoder over FFmpeg. Not thread-safe by design: it is driven from the one thread
 * that reads the screen stream, and [onFrame] runs on that thread.
 *
 * [onError] reports a packet the decoder rejected (or a picture format it cannot show). The caller
 * answers by asking the iPhone for a new keyframe.
 */
class VideoDecoder(
    private val onFrame: (DecodedFrame) -> Unit,
    private val onError: (String) -> Unit = {},
    private val videoCodec: VideoCodec = VideoCodec.H264,
) : Closeable {
    private val context: AVCodecContext
    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()
    private var parameterSets: ByteArray? = null
    private var closed = false

    /** Released pictures come back here; one decoder per stream, so a new stream starts empty. */
    internal val pool = FramePool()

    init {
        val codecId = when (videoCodec) {
            VideoCodec.H264 -> AV_CODEC_ID_H264
            VideoCodec.H265 -> AV_CODEC_ID_HEVC
        }
        val codec = avcodec_find_decoder(codecId) ?: error("FFmpeg has no $videoCodec decoder")
        context = avcodec_alloc_context3(codec)
        // Low delay and slice threading: frame threading would hold pictures back for several frames.
        context.flags(context.flags() or AV_CODEC_FLAG_LOW_DELAY)
        context.thread_type(FF_THREAD_SLICE)
        context.thread_count(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        check(avcodec_open2(context, codec, null as AVDictionary?) >= 0) { "cannot open the $videoCodec decoder" }
    }

    /**
     * Takes parameter sets from avcC or hvcC. Frames arrive in Annex B without them, so they are put in
     * front of the next frame; a new configuration also drops the pictures of the previous stream.
     */
    @Synchronized
    fun configure(codecData: ByteArray) {
        if (closed) return
        parameterSets = when (videoCodec) {
            VideoCodec.H264 -> {
                val (sps, pps) = MediaCodecSupport.avcParameterSets(codecData)
                if (sps.isEmpty() || pps.isEmpty()) null else START_CODE + sps + START_CODE + pps
            }
            VideoCodec.H265 -> MediaCodecSupport.hevcCodecSpecificData(codecData).takeIf { it.isNotEmpty() }
        }
        avcodec_flush_buffers(context)
    }

    @Synchronized
    fun decode(annexB: ByteArray) {
        if (closed) return
        val data = parameterSets?.let { it + annexB } ?: annexB
        parameterSets = null
        if (av_new_packet(packet, data.size) < 0) return onError("cannot allocate a packet of ${data.size} bytes")
        try {
            packet.data().put(data, 0, data.size)
            val sent = avcodec_send_packet(context, packet)
            if (sent < 0) onError("send_packet failed: ${errorText(sent)}")
        } finally {
            av_packet_unref(packet)
        }
        drain()
    }

    private fun drain() {
        while (true) {
            val received = avcodec_receive_frame(context, frame)
            if (received == AVERROR_EAGAIN() || received == AVERROR_EOF) return
            if (received < 0) return onError("receive_frame failed: ${errorText(received)}")
            try {
                if (frame.format() != AV_PIX_FMT_YUV420P && frame.format() != AV_PIX_FMT_YUVJ420P) {
                    onError("unsupported pixel format ${frame.format()}")
                } else {
                    onFrame(copyOut())
                }
            } finally {
                av_frame_unref(frame)
            }
        }
    }

    private fun copyOut(): DecodedFrame {
        val width = frame.width()
        val height = frame.height()
        val picture = pool.take(width, height)
        val chromaHeight = (height + 1) / 2
        fill(picture.y, 0, picture.strides[0], height)
        fill(picture.u, 1, picture.strides[1], chromaHeight)
        fill(picture.v, 2, picture.strides[2], chromaHeight)
        return picture
    }

    private fun fill(packed: ByteBuffer, index: Int, width: Int, height: Int) {
        val stride = frame.linesize(index)
        val source = frame.data(index).capacity(stride.toLong() * (height - 1) + width).asByteBuffer()
        packed.clear()
        for (row in 0 until height) {
            source.limit(row * stride + width).position(row * stride)
            packed.put(source)
        }
        packed.flip()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        // The free functions take pointer-to-pointer and null the handle they are given.
        avcodec_free_context(context)
        av_packet_free(packet)
        av_frame_free(frame)
    }

    private fun errorText(code: Int): String {
        val buffer = ByteArray(64)
        av_strerror(code, buffer, buffer.size.toLong())
        return String(buffer).trimEnd('\u0000') + " ($code)"
    }

    private companion object {
        val START_CODE = byteArrayOf(0, 0, 0, 1)

        // libavcodec's FF_THREAD_SLICE macro, which the presets do not export.
        const val FF_THREAD_SLICE = 2
    }
}
