package io.github.numberonebot.chairplay.input

import org.lwjgl.glfw.GLFW.*

/** Windows key lifetime and held modifiers, translated into ordered complete HID states. */
internal class KeyboardState {
    private val held = linkedSetOf<Int>()
    private var reported = ByteArray(8)

    fun accepts(key: Int): Boolean = modifier(key) != 0 || KeyboardKeys.report(key, 0) != null

    fun event(key: Int, action: Int, mods: Int): List<ByteArray> {
        if (!accepts(key)) return emptyList()
        if (action == GLFW_RELEASE && key !in held && (modifier(key) == 0 || reported.all { it == 0.toByte() })) return emptyList()
        if (action == GLFW_REPEAT) {
            if (key !in held || modifier(key) != 0) return emptyList()
            val usage = KeyboardKeys.report(key, mods)?.get(2) ?: return emptyList()
            val without = reported.copyOf().apply { for (i in 2..7) if (this[i] == usage) this[i] = 0 }
            return listOf(without, reported.copyOf())
        }
        if (action == GLFW_PRESS) held += key else held -= key
        // A modifier whose release Windows kept (switching the layout with Alt+Shift does) is up when this event's own
        // state says so: held on, it would reach the iPhone with every key after.
        held.removeAll { it != key && modifier(it) != 0 && mods and modifier(it) == 0 }
        var logicalMods = mods and (GLFW_MOD_SHIFT or GLFW_MOD_CONTROL or GLFW_MOD_ALT or GLFW_MOD_SUPER)
        if (action == GLFW_RELEASE) logicalMods = logicalMods and modifier(key).inv()
        held.forEach { logicalMods = logicalMods or modifier(it) }
        val keys = held.filter { modifier(it) == 0 }
        val next = KeyboardKeys.report(keys.lastOrNull() ?: GLFW_KEY_SPACE, logicalMods)!!
        for (i in 2..7) next[i] = 0
        if (keys.size > 6) for (i in 2..7) next[i] = 1 // USB six-key rollover state.
        else keys.forEachIndexed { index, heldKey -> next[index + 2] = KeyboardKeys.report(heldKey, logicalMods)!![2] }
        return transition(next)
    }

    fun release(): List<ByteArray> {
        held.clear()
        return transition(ByteArray(8))
    }

    /** Release removed keys under their old modifiers, change modifiers, then press added keys. */
    private fun transition(next: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        fun emit(packet: ByteArray) {
            if (!reported.contentEquals(packet)) { packets += packet.copyOf(); reported = packet.copyOf() }
        }
        val remaining = reported.drop(2).filter { it != 0.toByte() && it in next.drop(2) }
        val released = ByteArray(8).apply {
            this[0] = reported[0]
            remaining.take(6).forEachIndexed { index, usage -> this[index + 2] = usage }
        }
        emit(released)
        emit(released.copyOf().apply { this[0] = next[0] })
        emit(next)
        return packets
    }

    private fun modifier(key: Int): Int = when (key) {
        GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> GLFW_MOD_SHIFT
        GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> GLFW_MOD_CONTROL
        GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> GLFW_MOD_ALT
        GLFW_KEY_LEFT_SUPER, GLFW_KEY_RIGHT_SUPER -> GLFW_MOD_SUPER
        else -> 0
    }
}
