package io.github.numberonebot.chairplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import io.github.numberonebot.chairplay.bridge.FrameTap
import io.github.numberonebot.chairplay.carplay.annexBParameterSets
import io.github.numberonebot.chairplay.phone.ScreenKind
import io.github.numberonebot.chairplay.phone.VideoCodecKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A VideoWindow that never runs opens no window, so the decoders are tested with real ones. */
class ScreenDecodersTest {
    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
    private val avcParameterSets = annexBParameterSets(resource("/h264/avcC.bin"), VideoCodec.H264)
    private fun avc(n: Int) = resource("/h264/frame$n.h264")

    private class RecordingTap : FrameTap {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        var request: () -> Unit = {}
        override fun frame(screen: ScreenKind, frame: DecodedFrame) { events += "frame $screen" }
        override fun active(screen: ScreenKind, active: Boolean) { events += "active $screen $active" }
        override fun attach(requestPicture: () -> Unit) { request = requestPicture }
    }

    private val tap = RecordingTap()
    private val main = VideoWindow(320, 240)
    private val cluster = VideoWindow(320, 240)
    private val screens = ScreenDecoders(mapOf(ScreenKind.MAIN to main, ScreenKind.CLUSTER to cluster), tap,
        hardwareDecoding = { false })

    private fun startAvc(screen: ScreenKind) {
        screens.selectCodec(screen, VideoCodecKind.H264)
        screens.configure(screen, avcParameterSets)
        screens.decode(screen, avc(0))
    }

    @Test
    fun `a new codec closes the decoder`() {
        startAvc(ScreenKind.MAIN)
        screens.selectCodec(ScreenKind.MAIN, VideoCodecKind.H264)
        screens.decode(ScreenKind.MAIN, avc(1))
        assertEquals(2, tap.events.count { it == "frame MAIN" })

        screens.selectCodec(ScreenKind.MAIN, VideoCodecKind.H265)
        screens.decode(ScreenKind.MAIN, avc(2))
        screens.close()
        assertEquals(2, tap.events.count { it == "frame MAIN" })
    }

    // The AltScreen's state goes to the iPhone once per stream it sets up, on that stream's first picture: not on each
    // picture, nor on a decoder configured again, which a view asked for can bring and would ask again, in a loop.
    @Test
    fun `alt screen first frame sets up the altscreen once`() {
        var reported = 0
        screens.onClusterStream = { reported++ }

        cluster.onFrameShown()
        screens.active(ScreenKind.CLUSTER, true)
        cluster.onFrameShown()
        cluster.onFrameShown()
        main.onFrameShown()
        assertEquals(1, reported)

        screens.active(ScreenKind.MAIN, true)
        main.onFrameShown()
        assertEquals(1, reported)
        screens.active(ScreenKind.CLUSTER, true)
        cluster.onFrameShown()
        assertEquals(2, reported)
        screens.close()
    }

    // The decoder waiting for a keyframe and the bridge asking for a picture share the one limit of the screen.
    @Test
    fun `keyframe requests are at most one a second`() {
        val asked = AtomicInteger()
        val first = CountDownLatch(1)
        screens.setRecoveryHandler(ScreenKind.MAIN) { asked.incrementAndGet(); first.countDown() }
        val clusterAsked = CountDownLatch(1)
        screens.setRecoveryHandler(ScreenKind.CLUSTER) { clusterAsked.countDown() }
        screens.selectCodec(ScreenKind.MAIN, VideoCodecKind.H264)
        screens.configure(ScreenKind.MAIN, avcParameterSets)
        screens.decode(ScreenKind.MAIN, avc(1))
        assertTrue("never requested", first.await(5, TimeUnit.SECONDS))
        repeat(3) { tap.request() }
        screens.selectCodec(ScreenKind.CLUSTER, VideoCodecKind.H264)
        screens.configure(ScreenKind.CLUSTER, avcParameterSets)
        screens.decode(ScreenKind.CLUSTER, avc(1))
        assertTrue("the main screen's limit held the cluster's request", clusterAsked.await(5, TimeUnit.SECONDS))
        Thread.sleep(200)
        assertEquals(1, asked.get())

        Thread.sleep(1_000)
        tap.request()
        Thread.sleep(200)
        assertEquals(2, asked.get())
        screens.close()
    }

    // display.share: the tap reads each picture of each screen before its window takes it, and hears of a stop only once
    // the decoder is closed, so no picture follows it.
    @Test
    fun `the frame tap sees main screen frames`() {
        screens.active(ScreenKind.MAIN, true)
        startAvc(ScreenKind.MAIN)
        screens.decode(ScreenKind.MAIN, avc(1))
        screens.active(ScreenKind.MAIN, false)
        screens.decode(ScreenKind.MAIN, avc(2))

        assertEquals(listOf("active MAIN true", "frame MAIN", "frame MAIN", "active MAIN false"), tap.events.toList())
        screens.close()
    }

    @Test
    fun `a screen without a window is ignored`() {
        val only = ScreenDecoders(mapOf(ScreenKind.MAIN to VideoWindow(320, 240)), tap, hardwareDecoding = { false })
        only.selectCodec(ScreenKind.CLUSTER, VideoCodecKind.H264)
        only.configure(ScreenKind.CLUSTER, avcParameterSets)
        only.decode(ScreenKind.CLUSTER, avc(0))
        only.close()
        screens.close()
        assertEquals(emptyList<String>(), tap.events.toList())
    }
}
