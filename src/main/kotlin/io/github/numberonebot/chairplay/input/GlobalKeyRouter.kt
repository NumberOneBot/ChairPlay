package io.github.numberonebot.chairplay.input

import org.lwjgl.glfw.GLFW.*

data class GlobalKeyEvent(val key: Int, val action: Int, val mods: Int)

/** Own a physical press from beginning to end. Native callbacks never execute application actions. */
class GlobalKeyRouter {
    private val down = HashMap<Int, Boolean>()
    private val globalPresses = HashSet<Int>()
    private val queue = ArrayDeque<GlobalKeyEvent>()
    private var overflow = false

    @Synchronized fun key(key: Int, action: Int, mods: Int, background: Boolean) {
        if (action == GLFW_PRESS && key !in down) {
            down[key] = background && mods and GLFW_MOD_SUPER == 0 && down.values.count { it } < 64
            if (down[key] == true) globalPresses.add(key)
        }
        val owned = down[key] == true
        if (action == GLFW_RELEASE) { down.remove(key); globalPresses.remove(key) }
        if (!owned) return
        if (queue.size >= 128) {
            overflow = true
            reset()
            if (action == GLFW_RELEASE) queue.addLast(GlobalKeyEvent(key, GLFW_RELEASE, 0))
            return
        }
        queue.addLast(GlobalKeyEvent(key, action, mods))
    }

    @Synchronized fun owns(key: Int): Boolean = key in globalPresses
    @Synchronized fun takeOverflow(): Boolean = overflow.also { overflow = false }
    @Synchronized fun clear() { down.clear(); globalPresses.clear(); queue.clear(); overflow = false }

    /** Drop pending actions, release owned holds, and ignore repeats until each physical key comes up. */
    @Synchronized fun reset() {
        val releases = queue.filter { it.action == GLFW_RELEASE }.associateBy { it.key }.toMutableMap()
        down.filterValues { it }.keys.forEach { releases[it] = GlobalKeyEvent(it, GLFW_RELEASE, 0) }
        queue.clear()
        queue.addAll(releases.values)
        down.replaceAll { _, _ -> false }
    }

    @Synchronized fun drain(): List<GlobalKeyEvent> = queue.toList().also { queue.clear() }
}

/** Browser pages renew only while focused; a crashed/closed page cannot disable keys permanently. */
class KeyboardFocusLease(private val nanoTime: () -> Long = System::nanoTime) {
    private val clients = LinkedHashMap<String, Long>()
    @Synchronized fun update(client: String, focused: Boolean) {
        active()
        if (!focused) clients.remove(client)
        else {
            if (client !in clients && clients.size >= 32) clients.remove(clients.keys.first())
            clients[client] = nanoTime() + 2_000_000_000L
        }
    }
    @Synchronized fun active(): Boolean {
        val now = nanoTime()
        clients.entries.removeIf { it.value <= now }
        return clients.isNotEmpty()
    }
}
