package io.github.numberonebot.chairplay.platform.windows

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientDragTest {
    // The picture answers as a title bar does, so a press anywhere on it moves the window.
    @Test
    fun `the client area is the caption`() {
        assertEquals(ClientDrag.HTCAPTION, ClientDrag.hitTest(ClientDrag.HTCLIENT))
    }

    // The edges and corners keep sizing the window, and the title bar's buttons stay buttons.
    @Test
    fun `everything else stays as Windows answered`() {
        for (answer in listOf(ClientDrag.HTCAPTION, HTLEFT, HTBOTTOMRIGHT, HTCLOSE, HTNOWHERE)) {
            assertEquals(answer, ClientDrag.hitTest(answer))
        }
    }

    private companion object {
        const val HTNOWHERE = 0L
        const val HTLEFT = 10L
        const val HTBOTTOMRIGHT = 17L
        const val HTCLOSE = 20L
    }
}
