package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GpsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coordinator's part in track playback: when a track may drive the car, and the notice track playback raises on the
 * GPS page, which the page hears of at once.
 */
class GpsTrackTest {
    private val harness = GpsHarness()
    private var told = 0
    private val usbOn = GpsSettings(enabled = true, method = GpsMethod.USB, usbDevice = "UDID-1")

    init {
        harness.gps.changed = { told++ }
    }

    // Spec §1: the GPS page shows track playback for USB only, and losing USB pauses it.
    @Test
    fun `a track drives only while USB is in force, on and sending`() {
        val usb = GpsHarness(usbOn)
        usb.usb.state = ChannelState(null, device = UsbDeviceView("r", "iPhone"))
        usb.reconcile()
        assertTrue(usb.gps.trackLive())

        usb.usb.lose()
        assertFalse(usb.gps.trackLive())
        assertFalse("CarPlay in force drives the car, but no track", GpsHarness(GpsSettings(enabled = true)).gps.trackLive())
        assertFalse(GpsHarness(usbOn.copy(enabled = false)).gps.trackLive())
    }

    @Test
    fun `a notice shown is in the output with its args, and dropping it takes it away, each telling the page`() {
        harness.gps.showNotice(NoticeCode.TRACK_UNREADABLE, NoticeTone.BAD, mapOf("reason" to "no-track"))

        val shown = harness.output().notices.single()
        assertEquals(NoticeCode.TRACK_UNREADABLE, shown.code)
        assertEquals(NoticeTone.BAD, shown.tone)
        assertEquals(mapOf("reason" to "no-track"), shown.args)
        assertEquals(1, told)

        harness.gps.dropNotice(NoticeCode.TRACK_UNREADABLE)

        assertEquals(emptyList<GpsNotice>(), harness.output().notices)
        assertEquals(2, told)
    }
}
