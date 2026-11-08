package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.transport.Iap2ArtworkTransfer
import io.github.numberonebot.chairplay.data.Artwork
import io.github.numberonebot.chairplay.data.BatteryChargingState
import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallStatus
import io.github.numberonebot.chairplay.data.Communications
import io.github.numberonebot.chairplay.data.DisconnectReason
import io.github.numberonebot.chairplay.data.NowPlaying
import io.github.numberonebot.chairplay.data.PhoneDataEvent
import io.github.numberonebot.chairplay.data.PhoneDataEvent.ArtworkChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.CallChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.Cleared
import io.github.numberonebot.chairplay.data.PhoneDataEvent.CommunicationsChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.NowPlayingChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.PowerChanged
import io.github.numberonebot.chairplay.data.PhoneDataSnapshot
import io.github.numberonebot.chairplay.data.PlaybackStatus
import io.github.numberonebot.chairplay.data.Power
import io.github.numberonebot.chairplay.data.ShuffleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Iap2DataDecoder: now playing, artwork, calls, communications and power, and how the bus raises events and clears. */
class Iap2DataDecoderTest : Iap2DataDecoderFixture() {
    @Test
    fun `now playing keeps what each update leaves out`() {
        frame(0x5001) {
            group(0) {
                string(1, "Song")
                u32(4, 215_000)
                string(6, "Album")
                string(12, "Artist")
                string(14, "Band")
                u16(7, 4)
                u8(26, 7)
            }
            group(1) {
                u8(0, 1)
                u32(1, 1_000)
                u8(5, 1)
                string(7, "Music")
            }
        }
        frame(0x5001) { group(1) { u32(1, 1_500) } }

        val playing = last<NowPlayingChanged>().nowPlaying
        assertEquals(
            NowPlaying(
                title = "Song", artist = "Artist", album = "Album", albumArtist = "Band", durationMillis = 215_000,
                elapsedMillis = 1_500, status = PlaybackStatus.PLAYING, playing = true, trackNumber = 4,
                shuffle = ShuffleMode.SONGS, appName = "Music", artworkTransferId = 7,
            ),
            playing,
        )

        frame(0x5001) { group(1) { u8(0, 2) } }
        val paused = last<NowPlayingChanged>().nowPlaying!!
        assertEquals(PlaybackStatus.PAUSED, paused.status)
        assertEquals(false, paused.playing)
        assertEquals("Song", bus.snapshot().nowPlaying!!.title)
    }

    @Test
    fun `artwork arrives as bytes`() {
        decoder.acceptArtwork(Iap2ArtworkTransfer(7, byteArrayOf(1, 2, 3)))

        assertEquals(Artwork(7, byteArrayOf(1, 2, 3)), last<ArtworkChanged>().artwork)
        assertEquals(3, bus.snapshot().artwork!!.size)
    }

    @Test
    fun `a call is kept until it ends`() {
        frame(0x4155) {
            string(0, "+15551234567")
            string(1, "Alice")
            u8(2, 2)
            u8(3, 1)
            string(4, "call-1")
        }
        val ringing = Call("call-1", "+15551234567", "Alice", CallStatus.RINGING, CallDirection.INCOMING)
        assertEquals(CallChanged(ringing, listOf(ringing)), events.last())

        frame(0x4155) {
            u8(2, 4)
            string(4, "call-1")
        }
        val active = ringing.copy(status = CallStatus.ACTIVE)
        assertEquals(listOf(active), bus.snapshot().calls)
        assertEquals("Alice", active.name)

        frame(0x4155) {
            u8(2, 0)
            string(4, "call-1")
            u8(11, 1)
        }
        val ended = active.copy(status = CallStatus.DISCONNECTED, disconnectReason = DisconnectReason.DECLINED)
        assertEquals(CallChanged(ended, emptyList()), events.last())
        assertTrue(bus.snapshot().calls.isEmpty())
    }

    @Test
    fun `communications and power keep their last values`() {
        frame(0x4158) {
            u8(0, 3)
            string(3, "Carrier")
            bool(5, true)
        }
        frame(0x4158) { u8(0, 4) }
        assertEquals(
            Communications(signalStrength = 4, carrier = "Carrier", telephonyEnabled = true),
            last<CommunicationsChanged>().communications,
        )

        frame(0xae01) {
            bool(4, true)
            u8(5, 1)
            u16(6, 37)
        }
        val power = last<PowerChanged>().power!!
        assertEquals(Power(37, BatteryChargingState.CHARGING, true), power)
        // The iPhone sends percent: 38 in the data log while it showed 37 %.
        assertEquals(37, power.batteryPercent)
    }

    // The iPhone 15 on iOS 18.6 leaves the carrier name empty and names the carrier in parameter 4.
    @Test
    fun `the carrier falls back to the carrier id`() {
        frame(0x4158) {
            string(3, "")
            string(4, "UA-KYIVSTAR")
        }
        assertEquals("UA-KYIVSTAR", last<CommunicationsChanged>().communications!!.carrier)
    }

    @Test
    fun `an update that changes nothing raises no event`() {
        repeat(2) { frame(0xae01) { u16(6, 100) } }
        frame(0x4171) { string(0, "list") }

        assertEquals(1, events.size)
    }

    @Test
    fun `a listener that leaves hears nothing more and one that fails does not stop the others`() {
        val heard = ArrayList<PhoneDataEvent>()
        bus.addListener { error("listener failed") }
        val registration = bus.addListener { heard += it }
        frame(0xae01) { u16(6, 100) }
        registration.close()
        frame(0xae01) { u16(6, 200) }

        assertEquals(1, heard.size)
        assertEquals(2, events.size)
    }

    @Test
    fun `the end of a session empties the snapshot`() {
        frame(0xae01) { u16(6, 100) }
        frame(0x4158) { string(3, "Carrier") }

        decoder.clear()

        assertEquals(Cleared, events.last())
        assertEquals(PhoneDataSnapshot(), bus.snapshot())
    }
}
