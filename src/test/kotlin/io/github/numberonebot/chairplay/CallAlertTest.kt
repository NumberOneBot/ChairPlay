package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.data.Call
import io.github.numberonebot.chairplay.data.CallDirection
import io.github.numberonebot.chairplay.data.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CallAlertTest {
    private val flashes = mutableListOf<Boolean>()
    private var forwards = 0
    private var front = false
    private val alert = CallAlert(flash = { flashes += it }, bringForward = { forwards++ }, front = { front })

    private fun call(id: String, status: CallStatus, direction: CallDirection? = CallDirection.INCOMING) =
        Call(id, status = status, direction = direction)

    // An incoming call flashes the taskbar button while it rings, once, and stops as it is answered.
    @Test fun `an incoming call flashes the button while it rings`() {
        alert.calls(listOf(call("a", CallStatus.RINGING)))
        alert.calls(listOf(call("a", CallStatus.RINGING)))
        alert.calls(listOf(call("a", CallStatus.ACTIVE)))
        assertEquals(listOf(true, false), flashes)
        assertEquals(0, forwards)
    }

    // One the iPhone ends unanswered stops the flash too: it leaves the list.
    @Test fun `a call that ends unanswered stops the flash`() {
        alert.calls(listOf(call("a", CallStatus.RINGING, direction = null)))
        alert.calls(emptyList())
        assertEquals(listOf(true, false), flashes)
    }

    // Calling out is not something to be told of.
    @Test fun `an outgoing call does not flash`() {
        alert.calls(listOf(call("a", CallStatus.RINGING, CallDirection.OUTGOING)))
        assertEquals(emptyList<Boolean>(), flashes)
    }

    // With the switch on, the window comes forward once as the call starts ringing; a waiting call rings too.
    @Test fun `the switch brings the window forward as a call starts ringing`() {
        front = true
        alert.calls(listOf(call("a", CallStatus.ACTIVE), call("b", CallStatus.RINGING)))
        alert.calls(listOf(call("a", CallStatus.ACTIVE), call("b", CallStatus.RINGING)))
        assertEquals(1, forwards)
        assertEquals(listOf(true), flashes)
    }
}
