package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.KnobInput

/** A global key's release must not release a button held by another input source. Used on the HID sender. */
class KnobOwners {
    private val held = LinkedHashMap<String, KnobInput>()
    fun clear() = held.clear()
    fun apply(owner: String, control: Control.Knob): List<Control.Knob> {
        if (!control.momentary) {
            if (control.input == KnobInput()) held.remove(owner) else held[owner] = control.input
            return listOf(Control.Knob(merge(held.values), false))
        }
        val rest = merge(held.values)
        return listOf(Control.Knob(merge(held.values + control.input), false), Control.Knob(rest, false))
    }
    private fun merge(states: Collection<KnobInput>) = KnobInput(
        select = states.any { it.select }, home = states.any { it.home }, back = states.any { it.back },
        x = states.sumOf { it.x }.coerceIn(-127, 127), y = states.sumOf { it.y }.coerceIn(-127, 127),
        wheel = states.sumOf { it.wheel },
    )
}
