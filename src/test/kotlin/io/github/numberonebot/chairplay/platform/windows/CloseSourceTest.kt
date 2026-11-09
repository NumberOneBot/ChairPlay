package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseSourceTest {
    private val source = CloseSource()

    // The close button and Alt+F4 come as WM_SYSCOMMAND SC_CLOSE, which Windows then turns into WM_CLOSE: the person
    // closing the window, which only hides.
    @Test
    fun `a close after SC_CLOSE is the person's`() {
        source.systemCommand(CloseSource.SC_CLOSE)

        assertTrue(source.userClosed())
    }

    // taskkill without /F posts WM_CLOSE alone, to one of the app's windows: the app is asked to end.
    @Test
    fun `a close with no SC_CLOSE before it comes from outside`() {
        assertFalse(source.userClosed())
    }

    // The low four bits of the command are Windows' own; another command is no close.
    @Test
    fun `only SC_CLOSE counts, whatever its low bits, and once`() {
        source.systemCommand(CloseSource.SC_CLOSE or 0x2)
        assertTrue(source.userClosed())
        assertFalse(source.userClosed())

        source.systemCommand(0xF020)
        assertFalse(source.userClosed())
    }
}
