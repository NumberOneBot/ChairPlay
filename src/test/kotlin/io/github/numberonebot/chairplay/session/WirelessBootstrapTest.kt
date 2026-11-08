package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.BplistCodec
import io.github.numberonebot.chairplay.carplay.CarPlayMediaSink
import io.github.numberonebot.chairplay.media.AudioRenderer
import io.github.numberonebot.chairplay.media.ScreenDecoders
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.phone.Protocol
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Optional
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** WirelessBootstrap: the Bluetooth bootstrap, the session on the Wi-Fi, and when a session's end restarts it. */
class WirelessBootstrapTest : WirelessBootstrapFixture() {
    // A new iPhone needs time for the driver to accept CarPlay on it; one that paired before only has to join
    // the Wi-Fi, so a phone that lost the Wi-Fi meanwhile gets the network again after seconds, not minutes.
    @Test(timeout = 30_000)
    fun `the Bluetooth bootstrap waits minutes for a new iPhone and seconds for a known one`() {
        Harness().use { harness ->
            assertEquals(5 * 60_000L, harness.parts.take().bootstrapTimeout.poll(10, TimeUnit.SECONDS))
        }
        Harness(knownIphone = true).use { harness ->
            assertEquals(30_000L, harness.parts.take().bootstrapTimeout.poll(10, TimeUnit.SECONDS))
        }
        Harness(knownIphone = true, settings = "iphone.join.seconds=20").use { harness ->
            assertEquals(20_000L, harness.parts.take().bootstrapTimeout.poll(10, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 30_000)
    fun `ending an inactive session does not restart`() {
        Harness().use { harness ->
            val port = harness.nextPort()
            // A /info probe or a pairing retry: connects, asks, leaves without RECORD.
            RtspClient(harness.loopback, port).use { assertOk(it.request("OPTIONS", "*")) }
            Thread.sleep(500)

            assertEquals(1, harness.stacks.get())
            RtspClient(harness.loopback, port).use { assertOk(it.request("OPTIONS", "*")) }
        }
    }

    @Test(timeout = 30_000)
    fun `ending an active session restarts once`() {
        Harness().use { harness ->
            val port = harness.nextPort()
            // Both sessions go active before either ends, so both ends come from the same stack.
            val clients = List(2) { RtspClient(harness.loopback, port).also { assertOk(it.request("RECORD", "*")) } }

            clients[0].close()
            harness.nextPort()
            clients[1].close()
            Thread.sleep(500)

            assertEquals(2, harness.stacks.get())
            assertTrue("the replaced listener's port $port is still bound", harness.previousPortFree.get())
        }
    }

    // A session that goes active is the link, until it ends; a keyframe asked of it takes the screens' own request.
    @Test(timeout = 30_000)
    fun `an active session is the link, its keyframes through the screens`() {
        val sink = CarPlayMediaSink(ScreenDecoders(mapOf(ScreenKind.MAIN to VideoWindow(320, 240))), AudioRenderer())
        val asked = AtomicInteger()
        sink.setVideoRecoveryHandler(110) { asked.incrementAndGet() }
        val links = LinkedBlockingQueue<Optional<PhoneLink>>()
        Harness(sink = sink, onLink = { links.add(Optional.ofNullable(it)) }).use { harness ->
            RtspClient(harness.loopback, harness.nextPort()).use { client ->
                assertOk(client.request("RECORD", "*"))
                val link = links.poll(10, TimeUnit.SECONDS)!!.get()

                link.keyframe(ScreenKind.MAIN)
                awaitTrue { asked.get() == 1 }
                assertEquals(Protocol.CARPLAY, link.protocol)
            }
            assertFalse(links.poll(10, TimeUnit.SECONDS)!!.isPresent)
        }
        sink.close()
    }

    @Test(timeout = 30_000)
    fun `Bluetooth loss after joining keeps the Wi-Fi session`() {
        Harness().use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { client ->
                assertOk(client.request("RECORD", "*"))
                val parts = harness.parts.last
                awaitTrue { parts.tunnelHandler.get() != null }

                parts.link.endFromPeer()
                Thread.sleep(500)

                assertEquals(1, harness.stacks.get())
                assertOk(client.request("OPTIONS", "*"))
            }
        }
    }

    @Test(timeout = 30_000)
    fun `handoff closes the Bluetooth bootstrap once and keeps the Wi-Fi session`() {
        Harness().use { harness ->
            val port = harness.nextPort()
            RtspClient(harness.loopback, port).use { client ->
                assertOk(client.request("RECORD", "*"))
                val parts = harness.parts.last
                awaitTrue { parts.tunnelHandler.get() != null }
                val tunnel = SilentStream()
                assertTrue(parts.tunnelHandler.get()!!(tunnel))
                awaitTrue { parts.readyRuns.get() == 1 }

                val disableBluetooth = BplistCodec.encode(mapOf("type" to "disableBluetooth", "params" to emptyMap<String, Any?>()))
                assertOk(client.request("POST", "/command", disableBluetooth))
                awaitTrue { parts.link.closes.get() > 0 }
                assertOk(client.request("POST", "/command", disableBluetooth))
                Thread.sleep(300)

                assertEquals(1, parts.link.closes.get())
                assertEquals(0, tunnel.closes.get())
                assertEquals(1, harness.stacks.get())
                assertOk(client.request("OPTIONS", "*"))
            }
        }
    }
}
