package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.BplistCodec
import com.shilapi.xcertplay.airplay.MediaSink
import io.github.numberonebot.chairplay.carplay.DataLink
import io.github.numberonebot.chairplay.carplay.PhoneDataTap
import io.github.numberonebot.chairplay.drive.PacedLocation
import io.github.numberonebot.chairplay.media.AudioDuckSink
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** WirelessBootstrap: what a session passes on: duck commands, iAP2 data frames, and the spoofed GPS. */
class WirelessBootstrapDataTest : WirelessBootstrapFixture() {
    // The iPhone brackets each navigation prompt with duckAudio and unduckAudio; every command reaches the log.
    @Test(timeout = 30_000)
    fun `duck commands reach a sink that ducks, and the session's end unducks`() {
        val sink = DuckingSink()
        Harness(sink = sink).use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { client ->
                assertOk(client.request("RECORD", "*"))
                val duck = BplistCodec.encode(mapOf("type" to "duckAudio", "params" to mapOf("durationMs" to 500L, "volume" to 0.2)))
                assertOk(client.request("POST", "/command", duck))

                assertEquals(true to mapOf("durationMs" to 500L, "volume" to 0.2), sink.ducks.poll(10, TimeUnit.SECONDS))
                assertTrue(harness.logs.toString(), "airplay command duckAudio: durationMs=500, volume=0.2" in harness.logs)
            }
            // The connection dropped mid-prompt: no unduckAudio will come.
            assertEquals(false, sink.ducks.poll(10, TimeUnit.SECONDS)?.first)
        }
    }

    private class DuckingSink : MediaSink, AudioDuckSink {
        val ducks = LinkedBlockingQueue<Pair<Boolean, Map<String, Any?>>>()
        override fun onAudioDuck(ducked: Boolean, params: Map<String, Any?>) {
            ducks += ducked to params
        }
    }

    @Test(timeout = 30_000)
    fun `data frames from both iAP2 links reach the data tap`() {
        val tap = RecordingTap()
        Harness(tap).use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { client ->
                assertOk(client.request("RECORD", "*"))
                val parts = harness.parts.last
                awaitTrue { parts.tunnelHandler.get() != null }
                assertTrue(parts.tunnelHandler.get()!!(SilentStream()))
                awaitTrue { tap.frames.size >= 2 }

                assertEquals(setOf(DataLink.BLUETOOTH to POWER_UPDATE, DataLink.WIFI to POWER_UPDATE), tap.frames.toSet())
            }
        }
    }

    // The spoofed GPS: the tunnel identifies with the location component and runs with the car's position; the
    // Bluetooth link offers no location, so it gets none.
    @Test(timeout = 30_000)
    fun `the tunnel sends the spoofed GPS when it is on`() {
        // A GPS that paces itself gets the tunnel to send on.
        var attached = false
        val gps = object : Iap2LocationProvider, PacedLocation {
            override fun start() = true
            override fun stop() {}
            override fun latestNmea(): String? = null
            override fun attach(send: (List<String>) -> Unit) {
                attached = true
            }
        }
        Harness(settings = "gps.enabled=true", location = gps).use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { client ->
                assertOk(client.request("RECORD", "*"))
                val parts = harness.parts.last
                awaitTrue { parts.tunnelHandler.get() != null }
                assertTrue(parts.tunnelHandler.get()!!(SilentStream()))
                val bluetooth = parts.runs.poll(10, TimeUnit.SECONDS)!!
                val tunnel = parts.runs.poll(10, TimeUnit.SECONDS)!!

                assertNull(bluetooth.second)
                assertFalse(bluetooth.first.locationInformationEnabled)
                assertSame(gps, tunnel.second)
                assertTrue(tunnel.first.locationInformationEnabled)
                assertTrue(tunnel.first.vehicleSpeedEnabled)
                assertTrue(attached)
            }
        }
    }

    @Test(timeout = 30_000)
    fun `the tunnel offers no GPS while it is off`() {
        Harness().use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { client ->
                assertOk(client.request("RECORD", "*"))
                val parts = harness.parts.last
                awaitTrue { parts.tunnelHandler.get() != null }
                assertTrue(parts.tunnelHandler.get()!!(SilentStream()))
                parts.runs.poll(10, TimeUnit.SECONDS)!!
                val tunnel = parts.runs.poll(10, TimeUnit.SECONDS)!!

                assertNull(tunnel.second)
                assertFalse(tunnel.first.locationInformationEnabled)
            }
        }
    }

    @Test(timeout = 30_000)
    fun `the end of an active session clears the data`() {
        val tap = RecordingTap()
        Harness(tap).use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { assertOk(it.request("RECORD", "*")) }
            harness.nextPort()

            assertEquals(1, tap.ends.get())
        }
    }

    private class RecordingTap : PhoneDataTap {
        val frames = LinkedBlockingQueue<Pair<DataLink, Int>>()
        val ends = AtomicInteger()
        override fun onFrame(link: DataLink, frame: Iap2Frame) {
            frames += link to frame.messageId
        }
        override fun onSessionEnded() {
            ends.incrementAndGet()
        }
    }
}
