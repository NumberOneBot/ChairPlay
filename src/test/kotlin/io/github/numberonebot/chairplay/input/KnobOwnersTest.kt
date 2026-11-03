package io.github.numberonebot.chairplay.input

import com.shilapi.xcertplay.airplay.AirPlayKnobState
import org.junit.Assert.*
import org.junit.Test

class KnobOwnersTest {
    @Test fun `global release preserves a local hold and wheel steps restore it`() {
        val holds = KnobOwners()
        val local = Control.Knob(AirPlayKnobState(select = true), false)
        holds.apply("local", local)
        holds.apply("global", Control.Knob(AirPlayKnobState(back = true), false))
        val released = holds.apply("global", Control.Knob(AirPlayKnobState(), false)).single()
        assertTrue(released.state.select)
        assertFalse(released.state.back)
        val wheel = holds.apply("global", Control.Knob(AirPlayKnobState(wheel = 1), true))
        assertEquals(1, wheel.first().state.wheel)
        assertTrue(wheel.last().state.select)
        assertEquals(0, wheel.last().state.wheel)
    }
}
