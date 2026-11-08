package io.github.numberonebot.chairplay.data

import io.github.numberonebot.chairplay.data.PhoneDataEvent.ArtworkChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.CallChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.Cleared
import io.github.numberonebot.chairplay.data.PhoneDataEvent.CommunicationsChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.LanesChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.NowPlayingChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.PowerChanged
import io.github.numberonebot.chairplay.data.PhoneDataEvent.RouteChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PhoneDataBus: its setters, the events they raise, calls, the snapshot's refresh and the end of a session. */
class PhoneDataBusTest {
    private val bus = PhoneDataBus()
    private val events = ArrayList<PhoneDataEvent>()
    private val heldWhenHeard = ArrayList<PhoneDataSnapshot>()

    init {
        bus.addListener { events += it }
    }

    private val route = RouteGuidance(state = RouteGuidanceState.ROUTE_SET, currentRoad = "Main Street")
    private val lanes = LaneGuidance(4, listOf(Lane(0, LaneStatus.GOOD.code, listOf(0))))
    private val song = NowPlaying(title = "Song", playing = true)
    private val cover = Artwork(7, byteArrayOf(1, 2, 3))
    private val network = Communications(signalStrength = 3)
    private val battery = Power(chargeLevel = 50)
    private val ringing = Call("call-1", "+15551234567", status = CallStatus.RINGING, direction = CallDirection.INCOMING)

    @Test
    fun `each setter raises its event, and the snapshot already holds the value`() {
        bus.addListener { heldWhenHeard += bus.snapshot() }

        bus.route(route)
        bus.lanes(lanes)
        bus.nowPlaying(song)
        bus.artwork(cover)
        bus.call(ringing)
        bus.communications(network)
        bus.power(battery)

        assertEquals(
            listOf(
                RouteChanged(route), LanesChanged(lanes), NowPlayingChanged(song), ArtworkChanged(cover),
                CallChanged(ringing, listOf(ringing)), CommunicationsChanged(network), PowerChanged(battery),
            ),
            events,
        )
        val all = PhoneDataSnapshot(route, lanes, song, cover, listOf(ringing), network, battery)
        assertEquals(all, bus.snapshot())
        assertEquals(route, heldWhenHeard.first().route)
        assertEquals(all, heldWhenHeard.last())
    }

    @Test
    fun `a value equal to the one held raises nothing`() {
        repeat(2) {
            bus.route(route.copy())
            bus.lanes(null)
            bus.nowPlaying(song.copy())
            bus.artwork(Artwork(7, byteArrayOf(1, 2, 3)))
            bus.communications(network.copy())
            bus.power(battery.copy())
        }

        assertEquals(5, events.size)
    }

    @Test
    fun `null drops a value, and listeners hear it`() {
        bus.route(route)
        bus.nowPlaying(song)
        bus.communications(network)
        bus.power(battery)
        bus.lanes(lanes)
        events.clear()

        bus.route(null)
        bus.nowPlaying(null)
        bus.communications(null)
        bus.power(null)
        bus.lanes(null)
        bus.power(null)

        assertEquals(
            listOf(RouteChanged(null), NowPlayingChanged(null), CommunicationsChanged(null), PowerChanged(null), LanesChanged(null)),
            events,
        )
        assertEquals(PhoneDataSnapshot(), bus.snapshot())
    }

    @Test
    fun `calls are kept in their order until they end`() {
        val waiting = Call("+15557654321", "+15557654321", status = CallStatus.RINGING)
        val active = ringing.copy(status = CallStatus.ACTIVE)
        bus.call(ringing)
        bus.call(waiting)
        bus.call(active)
        bus.call(active)
        assertEquals(listOf(active, waiting), bus.snapshot().calls)

        val ended = active.copy(status = CallStatus.DISCONNECTED)
        bus.call(ended)
        assertEquals(CallChanged(ended, listOf(waiting)), events.last())

        // One the bus does not hold, ending, raises nothing.
        bus.call(Call("call-9", status = CallStatus.DISCONNECTING))
        assertEquals(4, events.size)
        assertEquals(listOf(waiting), bus.snapshot().calls)
    }

    @Test
    fun `a snapshot refreshes first and listeners hear what the refresh changed`() {
        var refreshes = 0
        val refresh = bus.onSnapshot {
            refreshes++
            bus.route(route.copy(remainingSeconds = refreshes.toLong()))
        }

        assertEquals(1L, bus.snapshot().route!!.remainingSeconds)
        assertEquals(RouteChanged(route.copy(remainingSeconds = 1)), events.single())

        refresh.close()
        bus.snapshot()
        assertEquals(1, refreshes)
    }

    @Test
    fun `the end of a session empties the snapshot, told once`() {
        bus.clear()
        assertTrue(events.isEmpty())

        bus.power(battery)
        bus.call(ringing)
        bus.clear()
        bus.clear()

        assertEquals(Cleared, events.last())
        assertEquals(1, events.count { it == Cleared })
        assertEquals(PhoneDataSnapshot(), bus.snapshot())
    }

    @Test
    fun `a listener that leaves hears nothing more and one that fails does not stop the others`() {
        val heard = ArrayList<PhoneDataEvent>()
        bus.addListener { error("listener failed") }
        val registration = bus.addListener { heard += it }
        bus.power(battery)
        registration.close()
        bus.power(null)

        assertEquals(1, heard.size)
        assertEquals(2, events.size)
    }
}
