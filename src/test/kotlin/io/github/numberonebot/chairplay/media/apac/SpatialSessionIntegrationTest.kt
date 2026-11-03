package io.github.numberonebot.chairplay.media.apac

import com.shilapi.xcertplay.airplay.*
import io.github.numberonebot.chairplay.media.encodeAac
import io.github.numberonebot.chairplay.media.BufferedAudioMonitor
import io.github.numberonebot.chairplay.media.BufferedAudioStatusSink
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class SpatialSessionIntegrationTest {
    @Test fun `RTSP negotiation and controls drive native AAC APAC AAC playback in one TCP session`() {
        val native = apacTestBackend()
        val fixture = apacTestFixture()
        val devices = CopyOnWriteArrayList<TestSpatialDevice>()
        val bridge = SpatialAudioBridge({ SpatialPlan(native, "test", 2) }, { 1f }, { 1f }, {}, {},
            { TestSpatialDevice().also(devices::add) })
        val monitor = BufferedAudioMonitor()
        val sink = object : MediaSink, SpatialAudioSink, BufferedAudioStatusSink by monitor { override val spatialAudio = bridge }
        val engine = CarPlayMediaEngine(sink)
        val session = session(engine)
        try {
            assertFalse(session.spatialAudioEnabled)
            assertEquals(200, handle(session, "SETMAGICCOOKIE", mapOf("magicCookieID" to 7L, "magicCookie" to aacCookie())).status)
            val initial = info(session)
            assertFalse(initial.containsKey("mainBufferedInfo"))
            handle(session, "SETUP", mapOf("features" to listOf("mainBuffered")))
            assertTrue(session.spatialAudioEnabled)
            val formats = info(session)["audioFormats"] as List<*>
            val offered = formats.map { it as Map<*, *> }.single { it["type"] == 103L }
            assertEquals(listOf(65L, 23L), offered["audioOutputFormatsExtended"])
            val setup = mapOf("type" to 103L, "audioType" to "media", "audioFormatIndex" to 65L,
                "audioFormat" to 0L, "packetFormatAPAP" to true, "spf" to 1024L, "shk" to ByteArray(32))
            val response = engine.onBufferedAudio(session, setup)!!
            assertEquals(21_168_000, response["audioBufferSize"])
            assertNull(engine.onBufferedAudio(session, setup + ("audioFormatIndex" to 66L)))
            Socket("127.0.0.1", response["dataPort"] as Int).use { socket ->
                val aac = encodeAac(48000, 2, 3).first()
                val items = listOf(aacCookie() to aac) + fixture.packets.map { fixture.cookie to it } + listOf(aacCookie() to aac)
                items.forEachIndexed { index, (cookie, audio) -> socket.getOutputStream().write(packet(index, cookie, audio)) }
                val start = handle(session, "SETRATE", mapOf("rate" to 1L, "mediaTimeValue" to 0L, "mediaTimeScale" to 48000L))
                val anchor = BplistCodec.decode(start.body) as Map<*, *>
                assertEquals(0L, anchor["rtpTime"])
                assertEquals(48000L, anchor["mediaTimeScale"])
                await { devices.firstOrNull()?.samples?.size == items.size }
                val expected = ApacNativeDecoder(native, fixture.cookie).use { spatialPcm(it.decode(fixture.packets.first()), 2) }
                assertArrayEquals(expected, devices[0].samples[1])
                assertArrayEquals(devices[0].samples.first(), devices[0].samples.last())
                assertNull(monitor.state()?.get("multichannelOutput"))
                devices[0].position.set(1536)
                await { monitor.state()?.get("multichannelOutput") == "downmix" }
                devices[0].position.set((items.size - 1) * 1024L + 512)
                await { monitor.state()?.get("multichannelOutput") == null }
                assertEquals(200, handle(session, "SETRATEANCHORTIME", mapOf("rate" to 0L)).status ?: 200)
                assertTrue(devices[0].closed)
            }
        } finally { session.close(); bridge.close() }
        assertNull(monitor.state())
    }

    private fun packet(index: Int, cookie: ByteArray, audio: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(15).putLong(index * 1024L).putInt(48000).put(0).put(0).put(index.toByte()).array()
        val plain = byteArrayOf(2, 1, 65, 18, cookie.size.toByte()) + cookie + byteArrayOf(0) + audio
        val nonce = ByteArray(12).also { it[11] = index.toByte() }
        val sealed = AirPlayCrypto.chachaSeal(ByteArray(32), nonce, plain, header.copyOfRange(0, 12))
        val body = header + sealed + nonce.copyOfRange(4, 12)
        return ByteBuffer.allocate(4).putInt(body.size + 4).array() + body
    }
    private fun info(session: AirPlaySession) = BplistCodec.decode(handle(session, "GET", path = "/info").body) as Map<*, *>
    private fun handle(session: AirPlaySession, method: String, body: Map<String, Any?> = emptyMap(), path: String = "rtsp://test") =
        AirPlaySession::class.java.getDeclaredMethod("handle", RtspMessage.Request::class.java).apply { isAccessible = true }
            .invoke(session, RtspMessage.Request(method, path, "RTSP/1.0", emptyMap(), BplistCodec.encode(body))) as RtspMessage.Response
    private fun session(media: AirPlayMediaHandler) = AirPlaySession(
        socket = object : Socket() {
            override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()
            override fun getRemoteSocketAddress(): SocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 1234)
        }, config = AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:01",
            sourceVersion = "1.0", main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480), mainBufferedAudio = true, spatialAudio = true),
        identity = AirPlayIdentity.generate(), pairings = PairingStore(), mfi = null,
        listener = object : AirPlaySessionListener {}, media = media)
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }
}
