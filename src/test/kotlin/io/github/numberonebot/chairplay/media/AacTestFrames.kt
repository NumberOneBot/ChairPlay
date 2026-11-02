package io.github.numberonebot.chairplay.media

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.*
import kotlin.math.PI
import kotlin.math.sin

/** Raw AAC access units from FFmpeg's own encoder, one per 1024-sample frame. */
internal fun encodeAac(rate: Int, channels: Int, count: Int = 1): List<ByteArray> {
    av_log_set_level(AV_LOG_ERROR)
    val codec = avcodec_find_encoder(AV_CODEC_ID_AAC)
    val context: AVCodecContext = avcodec_alloc_context3(codec)
    val frame: AVFrame = av_frame_alloc()
    val packet: AVPacket = av_packet_alloc()
    try {
        context.sample_fmt(AV_SAMPLE_FMT_FLTP)
        context.sample_rate(rate)
        context.bit_rate(96_000)
        av_channel_layout_default(context.ch_layout(), channels)
        check(avcodec_open2(context, codec, null as AVDictionary?) >= 0)
        frame.nb_samples(1024)
        frame.format(AV_SAMPLE_FMT_FLTP)
        frame.sample_rate(rate)
        av_channel_layout_default(frame.ch_layout(), channels)
        check(av_frame_get_buffer(frame, 0) >= 0)
        val units = ArrayList<ByteArray>()
        var tick = 0
        while (units.size < count && tick < 16 + count) {
            check(av_frame_make_writable(frame) >= 0)
            for (c in 0 until channels) {
                val samples = frame.data(c).capacity(1024L * 4).asByteBuffer().asFloatBuffer()
                for (i in 0 until 1024) samples.put(i, sin(2 * PI * 440 * (tick * 1024 + i) / rate).toFloat() * 0.3f)
            }
            frame.pts(tick * 1024L)
            check(avcodec_send_frame(context, frame) >= 0)
            while (avcodec_receive_packet(context, packet) >= 0) {
                val bytes = ByteArray(packet.size())
                packet.data().get(bytes)
                units += bytes
                av_packet_unref(packet)
            }
            tick++
        }
        return units
    } finally {
        avcodec_free_context(context)
        av_frame_free(frame)
        av_packet_free(packet)
    }
}
