package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.VideoCodec
import io.github.numberonebot.chairplay.bridge.FrameTap
import io.github.numberonebot.chairplay.media.AudioRenderer
import io.github.numberonebot.chairplay.media.DecodedFrame
import io.github.numberonebot.chairplay.media.ScreenDecoders
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.Collections
import java.util.zip.CRC32

/**
 * Characterization: one scripted CarPlay session of both screens through the sink, and what comes out of it: each
 * picture (screen, size, checksum of its planes) and stream event as the frame tap sees them, and the keyframes asked
 * for. The expected transcript was recorded from DesktopMediaSink, the screens and the sink in one class, before the
 * screens moved into ScreenDecoders: the move changed nothing the iPhone or the frame tap can see.
 */
class CarPlayMediaSinkTranscriptTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
    private val avcC = resource("/h264/avcC.bin")
    private val hvcC = resource("/hevc/hvcC.bin")
    private fun avc(n: Int) = resource("/h264/frame$n.h264")
    private fun hevc(n: Int) = resource("/hevc/frame$n.hevc")

    private class Transcript : FrameTap, Closeable {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun frame(screen: ScreenKind, frame: DecodedFrame) {
            val crc = CRC32()
            listOf(frame.y, frame.u, frame.v).forEach { crc.update(it.duplicate() as ByteBuffer) }
            events += "frame ${screen.name.lowercase()} ${frame.width}x${frame.height} ${crc.value}"
        }
        override fun active(screen: ScreenKind, active: Boolean) { events += "active ${screen.name.lowercase()} $active" }
        override fun close() { events += "close" }
    }

    private fun session(sink: MediaSink, tap: Transcript): List<String> {
        val recoveries = Collections.synchronizedList(mutableListOf<String>())
        sink.setVideoRecoveryHandler(110) { recoveries += "recover main" }
        sink.setVideoRecoveryHandler(111) { recoveries += "recover cluster" }
        sink.onScreenStreamActive(110, true)
        sink.onVideoCodec(110, VideoCodec.H264)
        sink.onVideoConfig(110, avcC)
        (0..2).forEach { sink.onVideoFrame(110, avc(it), 0, 0) }
        // The same configuration again keeps the decoder, and asks for no keyframe.
        sink.onVideoConfig(110, avcC)
        sink.onVideoFrame(110, avc(1), 0, 0)
        // HEVC, its first frame no keyframe: dropped, and a keyframe asked for.
        sink.onVideoCodec(110, VideoCodec.H265)
        sink.onVideoConfig(110, hvcC)
        sink.onVideoFrame(110, hevc(1), 0, 0)
        (0..2).forEach { sink.onVideoFrame(110, hevc(it), 0, 0) }
        sink.onScreenStreamActive(111, true)
        sink.onVideoCodec(111, VideoCodec.H265)
        sink.onVideoConfig(111, hvcC)
        (0..2).forEach { sink.onVideoFrame(111, hevc(it), 0, 0) }
        // A new codec closes the decoder: nothing decodes until the next configuration.
        sink.onVideoCodec(111, VideoCodec.H264)
        sink.onVideoFrame(111, avc(0), 0, 0)
        sink.onVideoConfig(111, avcC)
        (0..2).forEach { sink.onVideoFrame(111, avc(it), 0, 0) }
        sink.onScreenStreamActive(110, false)
        sink.onVideoFrame(110, hevc(0), 0, 0)
        // A configuration that holds no parameter sets.
        sink.onVideoConfig(111, byteArrayOf(1, 2, 3))
        sink.onVideoFrame(111, avc(0), 0, 0)
        sink.onVideoFrame(112, avc(0), 0, 0)
        Thread.sleep(300)
        (sink as Closeable).close()
        return tap.events.toList() + recoveries.toList()
    }

    @Test
    fun `a session of both screens gives the recorded transcript`() {
        val tap = Transcript()
        val windows = mapOf(ScreenKind.MAIN to VideoWindow(320, 240), ScreenKind.CLUSTER to VideoWindow(320, 240))
        val sink = CarPlayMediaSink(ScreenDecoders(windows, tap, hardwareDecoding = { false }), AudioRenderer())
        assertEquals(EXPECTED.joinToString("\n"), session(sink, tap).joinToString("\n"))
    }

    private companion object {
        const val AVC0 = "320x240 1407677232"
        const val AVC1 = "320x240 2848515081"
        const val AVC2 = "320x240 3758022002"
        const val RED = "320x240 4048791284"
        val EXPECTED = listOf(
            "active main true",
            "frame main $AVC0", "frame main $AVC1", "frame main $AVC2",
            "frame main $RED", "frame main $RED", "frame main $RED",
            "active cluster true",
            "frame cluster $RED", "frame cluster $RED", "frame cluster $RED",
            "frame cluster $AVC0", "frame cluster $AVC1", "frame cluster $AVC2",
            "active main false",
            "close",
            "recover main",
            "recover cluster",
        )
    }
}
