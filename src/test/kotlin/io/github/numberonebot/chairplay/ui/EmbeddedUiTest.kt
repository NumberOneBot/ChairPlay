package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedUiTest {
    private val calls = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private var embedded: Boolean? = null
    private var shows = true
    private var parentFocused = false
    private var pageFocused = false
    private var mouseHeld = false

    private val backend = object : WebViewBackend {
        override fun create(parent: Long): Long {
            calls += "create $parent"
            return 7
        }
        override fun navigate(view: Long, url: String) {
            calls += "navigate $url"
        }
        override fun setBounds(view: Long, width: Int, height: Int) {
            calls += "bounds ${width}x$height"
        }
        override fun setVisible(view: Long, visible: Boolean) {
            calls += if (visible) "show" else "hide"
        }
        override fun focus(view: Long) {
            calls += "focus view"
            parentFocused = false
        }
        override fun focusParent(parent: Long) {
            calls += "focus window"
            pageFocused = false
        }
        override fun parentHasFocus(parent: Long) = parentFocused
        override fun pageHasFocus(parent: Long) = pageFocused
        override fun mouseHeld() = mouseHeld
        override fun destroy(view: Long) {
            calls += "destroy $view"
        }
    }

    // The window's page names itself in its address; the browser it falls back to gets the plain one, as a tab.
    private fun ui(make: () -> WebViewBackend = { backend }) =
        EmbeddedUi("http://127.0.0.1:1/?window", { shows }, make, { opened += it }, { logged += it }, { embedded = it },
            browserUrl = "http://127.0.0.1:1/")

    @Test
    fun `the page opens hidden in the window and shows on the first layout`() {
        val ui = ui()

        ui.attach(42)
        ui.layout(1280, 720)

        assertEquals(listOf("create 42", "navigate http://127.0.0.1:1/?window", "bounds 1280x720", "show", "focus view"), calls)
        assertEquals(true, embedded)
    }

    // While CarPlay shows, the keys are CarPlay's again.
    @Test
    fun `CarPlay's turn hides the page and hands the keys back to the window`() {
        val ui = ui()
        ui.attach(42)
        ui.layout(1280, 720)
        calls.clear()

        shows = false
        ui.layout(1280, 720)
        ui.layout(1280, 720)

        assertEquals(listOf("hide", "focus window"), calls)
    }

    // The window fades through black: the page wants to cover it at once, but shows or hides when the window says.
    @Test
    fun `the page says what it wants, and shows or hides when the window says`() {
        val ui = ui()
        ui.attach(42)
        ui.layout(1280, 720)
        calls.clear()

        shows = false
        assertEquals(false, ui.covers)
        ui.layout(1280, 720, shown = true)
        assertEquals(emptyList<String>(), calls)

        ui.layout(1280, 720, shown = false)
        assertEquals(listOf("hide", "focus window"), calls)
    }

    @Test
    fun `the page follows the window's size`() {
        val ui = ui()
        ui.attach(42)
        ui.layout(1280, 720)
        calls.clear()

        ui.layout(1600, 900)

        assertEquals(listOf("bounds 1600x900"), calls)
    }

    // Activating the window again (Alt+Tab, its title bar) gives Windows' keyboard focus to the window itself.
    @Test
    fun `the page takes the keys back while it shows`() {
        val ui = ui()
        ui.attach(42)
        ui.layout(1280, 720)
        calls.clear()

        parentFocused = true
        ui.layout(1280, 720)

        assertEquals(listOf("focus view"), calls)
    }

    private fun controlsPage() =
        EmbeddedUi("http://127.0.0.1:1/controls", { true }, { backend }, { opened += it }, { logged += it }, { embedded = it },
            takesKeyboard = false)

    // The controls window's page: the window keeps the keyboard, so the keys stay CarPlay's, where with the page they
    // went nowhere or moved its focus about. A click anywhere on the page takes it; the window gets it back once the
    // mouse button is up, so a button held down is not let go of.
    @Test
    fun `a page that leaves the keys to the window gives them back once the mouse is up`() {
        val ui = controlsPage()
        ui.attach(42)
        ui.layout(720, 320)
        assertEquals(listOf("create 42", "navigate http://127.0.0.1:1/controls", "bounds 720x320", "show"), calls)
        calls.clear()

        pageFocused = true
        mouseHeld = true
        ui.layout(720, 320)
        assertEquals(emptyList<String>(), calls)
        mouseHeld = false
        ui.layout(720, 320)
        ui.layout(720, 320)

        assertEquals(listOf("focus window"), calls)
    }

    // Keyboard focus in another app is not the page's: the window does not take it.
    @Test
    fun `a page that leaves the keys to the window takes none it does not have`() {
        val ui = controlsPage()
        ui.attach(42)
        ui.layout(720, 320)
        calls.clear()

        parentFocused = true
        ui.layout(720, 320)
        parentFocused = false
        ui.layout(720, 320)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `without WebView2 the page opens in the browser at the plain address`() {
        val ui = ui { throw WebViewUnavailable("WebView2 is unavailable") }

        ui.attach(42)
        ui.layout(1280, 720)
        ui.detach()

        assertEquals(listOf("http://127.0.0.1:1/"), opened)
        assertEquals(false, embedded)
        assertTrue(logged.single(), "WebView2 is unavailable" in logged.single() && "browser" in logged.single())
        assertEquals(emptyList<String>(), calls)
    }

    // The controls window has no browser to fall back to: it says why and stays empty.
    @Test
    fun `a page with no browser to fall back to says why`() {
        val ui = EmbeddedUi("http://127.0.0.1:1/controls", { true }, { throw WebViewUnavailable("WebView2 is unavailable") },
            openBrowser = null, log = { logged += it }, onEmbedded = { embedded = it }, takesKeyboard = false)

        ui.attach(42)

        assertEquals(emptyList<String>(), opened)
        assertTrue(logged.single(), "WebView2 is unavailable" in logged.single() && "browser" !in logged.single())
    }

    // A missing DLL is an Error, not an Exception, and must not take the window down.
    @Test
    fun `a missing native library falls back too`() {
        val ui = ui { throw UnsatisfiedLinkError("chairplay_webview.dll") }

        ui.attach(42)

        assertEquals(1, opened.size)
        assertEquals(false, embedded)
    }

    @Test
    fun `the view goes with the window`() {
        val ui = ui()
        ui.attach(42)

        ui.detach()
        ui.layout(1280, 720)

        assertEquals("destroy 7", calls.last())
    }
}
