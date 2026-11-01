package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class HevcVideoTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }

    // Observe the decoded picture handed to the real window, without opening a native window in a unit test.
    @Suppress("UNCHECKED_CAST")
    private fun pending(window: VideoWindow): AtomicReference<DecodedFrame?> =
        VideoWindow::class.java.getDeclaredField("latest").apply { isAccessible = true }
            .get(window) as AtomicReference<DecodedFrame?>

    @Test
    fun `both screens decode HEVC and can switch back to AVC`() {
        val main = VideoWindow(320, 240)
        val alt = VideoWindow(320, 240)
        DesktopMediaSink(main, AudioRenderer(), altscreen = alt).use { sink ->
            for ((type, window) in listOf(110 to main, 111 to alt)) {
                for (codec in listOf(VideoCodec.H265, VideoCodec.H264, VideoCodec.H265)) {
                    val hevc = codec == VideoCodec.H265
                    val dir = if (hevc) "hevc" else "h264"
                    sink.onVideoCodec(type, codec)
                    sink.onVideoConfig(type, resource("/$dir/${if (hevc) "hvcC" else "avcC"}.bin"))
                    repeat(3) { index ->
                        sink.onVideoFrame(type, resource("/$dir/frame$index.${if (hevc) "hevc" else "h264"}"), 0, 0)
                        val frame = pending(window).getAndSet(null)
                        assertNotNull("No $codec picture for screen $type, frame $index", frame)
                        frame!!
                        try {
                            assertEquals(if (hevc) 64 else 320, frame.width)
                            assertEquals(if (hevc) 48 else 240, frame.height)
                            if (hevc) {
                                // The fixture is solid red in limited-range YUV420, not merely a valid frame size.
                                assertEquals(81.0, (frame.y.get(0).toInt() and 255).toDouble(), 3.0)
                                assertEquals(90.0, (frame.u.get(0).toInt() and 255).toDouble(), 3.0)
                                assertEquals(240.0, (frame.v.get(0).toInt() and 255).toDouble(), 3.0)
                            }
                        } finally {
                            frame.release()
                        }
                    }
                }
            }
        }
    }
}
