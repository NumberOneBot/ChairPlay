package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.Pointer
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class HardwareVideoTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }

    private fun requireD3d11() {
        val device = AVBufferRef(null as Pointer?)
        val result = av_hwdevice_ctx_create(device, AV_HWDEVICE_TYPE_D3D11VA, null as String?, null, 0)
        if (!device.isNull) av_buffer_unref(device)
        assumeTrue("D3D11VA is unavailable on this machine", result >= 0)
    }

    private fun playAvc(decoder: VideoDecoder) {
        decoder.configure(resource("/h264/avcC.bin"))
        repeat(3) { decoder.decode(resource("/h264/frame$it.h264")) }
    }

    @Test(timeout = 15000)
    fun `software HEVC keeps decoding after repeated stream configurations`() {
        var frames = 0
        val config = resource("/hevc/hvcC.bin")
        val packets = (0..2).map { resource("/hevc/frame$it.hevc") }
        VideoDecoder(onFrame = { frames++; it.release() }, videoCodec = VideoCodec.H265,
            useHardware = false).use { decoder ->
            repeat(100) {
                decoder.configure(config)
                packets.forEach(decoder::decode)
            }
            assertEquals(300, frames)
            assertEquals(0L, decoder.hardwareFrames)
        }
    }

    @Test
    fun `software selection never opens a GPU device`() {
        var frames = 0
        VideoDecoder(onFrame = { frames++; it.release() }, useHardware = false,
            hardwareFactory = { _, _ -> error("GPU must not be opened") }).use {
            playAvc(it)
            assertEquals(3, frames)
            assertEquals(0L, it.hardwareFrames)
        }
    }

    @Test
    fun `missing or failed device creation still decodes all pictures in software`() {
        for (throws in listOf(false, true)) {
            var frames = 0
            VideoDecoder(onFrame = { frames++; it.release() }, hardwareFactory = { _, _ ->
                if (throws) error("simulated device creation failure") else null
            }).use {
                playAvc(it)
                assertEquals(3, frames)
                assertEquals(0L, it.hardwareFrames)
            }
        }
    }

    @Test
    fun `failed hardware keyframe is retried immediately in software`() {
        requireD3d11()
        var frames = 0
        var failedTransfers = 0
        VideoDecoder(onFrame = { frames++; it.release() }, hardwareFactory = { context, codec ->
            D3D11Video.open(context, codec) { _, _ -> failedTransfers++; -1 }
        }).use {
            playAvc(it)
            assertEquals(1, failedTransfers)
            assertEquals(3, frames)
            assertEquals(0L, it.hardwareFrames)
        }
    }

    @Test
    fun `hardware failure on a delta frame waits for a requested keyframe`() {
        requireD3d11()
        var frames = 0
        var transfers = 0
        var recoveries = 0
        VideoDecoder(onFrame = { frames++; it.release() }, onError = { recoveries++ },
            hardwareFactory = { context, codec ->
                D3D11Video.open(context, codec) { destination, source ->
                    if (++transfers == 2) -1 else av_hwframe_transfer_data(destination, source, 0)
                }
            }).use { decoder ->
            playAvc(decoder)
            assertEquals(1, frames)
            assertEquals(1, recoveries)
            repeat(3) { decoder.decode(resource("/h264/frame$it.h264")) }
            assertEquals(4, frames)
            assertEquals(1L, decoder.hardwareFrames)
        }
    }

    @Test
    fun `H264 and HEVC pictures are decoded on D3D11 and copied to the renderer`() {
        requireD3d11()
        for (codec in VideoCodec.entries) {
            val hevc = codec == VideoCodec.H265
            val dir = if (hevc) "hevc" else "h264"
            var frames = 0
            VideoDecoder(onFrame = { frame ->
                frames++
                assertEquals(320, frame.width)
                assertEquals(240, frame.height)
                if (hevc) {
                    assertEquals(81.0, (frame.y.get(0).toInt() and 255).toDouble(), 3.0)
                    assertEquals(90.0, (frame.u.get(0).toInt() and 255).toDouble(), 3.0)
                    assertEquals(240.0, (frame.v.get(0).toInt() and 255).toDouble(), 3.0)
                }
                frame.release()
            }, videoCodec = codec).use { decoder ->
                decoder.configure(resource("/$dir/${if (hevc) "hvcC" else "avcC"}.bin"))
                repeat(3) { decoder.decode(resource("/$dir/frame$it.${if (hevc) "hevc" else "h264"}")) }
                assertEquals(3, frames)
                assertEquals(3L, decoder.hardwareFrames)
            }
        }
    }
}
