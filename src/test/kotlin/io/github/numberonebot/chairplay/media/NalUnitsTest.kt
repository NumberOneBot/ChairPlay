package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import com.shilapi.xcertplay.media.MediaCodecSupport
import io.github.numberonebot.chairplay.phone.VideoCodecKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NalUnits.isRandomAccess decides, as DiPlay's MediaCodecSupport did, whether a decoder waiting may start at a frame. */
class NalUnitsTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }

    private val long = byteArrayOf(0, 0, 0, 1)
    private val short = byteArrayOf(0, 0, 1)
    private fun avc(type: Int, start: ByteArray = long) = start + byteArrayOf(type.toByte(), 0x11, 0x22)
    private fun hevc(type: Int, start: ByteArray = long) = start + byteArrayOf((type shl 1).toByte(), 1, 0x11)

    private val samples: Map<String, ByteArray> by lazy {
        buildMap {
            for (type in 0..31) {
                put("H.264 NAL $type", avc(type))
                put("H.264 NAL $type, 3-byte start", avc(type, short))
            }
            for (type in 0..63) {
                put("HEVC NAL $type", hevc(type))
                put("HEVC NAL $type, 3-byte start", hevc(type, short))
            }
            put("H.264 IDR after SPS and PPS", avc(7) + avc(8) + avc(5))
            put("H.264 non-IDR after SPS and PPS", avc(7) + avc(8) + avc(1))
            put("HEVC IDR after VPS, SPS and PPS", hevc(32) + hevc(33) + hevc(34) + hevc(19))
            put("HEVC CRA after VPS, SPS and PPS", hevc(32) + hevc(33) + hevc(34) + hevc(21))
            put("HEVC TRAIL after VPS, SPS and PPS", hevc(32) + hevc(33) + hevc(34) + hevc(1))
            put("HEVC header cut after its first byte", long + byteArrayOf((19 shl 1).toByte()))
            put("H.264 start code at the end", avc(1) + long)
            put("no start code", byteArrayOf(0x65, 0x26, 1, 2, 3))
            put("empty", ByteArray(0))
            put("zeros", ByteArray(16))
            for (n in 0..2) put("H.264 fixture $n", resource("/h264/frame$n.h264"))
            for (n in 0..2) put("HEVC fixture $n", resource("/hevc/frame$n.hevc"))
        }
    }

    @Test
    fun `NalUnits agrees with DiPlay`() {
        for ((name, annexB) in samples) {
            for ((kind, codec) in listOf(VideoCodecKind.H264 to VideoCodec.H264, VideoCodecKind.H265 to VideoCodec.H265)) {
                assertEquals("$name as $kind", MediaCodecSupport.isRandomAccess(annexB, codec), NalUnits.isRandomAccess(annexB, kind))
            }
        }
    }

    @Test
    fun `an IDR or an IRAP is a random access point`() {
        assertTrue(NalUnits.isRandomAccess(avc(7) + avc(8) + avc(5), VideoCodecKind.H264))
        assertFalse(NalUnits.isRandomAccess(avc(1), VideoCodecKind.H264))
        for (type in listOf(16, 19, 21)) assertTrue("$type", NalUnits.isRandomAccess(hevc(type), VideoCodecKind.H265))
        assertFalse(NalUnits.isRandomAccess(hevc(1), VideoCodecKind.H265))
    }
}
