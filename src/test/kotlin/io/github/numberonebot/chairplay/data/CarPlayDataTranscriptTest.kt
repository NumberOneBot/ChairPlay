package io.github.numberonebot.chairplay.data

import com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2ArtworkTransfer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization: one scripted session of the iPhone's data messages, the frames of the bus tests and more, and what
 * the bus makes of them: the events each step raises and the snapshot after it. The expected transcript
 * (iap2/data-transcript.txt) was recorded from CarPlayDataBus, iAP2 decoding and the bus in one class.
 */
class CarPlayDataTranscriptTest {
    private var now = 0L
    private val bus = CarPlayDataBus(nanoTime = { now })
    private val lines = ArrayList<String>()

    init {
        bus.addListener { lines += "  event $it" }
    }

    private fun step(name: String, action: () -> Unit) {
        lines += "> $name"
        action()
        val s = bus.snapshot()
        lines += "  snapshot route=${s.route} lanes=${s.lanes} nowPlaying=${s.nowPlaying} artwork=${s.artwork} " +
            "calls=${s.calls} communications=${s.communications} power=${s.power}"
    }

    private fun frame(id: Int, block: Iap2BodyBuilder.() -> Unit) = bus.accept(Iap2Messages.buildRaw(id, block))

    private fun maneuver(index: Int, type: Int, road: String) = frame(0x5202) {
        u16(1, index)
        u8(3, type)
        string(4, road)
        u8(8, 0)
    }

    private fun lanes(index: Int?, description: String = "Lanes $index") = frame(0x5204) {
        index?.let { u16(1, it) }
        group(2) {
            u16(0, 0)
            u8(1, 2)
            i16(2, -90)
            i16(3, -90)
        }
        group(2) {
            u16(0, 1)
            u8(1, 1)
            i16(2, 0)
        }
        string(3, description)
    }

    private fun currentLanes(index: Int) = frame(0x5201) {
        u8(1, 1)
        u16(16, index)
        bool(18, true)
    }

    private fun session() {
        step("maneuvers before their route") {
            frame(0x5202) {
                u16(1, 3)
                string(2, "Turn right onto Oak Avenue")
                u8(3, 2)
                string(4, "Oak Avenue")
                u32(5, 300)
                u8(8, 1)
                u8(9, 0)
                i16(10, -90)
                i16(10, 90)
                i16(11, 90)
            }
            maneuver(4, 12, "")
        }
        step("route update") {
            frame(0x5201) {
                u8(1, 1)
                u8(2, 2)
                string(3, "Main Street")
                string(4, "Home")
                u64(5, 1_790_000_000L)
                u64(6, 600)
                u32(7, 5_400)
                u32(10, 150)
                string(11, "150")
                u8(12, 2)
                u16List(13, listOf(3, 4))
                string(19, "Maps")
            }
        }
        step("route carries only what changed") {
            frame(0x5201) {
                u32(10, 120)
                bool(15, false)
            }
        }
        step("the same route update again") { frame(0x5201) { u32(10, 120) } }
        step("a maneuver without an index") { frame(0x5202) { u8(3, 1) } }
        step("a maneuver changes") { maneuver(4, 13, "Ring Road") }
        step("an empty current list") { frame(0x5201) { u16List(13, emptyList()) } }
        step("time passes, briefly") { now += 1_000_000_000L }
        step("time passes past the empty list's hold") { now += 2_000_000_000L }
        step("the list comes back") { frame(0x5201) { u16List(13, listOf(4)) } }
        step("thirty seconds without a route update") { now += 30_000_000_000L }
        step("lane events ahead") {
            lanes(4, "Keep left")
            lanes(5)
            lanes(null)
        }
        step("lanes become current") { currentLanes(4) }
        step("the next lane event") { currentLanes(5) }
        step("a passed lane event") { currentLanes(4) }
        step("a full lane cache") {
            lanes(0)
            currentLanes(0)
            for (index in 1..32) lanes(index)
        }
        step("its oldest went") { currentLanes(1) }
        step("one kept") { currentLanes(2) }
        step("arrival") {
            maneuver(0, 12, "")
            frame(0x5201) {
                u8(1, 1)
                u16List(13, listOf(0))
            }
            frame(0x5201) { u8(1, 2) }
        }
        step("a new route, then no route") {
            lanes(3)
            frame(0x5201) {
                u8(1, 1)
                string(3, "Elm Street")
                u16List(13, listOf(0))
            }
            frame(0x5201) { u8(1, 0) }
            currentLanes(3)
        }
        step("now playing") {
            frame(0x5001) {
                group(0) {
                    string(1, "Song")
                    u32(4, 215_000)
                    string(6, "Album")
                    string(12, "Artist")
                    string(14, "Band")
                    string(16, "Genre")
                    string(18, "Composer")
                    u16(7, 4)
                    u16(8, 12)
                    u16(9, 1)
                    u16(10, 2)
                    u8(26, 7)
                }
                group(1) {
                    u8(0, 1)
                    u32(1, 1_000)
                    u32(2, 3)
                    u32(3, 20)
                    u8(5, 1)
                    u8(6, 2)
                    string(7, "Music")
                    string(16, "com.apple.Music")
                }
            }
        }
        step("elapsed time") { frame(0x5001) { group(1) { u32(1, 1_500) } } }
        step("paused") { frame(0x5001) { group(1) { u8(0, 2) } } }
        step("a title cleared") { frame(0x5001) { group(0) { string(1, "") } } }
        step("artwork") { bus.acceptArtwork(Iap2ArtworkTransfer(7, byteArrayOf(1, 2, 3))) }
        step("the same artwork") { bus.acceptArtwork(Iap2ArtworkTransfer(7, byteArrayOf(1, 2, 3))) }
        step("artwork cleared") { bus.acceptArtwork(Iap2ArtworkTransfer(7, ByteArray(0))) }
        step("a call rings") {
            frame(0x4155) {
                string(0, "+15551234567")
                string(1, "Alice")
                u8(2, 2)
                u8(3, 1)
                string(4, "call-1")
                string(7, "mobile")
                u8(8, 1)
            }
        }
        step("a second call, known by its number") {
            frame(0x4155) {
                string(0, "+15557654321")
                u8(2, 2)
                u8(3, 1)
            }
        }
        step("the first answered") {
            frame(0x4155) {
                u8(2, 4)
                string(4, "call-1")
                bool(9, true)
                u8(10, 1)
            }
        }
        step("the same again") {
            frame(0x4155) {
                u8(2, 4)
                string(4, "call-1")
            }
        }
        step("the first declined") {
            frame(0x4155) {
                u8(2, 0)
                string(4, "call-1")
                u8(11, 1)
            }
        }
        step("an unknown call ends") {
            frame(0x4155) {
                u8(2, 6)
                string(4, "call-9")
            }
        }
        step("communications") {
            frame(0x4158) {
                u8(0, 3)
                u8(1, 4)
                bool(2, false)
                string(3, "Carrier")
                bool(5, true)
                bool(8, false)
                u8(9, 1)
                u8(10, 2)
            }
        }
        step("signal changes") { frame(0x4158) { u8(0, 4) } }
        step("the carrier falls back to its id") {
            frame(0x4158) {
                string(3, "")
                string(4, "UA-KYIVSTAR")
            }
        }
        step("power") {
            frame(0xae01) {
                bool(4, true)
                u8(5, 1)
                u16(6, 37)
            }
        }
        step("the same power") { frame(0xae01) { u16(6, 37) } }
        step("a list, not decoded") { frame(0x4171) { string(0, "list") } }
        step("a body that is no parameter list") { bus.accept(Iap2Frame(0x5201, byteArrayOf(0, 9, 0))) }
        step("the session ends") { bus.clear() }
        step("and ends again") { bus.clear() }
        step("a new session's power") { frame(0xae01) { u16(6, 100) } }
        step("a new session's maneuver, then route") {
            maneuver(1, 2, "Oak Avenue")
            frame(0x5201) {
                u8(1, 1)
                u16List(13, listOf(1))
            }
        }
    }

    @Test
    fun `a scripted session gives the recorded transcript`() {
        session()
        val expected = javaClass.getResourceAsStream("/iap2/data-transcript.txt")!!.use { String(it.readBytes(), Charsets.UTF_8) }
        assertEquals(expected.replace("\r\n", "\n").trimEnd(), lines.joinToString("\n"))
    }
}
