package io.github.numberonebot.chairplay.platform.windows

/** GetAsyncKeyState supplies virtual keys before the first raw packet, not physical scan codes. */
internal class WindowsHeldAtStart {
    private val held = HashMap<Int, Int>()
    val keys: Set<Int> get() = held.keys.toSet()
    fun remember(key: Int, virtual: Int) { held[key] = virtual }
    operator fun contains(key: Int): Boolean = key in held
    fun contains(key: Int, virtual: Int): Boolean = key in held || virtual in held.values
    fun release(key: Int, virtual: Int) { held.entries.removeIf { it.key == key || it.value == virtual } }
    fun released(isDown: (Int) -> Boolean): Set<Int> =
        held.filterValues { !isDown(it) }.keys.toSet().also { keys -> keys.forEach(held::remove) }
    fun clear() = held.clear()
}
