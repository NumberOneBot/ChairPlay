package io.github.numberonebot.chairplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** TrayWindows: which windows go into the notification area and come back from it (window.tray). */
class TrayWindowsTest {
    private val moves = mutableListOf<String>()
    private val shown = mutableSetOf("main", "alt")
    private val tray = TrayWindows("main", listOf("alt"), { it in shown }) { window, on -> moves += if (on) "$window in" else "$window out" }

    @Test
    fun `minimizing the main window takes every shown window into the tray`() {
        tray.enabled = true

        tray.minimized("main")

        assertEquals(listOf("main in", "alt in"), moves)
    }

    @Test
    fun `a window closed by its close button stays where it is`() {
        tray.enabled = true
        shown -= "alt"

        tray.minimized("main")

        assertEquals(listOf("main in"), moves)
    }

    @Test
    fun `minimizing another window leaves it to the taskbar`() {
        tray.enabled = true

        tray.minimized("alt")

        assertEquals(emptyList<String>(), moves)
    }

    // Hiding a window minimizes it first, and its minimize is heard once more: it moves once.
    @Test
    fun `a window already in the tray is not moved again`() {
        tray.enabled = true
        tray.minimized("main")

        tray.minimized("main")
        tray.minimized("alt")

        assertEquals(listOf("main in", "alt in"), moves)
    }

    @Test
    fun `a click brings back what is in the tray, and the next takes the windows there again`() {
        tray.enabled = true
        tray.minimized("main")

        tray.clicked()
        tray.clicked()

        assertEquals(listOf("main in", "alt in", "alt out", "main out", "main in", "alt in"), moves)
    }

    // A call or the AltScreen's trigger can bring a window back without the tray: minimized again, it goes there again.
    @Test
    fun `a window restored another way leaves the tray`() {
        tray.enabled = true
        tray.minimized("main")
        tray.restored("main")
        moves.clear()

        tray.minimized("main")
        tray.clicked()

        assertEquals(listOf("main in", "alt out", "main out"), moves)
    }

    @Test
    fun `off, nothing goes into the tray`() {
        tray.minimized("main")
        tray.clicked()

        assertEquals(emptyList<String>(), moves)
    }

    // The main window comes back last, so it takes the focus.
    @Test
    fun `turning it off brings back what is in the tray`() {
        tray.enabled = true
        tray.minimized("main")

        tray.enabled = false

        assertEquals(listOf("main in", "alt in", "alt out", "main out"), moves)
    }
}
