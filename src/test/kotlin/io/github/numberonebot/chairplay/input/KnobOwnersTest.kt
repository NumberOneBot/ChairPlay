package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput
import org.junit.Assert.*
import org.junit.Test

class KnobOwnersTest {
    @Test fun `global release preserves a local hold and wheel steps restore it`() {
        val holds = KnobOwners()
        val local = Control.Knob(KnobInput(select = true), false)
        holds.apply("local", local)
        holds.apply("global", Control.Knob(KnobInput(back = true), false))
        val released = holds.apply("global", Control.Knob(KnobInput(), false)).single()
        assertTrue(released.input.select)
        assertFalse(released.input.back)
        val wheel = holds.apply("global", Control.Knob(KnobInput(wheel = 1), true))
        assertEquals(1, wheel.first().input.wheel)
        assertTrue(wheel.last().input.select)
        assertEquals(0, wheel.last().input.wheel)
    }
}
