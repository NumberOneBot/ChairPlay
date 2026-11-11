package io.github.numberonebot.chairplay.gps

import org.junit.Assert.assertEquals
import org.junit.Test

/** A notice another part of the app raises on the GPS page through the coordinator: the page hears of it at once. */
class GpsTrackNoticeTest {
    private val harness = GpsHarness()
    private var told = 0

    init {
        harness.gps.changed = { told++ }
    }

    @Test
    fun `a notice shown is in the output with its args, and dropping it takes it away, each telling the page`() {
        harness.gps.showNotice(NoticeCode.TRACK_UNREADABLE, NoticeTone.BAD, mapOf("reason" to "It has no GPS track."))

        val shown = harness.output().notices.single()
        assertEquals(NoticeCode.TRACK_UNREADABLE, shown.code)
        assertEquals(NoticeTone.BAD, shown.tone)
        assertEquals(mapOf("reason" to "It has no GPS track."), shown.args)
        assertEquals(1, told)

        harness.gps.dropNotice(NoticeCode.TRACK_UNREADABLE)

        assertEquals(emptyList<GpsNotice>(), harness.output().notices)
        assertEquals(2, told)
    }
}
