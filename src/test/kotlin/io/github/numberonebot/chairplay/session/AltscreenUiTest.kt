package io.github.numberonebot.chairplay.session

import io.github.numberonebot.chairplay.phone.ClusterControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AltscreenUiTest {
    /** The commands an iPhone got for its AltScreen, in order. */
    private class Phone : ClusterControl {
        val commands = mutableListOf<String>()
        override fun setClusterUrl(url: String, send: Boolean): Boolean {
            commands += if (send) "showUI $url" else "keep $url"
            return true
        }

        override fun setClusterUiShown(shown: Boolean): Boolean {
            commands += if (shown) "showUI" else "stopUI"
            return true
        }
    }

    private val ui = AltscreenUi()
    private val phone = Phone()

    // The iPhone draws the AltScreen's view only while it shows: under the HUD, or with the window closed, it is told
    // the view is not wanted (stopUI), and keeps the URL for when it is again.
    @Test
    fun `a view that shows is asked for, one that does not is stopped`() {
        ui.tell(phone, "maps:/car/instrumentcluster/map", seen = true)
        ui.tell(phone, "maps:/car/instrumentcluster/map", seen = false)

        assertEquals(
            listOf("showUI maps:/car/instrumentcluster/map", "keep maps:/car/instrumentcluster/map", "stopUI"),
            phone.commands,
        )
    }

    // Changing the cards under the HUD changes only the URL kept: the iPhone is not told to stop again.
    @Test
    fun `a stopped view is stopped once`() {
        ui.tell(phone, "a", seen = false)
        ui.tell(phone, "b", seen = false)

        assertEquals(listOf("keep a", "stopUI", "keep b"), phone.commands)
    }

    // The window coming back asks for the view again, which starts the iPhone drawing.
    @Test
    fun `a view seen again is asked for`() {
        ui.tell(phone, "a", seen = false)
        ui.tell(phone, "a", seen = true)
        ui.tell(phone, "a", seen = false)

        assertEquals(listOf("keep a", "stopUI", "showUI a", "keep a", "stopUI"), phone.commands)
    }

    // Each new AltScreen stream starts with the iPhone drawing its initial view, so a stop is sent anew; another
    // phone is told too.
    @Test
    fun `a new stream or another phone is told again`() {
        val other = Phone()
        ui.tell(phone, "a", seen = false)
        ui.newStream()
        ui.tell(phone, "a", seen = false)
        ui.tell(other, "a", seen = false)

        assertEquals(listOf("keep a", "stopUI", "keep a", "stopUI"), phone.commands)
        assertTrue(other.commands.contains("stopUI"))
    }
}
