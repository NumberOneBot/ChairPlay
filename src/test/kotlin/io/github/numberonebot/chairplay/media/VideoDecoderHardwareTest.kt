package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import io.github.numberonebot.chairplay.carplay.annexBParameterSets
import io.github.numberonebot.chairplay.platform.Platforms
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.Pointer
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class VideoDecoderHardwareTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }

    private fun play(decoder: VideoDecoder) {
        decoder.configure(annexBParameterSets(resource("/h264/avcC.bin"), VideoCodec.H264))
        repeat(3) { decoder.decode(resource("/h264/frame$it.h264")) }
    }

    @Test
    fun `no hardware video never asks for a GPU device`() {
        var frames = 0
        VideoDecoder(onFrame = { frames++; it.release() }, video = null,
            hardwareFactory = { _, _ -> error("GPU must not be opened") }).use {
            play(it)
            assertEquals(3, frames)
            assertEquals(0L, it.hardwareFrames)
        }
    }

    @Test
    fun `the decoder's hardware log lines are today's`() {
        val unavailable = mutableListOf<String>()
        VideoDecoder(onFrame = { it.release() }, log = { unavailable += it },
            hardwareFactory = { _, _ -> null }).use { play(it) }
        assertEquals(listOf("H264 decoder: software"), unavailable)

        val thrown = mutableListOf<String>()
        VideoDecoder(onFrame = { it.release() }, log = { thrown += it },
            hardwareFactory = { _, _ -> error("no device") }).use { play(it) }
        assertEquals(listOf("H264 D3D11 unavailable: no device", "H264 decoder: software"), thrown)

        val device = AVBufferRef(null as Pointer?)
        val created = av_hwdevice_ctx_create(device, AV_HWDEVICE_TYPE_D3D11VA, null as String?, null, 0)
        if (!device.isNull) av_buffer_unref(device)
        assumeTrue("D3D11VA is unavailable on this machine", created >= 0)
        val failed = mutableListOf<String>()
        VideoDecoder(onFrame = { it.release() }, log = { failed += it },
            hardwareFactory = { context, codec -> FfmpegHardware.open(context, codec, Platforms.current.video!!) { _, _ -> -1 } }).use { play(it) }
        assertEquals(listOf("H264 D3D11 failed; using software: D3D11 frame transfer failed (-1)", "H264 decoder: software"), failed)
    }
}
