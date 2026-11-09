package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.platform.OneCopyClaim
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable

class OneCopyTest {
    @Test fun `another copy running ends this one`() {
        val shown = mutableListOf<String>()
        val exits = mutableListOf<Int>()

        claimOneCopy(0, { shown += it }, { exits += it }, OneCopyClaim { _, _ -> null })

        assertEquals(1, shown.size)
        assertEquals(listOf(1), exits)
    }

    @Test fun `the only copy asks for the name and its wait, and goes on`() {
        val asked = mutableListOf<Pair<String, Long>>()
        val shown = mutableListOf<String>()
        val exits = mutableListOf<Int>()

        claimOneCopy(250, { shown += it }, { exits += it }, OneCopyClaim { name, wait -> asked += name to wait; Closeable { } })

        assertEquals(listOf(OneCopy.NAME to 250L), asked)
        assertEquals(emptyList<String>(), shown)
        assertEquals(emptyList<Int>(), exits)
    }
}
