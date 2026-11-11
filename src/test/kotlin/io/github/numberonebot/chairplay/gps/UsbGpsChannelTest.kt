package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** UsbGpsChannel: the USB channel over the helper's port (A4). */
class UsbGpsChannelTest {
    private val log = CopyOnWriteArrayList<String>()
    private val phone = UsbDevice("UDID-1", "iPhone", "27.2")
    private var devices: UsbResult<List<UsbDevice>> = UsbResult.Ok(listOf(phone))
    private var clearResult: UsbResult<Unit> = UsbResult.Ok(Unit)
    private var sessions = 0

    private inner class Session : UsbSession {
        override val id = "s${++sessions}".also { latest = this }
        override val acknowledged get() = log.count { it.startsWith("$id.offer") }.toLong()
        override var onLost: (UsbErrorCode) -> Unit = {}
        override fun offer(point: GeoPoint) { log += "$id.offer" }
        override fun clear() = clearResult.also { log += "$id.clear" }
        override fun close() { log += "$id.close" }
    }

    private val helper = object : UsbHelper {
        override fun components() = AppleComponents.READY
        override fun devices() = devices
        override fun probe(udid: String): UsbResult<UsbProbe> = UsbResult.Failed(UsbErrorCode.PROTOCOL)
        override fun revealDeveloperMode(udid: String): UsbResult<Unit> = UsbResult.Failed(UsbErrorCode.PROTOCOL)
        override fun toolsStatus(udid: String): UsbResult<ToolsStatus> = UsbResult.Failed(UsbErrorCode.PROTOCOL)
        override fun prepareTools(udid: String, progress: (DownloadProgress) -> Unit, cancel: Cancellation): UsbResult<Unit> =
            UsbResult.Failed(UsbErrorCode.PROTOCOL)
        override fun openSession(udid: String): UsbResult<UsbSession> = UsbResult.Ok(Session()).also { log += "open $udid" }
        override fun close() {}
    }

    private val channel = UsbGpsChannel({ helper }, { GeoPoint(40.7, -74.0) }, { 20 }, pollMillis = 20)

    @Test
    fun `it sees the attached iPhone, the first of several (D11), and none without a helper`() {
        var changes = 0
        channel.onChanged = { changes++ }
        channel.start()
        Thread.sleep(150)
        assertEquals(ChannelState(null, device = phone.view()), channel.state())
        devices = UsbResult.Ok(listOf(phone, phone.copy(udid = "UDID-2")))
        Thread.sleep(150)
        assertEquals(ChannelState(null, device = phone.view()), channel.state())
        devices = UsbResult.Ok(emptyList())
        Thread.sleep(150)
        assertEquals(GpsReason.USB_NOT_CONNECTED, channel.state().reason)
        assertEquals(2, changes)
        // Apple Devices closed: its usbmux is gone with the cable still in, which is no missing cable.
        devices = UsbResult.Failed(UsbErrorCode.USBMUX_UNAVAILABLE)
        Thread.sleep(150)
        assertEquals(GpsReason.USB_NOT_READY, channel.state().reason)
        assertEquals(GpsReason.USB_HELPER_FAILED, UsbGpsChannel({ null }, { null }, { 10 }).state().reason)
        channel.close()
    }

    // P-03: the car's place goes at the rate, into one session; quiesce stops the offers.
    @Test
    fun `a started channel offers the car's place at its rate until quiesced`() {
        assertEquals(null, channel.start(GeoPoint(1.0, 2.0), phone))
        Thread.sleep(300)
        channel.quiesce()
        val offers = log.count { it == "s1.offer" }
        Thread.sleep(150)

        assertTrue("offers $offers", offers in 4..8)
        assertEquals(offers, log.count { it == "s1.offer" })
        assertEquals(1, sessions)
        assertTrue(channel.running)
    }

    // L-01: clear and close, the close whatever the clear did; the session ends.
    @Test
    fun `release clears and closes the session`() {
        channel.start(GeoPoint(1.0, 2.0), phone)
        clearResult = UsbResult.Failed(UsbErrorCode.LINK_LOST)
        val stages = mutableListOf<OperationStage>()

        val release = channel.release { stages += it }

        assertEquals(Release(StepOutcome.FAILED, StepOutcome.DONE), release)
        assertEquals(listOf(OperationStage.CLEAR, OperationStage.CLOSE), stages)
        assertEquals(listOf("s1.clear", "s1.close"), log.filter { !it.contains("offer") && !it.startsWith("open") })
        assertFalse(channel.running)
    }

    // V49, V50: what an earlier run left is cleared through a session of its own; with no iPhone nothing is tried.
    @Test
    fun `release without a session tries nothing, the iPhone attached or not (D12)`() {
        assertEquals(Release.NOTHING, channel.release {})
        channel.start()
        Thread.sleep(100)

        assertEquals(Release.NOTHING, channel.release {})
        assertEquals(emptyList<String>(), log.toList())
        channel.close()
    }

    @Test
    fun `a session lost on its own stops the channel and says so`() {
        var changes = 0
        channel.onChanged = { changes++ }
        channel.start(GeoPoint(1.0, 2.0), phone)
        val own = latest!!
        val other = (helper.openSession("x") as UsbResult.Ok).value

        other.onLost(UsbErrorCode.LINK_LOST)
        assertTrue("a session not the channel's changes nothing", channel.running)
        own.onLost(UsbErrorCode.LINK_LOST)
        assertFalse(channel.running)
        assertEquals(1, changes)
    }

    private var latest: Session? = null
}
