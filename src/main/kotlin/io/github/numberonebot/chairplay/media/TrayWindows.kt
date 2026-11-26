package io.github.numberonebot.chairplay.media

/**
 * Which of the app's windows wait in the system tray while [enabled] (window.tray), as the owner set it
 * (2026-11-26): minimizing [main] takes it there with each of [others] [shown] then (not hidden by its close button);
 * the others minimize to the taskbar as usual, so only [main] reports [minimized]. A click on the icon brings back all
 * that wait there, or takes the windows there when none does. [move] hides a window into the tray (true) or brings it
 * back (false). The windows Windows has [main] own (the controls) go and come back with it on their own.
 */
internal class TrayWindows<W>(
    private val main: W,
    private val others: List<W>,
    private val shown: (W) -> Boolean,
    private val move: (W, Boolean) -> Unit,
) {
    private val waiting = LinkedHashSet<W>()

    /** Turned off, it brings back what waits in the area. */
    @Volatile
    var enabled = false
        set(value) = synchronized(this) {
            field = value
            if (!value) bringBack()
        }

    /** [main] was minimized: by its button, as it started, or by [move] hiding it, heard once more. */
    @Synchronized
    fun minimized() {
        if (!enabled || main in waiting) return
        for (each in listOf(main) + others.filter { shown(it) && it !in waiting }) {
            waiting += each
            move(each, true)
        }
    }

    /** The window came back from minimized, by [move] or another way (a call, the AltScreen's trigger). */
    @Synchronized
    fun restored(window: W) {
        waiting -= window
    }

    @Synchronized
    fun clicked() {
        if (!enabled) return
        if (waiting.isEmpty()) minimized() else bringBack()
    }

    // The main window last: the window brought back last takes the focus.
    private fun bringBack() {
        val back = waiting.sortedBy { it == main }
        waiting.clear()
        back.forEach { move(it, false) }
    }
}
