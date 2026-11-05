package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.media.MediaCodecSupport
import com.shilapi.xcertplay.airplay.VideoCodec
import org.bytedeco.ffmpeg.avcodec.AVCodec
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

/** H.264/HEVC decoder with D3D11 download to the existing renderer and software recovery on failure. */
class VideoDecoder internal constructor(
    private val onFrame: (DecodedFrame) -> Unit,
    private val onError: (String) -> Unit = {},
    private val videoCodec: VideoCodec = VideoCodec.H264,
    private val log: (String) -> Unit = {},
    private val useHardware: Boolean = true,
    private val hardwareFactory: (AVCodecContext, AVCodec) -> D3D11Video? = D3D11Video::open,
) : Closeable {
    private val codec = avcodec_find_decoder(if (videoCodec == VideoCodec.H264) AV_CODEC_ID_H264 else AV_CODEC_ID_HEVC)
        ?: error("FFmpeg has no $videoCodec decoder")
    private var hardware: D3D11Video? = null
    private var context = openContext(useHardware)
    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()
    private var parameterSets: ByteArray? = null
    private var prependParameters = false
    private var waitingForKeyframe = false
    /** The keyframe waited for has been asked for once ([onError]); the frames until it are dropped silently. */
    private var keyframeAsked = false
    private var reportedHardware: Boolean? = null
    private var closed = false
    internal val pool = FramePool()
    private val copier = VideoFrameCopy(pool)
    internal var hardwareFrames = 0L
        private set

    private fun openContext(tryHardware: Boolean): AVCodecContext {
        val ctx = avcodec_alloc_context3(codec)
        ctx.flags(ctx.flags() or AV_CODEC_FLAG_LOW_DELAY)
        ctx.thread_type(2) // FF_THREAD_SLICE: frame threading adds latency.
        ctx.thread_count(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        if (tryHardware) {
            try {
                hardware = hardwareFactory(ctx, codec)
            } catch (error: Exception) {
                log("$videoCodec D3D11 unavailable: ${error.message}")
                avcodec_free_context(ctx)
                hardware?.close()
                hardware = null
                return openContext(false)
            }
        }
        val result = avcodec_open2(ctx, codec, null as AVDictionary?)
        if (result < 0) {
            avcodec_free_context(ctx)
            if (hardware != null) {
                hardware?.close()
                hardware = null
                log("$videoCodec D3D11 open failed ($result); using software")
                return openContext(false)
            }
            error("cannot open the $videoCodec decoder ($result)")
        }
        return ctx
    }

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
        prependParameters = true
        // A configuration starts the stream again: frames before its keyframe refer to pictures this decoder never had.
        waitingForKeyframe = true
        keyframeAsked = false
        avcodec_flush_buffers(context)
    }

    @Synchronized
    fun decode(annexB: ByteArray) {
        if (closed) return
        if (waitingForKeyframe) {
            if (!MediaCodecSupport.isRandomAccess(annexB, videoCodec)) {
                // FFmpeg would drop it too, silently: the screen stays black unless the iPhone is asked for a keyframe.
                if (!keyframeAsked) onError("waiting for a keyframe")
                keyframeAsked = true
                return
            }
            waitingForKeyframe = false
        }
        val data = if (prependParameters) parameterSets?.let { it + annexB } ?: annexB else annexB
        prependParameters = false
        if (av_new_packet(packet, data.size) < 0) return onError("cannot allocate a packet of ${data.size} bytes")
        val sent = try {
            packet.data().put(data, 0, data.size)
            avcodec_send_packet(context, packet)
        } finally {
            av_packet_unref(packet)
        }
        val failure = if (sent < 0) "send_packet failed: ${errorText(sent)}" else drain()
        if (failure != null) {
            if (hardware == null) onError(failure) else {
                // Native calls have returned: it is now safe to close the context and its callback.
                avcodec_free_context(context)
                hardware?.close()
                hardware = null
                context = openContext(false)
                prependParameters = true
                waitingForKeyframe = true
                keyframeAsked = true
                log("$videoCodec D3D11 failed; using software: $failure")
                if (MediaCodecSupport.isRandomAccess(annexB, videoCodec)) decode(annexB)
                else onError("software decoder needs a keyframe after D3D11 failure")
            }
        }
    }

    private fun drain(): String? {
        while (true) {
            val received = avcodec_receive_frame(context, frame)
            if (received == AVERROR_EAGAIN() || received == AVERROR_EOF) return null
            if (received < 0) return "receive_frame failed: ${errorText(received)}"
            var picture: DecodedFrame? = null
            try {
                val onGpu = frame.format() == AV_PIX_FMT_D3D11
                val readable = if (onGpu) checkNotNull(hardware).download(frame) else frame
                picture = copier.copy(readable)
                if (onGpu) hardwareFrames++
                if (reportedHardware != onGpu) {
                    reportedHardware = onGpu
                    log("$videoCodec decoder: ${if (onGpu) "D3D11VA" else "software"}")
                }
            } catch (error: Exception) {
                return error.message ?: "cannot copy decoded video frame"
            } finally {
                av_frame_unref(frame)
            }
            // Consumer errors must not be mistaken for a hardware decoder failure.
            onFrame(checkNotNull(picture))
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        avcodec_free_context(context)
        hardware?.close()
        hardware = null
        av_packet_free(packet)
        av_frame_free(frame)
        copier.close()
    }

    private fun errorText(code: Int): String {
        val buffer = ByteArray(64)
        av_strerror(code, buffer, buffer.size.toLong())
        return String(buffer).trimEnd('\u0000') + " ($code)"
    }

    private companion object {
        val START_CODE = byteArrayOf(0, 0, 0, 1)
    }
}
