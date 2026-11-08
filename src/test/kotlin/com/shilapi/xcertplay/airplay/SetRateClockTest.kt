package com.shilapi.xcertplay.airplay

import io.github.numberonebot.chairplay.media.apac.ApacNativeLibrary
import io.github.numberonebot.chairplay.media.apac.SpatialAudioBridge
import io.github.numberonebot.chairplay.media.apac.SpatialAudioSink
import io.github.numberonebot.chairplay.media.apac.SpatialPlan
import io.github.numberonebot.chairplay.media.apac.TestSpatialDevice
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress

/**
 * A SETRATE that comes right after the session's SETUP (ChairPlay connecting while music plays) is answered on the
 * clock synchronized with the iPhone; the wall clock in its anchor leaves buffered music silent.
 */
class SetRateClockTest {
    @Test fun `an early play command is anchored on the iPhone's clock`() {
        FakeTimingPeer().use { peer ->
            val engine = CarPlayMediaEngine(object : MediaSink {})
            session(engine, spatial = false).use { session ->
                setup(session, peer)
                assertNotNull(engine.onBufferedAudio(session, mapOf("type" to 103, "shk" to ByteArray(32),
                    "audioFormat" to 0x800000, "ct" to 4, "spf" to 1024, "streamConnectionID" to 1)))
                assertOnPhoneClock(anchorSeconds(handle(session, "SETRATE", mapOf("rate" to 1L, "rtpTime" to 0L))))
            }
        }
    }

    @Test fun `an early spatial play command is anchored on the iPhone's clock`() {
        // The test task builds the decoder (apacDll); SETUP loads no stream into it.
        val dll = Path.of(System.getProperty("chairplay.apac.testLibrary") ?: "build/native/chairplay_apac.dll")
        assumeTrue("no APAC decoder at $dll", Files.isRegularFile(dll))
        val native = ApacNativeLibrary.load(dll)
        FakeTimingPeer().use { peer ->
            SpatialAudioBridge({ SpatialPlan(native, "test", 2) }, { 1f }, { 1f }, {}, {}, { TestSpatialDevice() }).use { bridge ->
                val engine = CarPlayMediaEngine(object : MediaSink, SpatialAudioSink { override val spatialAudio = bridge })
                session(engine, spatial = true).use { session ->
                    setup(session, peer)
                    assertNotNull(engine.onBufferedAudio(session, mapOf("type" to 103L, "audioType" to "media",
                        "audioFormatIndex" to 65L, "audioFormat" to 0L, "packetFormatAPAP" to true, "spf" to 1024L,
                        "shk" to ByteArray(32))))
                    assertOnPhoneClock(anchorSeconds(handle(session, "SETRATE",
                        mapOf("rate" to 1L, "mediaTimeValue" to 0L, "mediaTimeScale" to 48000L))))
                }
            }
        }
    }

    @Test fun `an iPhone that never syncs holds the first play command for 3 s only`() {
        FakeTimingPeer(answering = false).use { peer ->
            val engine = CarPlayMediaEngine(object : MediaSink {})
            session(engine, spatial = false).use { session ->
                setup(session, peer)
                assertNotNull(engine.onBufferedAudio(session, mapOf("type" to 103, "shk" to ByteArray(32),
                    "audioFormat" to 0x800000, "ct" to 4, "spf" to 1024, "streamConnectionID" to 1)))
                val first = timedMs { handle(session, "SETRATE", mapOf("rate" to 1L, "rtpTime" to 0L)) }
                assertTrue("first play waited $first ms", first in 2500..4500)
                val second = timedMs { handle(session, "SETRATE", mapOf("rate" to 0L)) }
                assertTrue("second play waited $second ms", second < 500)
            }
        }
    }

    private fun timedMs(block: () -> Unit): Long = System.nanoTime().let { start -> block(); (System.nanoTime() - start) / 1_000_000L }

    private fun assertOnPhoneClock(seconds: Long) =
        assertTrue("anchor at $seconds s", Math.abs(seconds - FakeTimingPeer.PHONE_UNIX_SECONDS) < 60)

    private fun anchorSeconds(response: RtspMessage.Response): Long =
        ((BplistCodec.decode(response.body) as Map<*, *>)["networkTimeSecs"] as Number).toLong()

    private fun setup(session: AirPlaySession, peer: FakeTimingPeer) =
        handle(session, "SETUP", mapOf("timingPort" to peer.port.toLong(), "features" to listOf(MAIN_BUFFERED_FEATURE)))

    private fun handle(session: AirPlaySession, method: String, body: Map<String, Any?>) =
        AirPlaySession::class.java.getDeclaredMethod("handle", RtspMessage.Request::class.java).apply { isAccessible = true }
            .invoke(session, RtspMessage.Request(method, "rtsp://test", "RTSP/1.0", emptyMap(), BplistCodec.encode(body))) as RtspMessage.Response

    private fun session(media: AirPlayMediaHandler, spatial: Boolean) = AirPlaySession(
        socket = object : Socket() {
            override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()
            override fun getRemoteSocketAddress(): SocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 1234)
        }, config = AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:01",
            sourceVersion = "1.0", main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480),
            mainBufferedAudio = true, spatialAudio = spatial),
        identity = AirPlayIdentity.generate(), pairings = PairingStore(), mfi = null,
        listener = object : AirPlaySessionListener {}, media = media)
}
