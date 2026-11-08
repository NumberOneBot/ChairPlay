package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import io.github.numberonebot.chairplay.carplay.annexBParameterSets
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDecoderTest {
    private fun resource(name: String) =
        javaClass.getResourceAsStream("/h264/$name")!!.use { it.readBytes() }

    @Test
    fun `decodes annex-b stream with the sps-pps of avcC`() {
        val frames = mutableListOf<DecodedFrame>()
        VideoDecoder(onFrame = { frames += it }).use { decoder ->
            decoder.configure(annexBParameterSets(resource("avcC.bin"), VideoCodec.H264))
            repeat(3) { decoder.decode(resource("frame$it.h264")) }
        }
        assertEquals(3, frames.size)
        frames.forEach {
            assertEquals(320, it.width)
            assertEquals(240, it.height)
            assertEquals(320 * 240, it.y.remaining())
            assertEquals(160 * 120, it.u.remaining())
            assertEquals(160 * 120, it.v.remaining())
        }
    }

    // A configuration starts the stream again: a frame before its keyframe refers to pictures this decoder never had, so
    // it is dropped, and the decoder asks for a keyframe instead of staying black until the iPhone sends one.
    @Test
    fun `a frame before the first keyframe asks for one`() {
        val frames = mutableListOf<DecodedFrame>()
        val errors = mutableListOf<String>()
        VideoDecoder(onFrame = { frames += it }, recover = { errors += it }).use { decoder ->
            decoder.configure(annexBParameterSets(resource("avcC.bin"), VideoCodec.H264))
            decoder.decode(resource("frame1.h264"))
            assertEquals(0, frames.size)
            assertEquals(1, errors.size)

            decoder.decode(resource("frame0.h264"))
            decoder.decode(resource("frame1.h264"))
        }
        assertEquals(2, frames.size)
        assertEquals(1, errors.size)
    }

    @Test
    fun `released pictures are refilled instead of allocated`() {
        var shown = 0
        VideoDecoder(onFrame = { shown++; it.release() }).use { decoder ->
            decoder.configure(annexBParameterSets(resource("avcC.bin"), VideoCodec.H264))
            repeat(3) { decoder.decode(resource("frame$it.h264")) }
            assertEquals(3, shown)
            assertEquals(1, decoder.pool.allocations)
        }
    }
}
