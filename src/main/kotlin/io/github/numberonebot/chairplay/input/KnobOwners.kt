package io.github.numberonebot.chairplay.input

import com.shilapi.xcertplay.airplay.AirPlayKnobState

/** A global key's release must not release a button held by another input source. Used on the HID sender. */
class KnobOwners {
    private val held = LinkedHashMap<String, AirPlayKnobState>()
    fun clear() = held.clear()
    fun apply(owner: String, control: Control.Knob): List<Control.Knob> {
        if (!control.momentary) {
            if (control.state == AirPlayKnobState()) held.remove(owner) else held[owner] = control.state
            return listOf(Control.Knob(merge(held.values), false))
        }
        val rest = merge(held.values)
        return listOf(Control.Knob(merge(held.values + control.state), false), Control.Knob(rest, false))
    }
    private fun merge(states: Collection<AirPlayKnobState>) = AirPlayKnobState(
        select = states.any { it.select }, home = states.any { it.home }, back = states.any { it.back },
        x = states.sumOf { it.x }.coerceIn(-127, 127), y = states.sumOf { it.y }.coerceIn(-127, 127),
        wheel = states.sumOf { it.wheel },
    )
}
