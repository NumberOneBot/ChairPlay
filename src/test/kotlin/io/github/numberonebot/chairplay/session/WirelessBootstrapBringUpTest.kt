package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.MediaSink
import io.github.numberonebot.chairplay.carplay.PhoneDataTap
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.config.writeTestSettings
import io.github.numberonebot.chairplay.phone.LinkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** WirelessBootstrap: the bring-up: the progress it reports, the screens it advertises, how it fails and stops. */
class WirelessBootstrapBringUpTest : WirelessBootstrapFixture() {
    // The start screen tells the driver what the app is waiting for.
    @Test(timeout = 30_000)
    fun `the bring-up reports its progress`() {
        Harness().use { harness ->
            harness.nextPort()

            assertEquals(LinkStatus.Starting, harness.statuses.poll(10, TimeUnit.SECONDS))
            assertEquals(LinkStatus.CallingPhone, harness.statuses.poll(10, TimeUnit.SECONDS))
            assertEquals(LinkStatus.Joining(knownPhone = false), harness.statuses.poll(10, TimeUnit.SECONDS))
        }
    }

    // Screens saved since the start go to the iPhone at the next connection, and the windows hear of them.
    @Test(timeout = 30_000)
    fun `each bring-up advertises the screens the settings have now`() {
        Harness().use { harness ->
            assertEquals(1280, harness.advertised.poll(10, TimeUnit.SECONDS)!!.main.widthPixels)
            assertEquals(WindowSize(1280, 720), harness.displays.poll(10, TimeUnit.SECONDS))
            assertNull(harness.altscreens.poll(10, TimeUnit.SECONDS)!!.size)

            SettingsFile(harness.dir.resolve("settings.properties")).write(
                mapOf("display.width" to "800", "display.height" to "480", "altscreen.enabled" to "true"),
            )
            harness.restart()

            val next = harness.advertised.poll(10, TimeUnit.SECONDS)!!
            assertEquals(800 to 480, next.main.widthPixels to next.main.heightPixels)
            assertEquals(1440, next.cluster?.widthPixels)
            assertEquals(WindowSize(800, 480), harness.displays.poll(10, TimeUnit.SECONDS))
            assertEquals(WindowSize(1440, 540), harness.altscreens.poll(10, TimeUnit.SECONDS)!!.size)
        }
    }

    // The page marks a change against what the connection was made with, so the network and the screens come from one
    // reading of the file: a network saved as the network starts waits for the next connection, for both alike.
    @Test(timeout = 30_000)
    fun `a bring-up takes the network and the screens from one reading of the settings`() {
        val save = { dir: java.nio.file.Path -> SettingsFile(dir.resolve("settings.properties")).write(mapOf("wifi.ssid" to "Garage")) }
        Harness(onNetworkStart = save).use { harness ->
            assertEquals("Home", harness.ssids.poll(10, TimeUnit.SECONDS))
            assertEquals("Home", harness.parts.first.networks.poll(10, TimeUnit.SECONDS))
        }
    }

    // Missing MFi files stopped the start in the caller's thread, so the reason reached only the console.
    @Test(timeout = 30_000)
    fun `parts that cannot be made fail the bring-up with the reason`() {
        val dir = Files.createTempDirectory("chairplay-bootstrap")
        writeTestSettings(dir)
        val statuses = LinkedBlockingQueue<LinkStatus>()
        val listener = object : AirPlaySessionListener {}
        WirelessBootstrap(DesktopConfig(dir), object : MediaSink {}, listener, PhoneDataTap.NONE) {
            throw IllegalArgumentException("Offline MFi directory is missing or invalid")
        }.use { bootstrap ->
            bootstrap.onStatus = statuses::add

            bootstrap.start()

            assertEquals(LinkStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
            val failed = statuses.poll(10, TimeUnit.SECONDS)
            assertEquals(LinkStatus.Failed("Offline MFi directory is missing or invalid", 5_000L), failed)
        }
    }

    // The iPhone away (its Wi-Fi off, restarted): each failed bring-up starts the next one after its wait, on and on.
    // The app waits 5 s (the test above); here the wait is short, so the test does not sit through it.
    @Test(timeout = 30_000)
    fun `bring-up retries after a failure`() {
        val dir = Files.createTempDirectory("chairplay-bootstrap")
        writeTestSettings(dir)
        val statuses = LinkedBlockingQueue<LinkStatus>()
        WirelessBootstrap(DesktopConfig(dir), object : MediaSink {}, object : AirPlaySessionListener {}, PhoneDataTap.NONE, RETRY) {
            throw IOException("RFCOMM connect failed")
        }.use { bootstrap ->
            bootstrap.onStatus = statuses::add
            bootstrap.start()
            assertEquals(LinkStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
            assertEquals(LinkStatus.Failed("RFCOMM connect failed", RETRY), statuses.poll(10, TimeUnit.SECONDS))
            val failedAt = System.nanoTime()

            assertEquals(LinkStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
            assertTrue("retried before its wait", System.nanoTime() - failedAt >= TimeUnit.MILLISECONDS.toNanos(RETRY * 9 / 10))
            assertEquals(LinkStatus.Failed("RFCOMM connect failed", RETRY), statuses.poll(10, TimeUnit.SECONDS))
        }
    }

    // Disconnect: a failed bring-up retries after its wait, but not once stopped; Connect starts it again.
    @Test(timeout = 30_000)
    fun `a stopped bring-up stays stopped until it is started`() {
        val dir = Files.createTempDirectory("chairplay-bootstrap")
        writeTestSettings(dir)
        val statuses = LinkedBlockingQueue<LinkStatus>()
        WirelessBootstrap(DesktopConfig(dir), object : MediaSink {}, object : AirPlaySessionListener {}, PhoneDataTap.NONE, RETRY) {
            throw IllegalArgumentException("no MFi files")
        }.use { bootstrap ->
            bootstrap.onStatus = statuses::add
            bootstrap.start()
            assertEquals(LinkStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
            assertTrue(statuses.poll(10, TimeUnit.SECONDS) is LinkStatus.Failed)

            bootstrap.stop()

            assertNull("no retry after the stop", statuses.poll(RETRY * 3, TimeUnit.MILLISECONDS))
            bootstrap.start()
            assertEquals(LinkStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
        }
    }

    private companion object {
        /** A failed bring-up's wait in the tests that sit through it. */
        const val RETRY = 300L
    }
}
