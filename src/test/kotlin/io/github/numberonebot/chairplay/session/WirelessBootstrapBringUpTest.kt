package io.github.numberonebot.chairplay.session

import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.MediaSink
import io.github.numberonebot.chairplay.carplay.PhoneDataTap
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.SettingsFile
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.config.writeTestSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

            assertEquals(BringUpStatus.Starting, harness.statuses.poll(10, TimeUnit.SECONDS))
            assertEquals(BringUpStatus.CallingIphone, harness.statuses.poll(10, TimeUnit.SECONDS))
            assertEquals(BringUpStatus.Joining(knownIphone = false), harness.statuses.poll(10, TimeUnit.SECONDS))
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

    // Missing MFi files stopped the start in the caller's thread, so the reason reached only the console.
    @Test(timeout = 30_000)
    fun `parts that cannot be made fail the bring-up with the reason`() {
        val dir = Files.createTempDirectory("chairplay-bootstrap")
        writeTestSettings(dir)
        val statuses = LinkedBlockingQueue<BringUpStatus>()
        val listener = object : AirPlaySessionListener {}
        WirelessBootstrap(DesktopConfig(dir), object : MediaSink {}, listener, PhoneDataTap.NONE) {
            throw IllegalArgumentException("Offline MFi directory is missing or invalid")
        }.use { bootstrap ->
            bootstrap.onStatus = statuses::add

            bootstrap.start()

            assertEquals(BringUpStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
            val failed = statuses.poll(10, TimeUnit.SECONDS)
            assertEquals(BringUpStatus.Failed("Offline MFi directory is missing or invalid", 5_000L), failed)
        }
    }

    // Disconnect: a failed bring-up retries after 5 s, but not once stopped; Connect starts it again.
    @Test(timeout = 30_000)
    fun `a stopped bring-up stays stopped until it is started`() {
        val dir = Files.createTempDirectory("chairplay-bootstrap")
        writeTestSettings(dir)
        val statuses = LinkedBlockingQueue<BringUpStatus>()
        WirelessBootstrap(DesktopConfig(dir), object : MediaSink {}, object : AirPlaySessionListener {}, PhoneDataTap.NONE) {
            throw IllegalArgumentException("no MFi files")
        }.use { bootstrap ->
            bootstrap.onStatus = statuses::add
            bootstrap.start()
            assertEquals(BringUpStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
            assertTrue(statuses.poll(10, TimeUnit.SECONDS) is BringUpStatus.Failed)

            bootstrap.stop()

            assertNull("no retry after the stop", statuses.poll(7, TimeUnit.SECONDS))
            bootstrap.start()
            assertEquals(BringUpStatus.Starting, statuses.poll(10, TimeUnit.SECONDS))
        }
    }
}
