package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.media.WindowChild

/**
 * A system web view inside a window, as the UI needs it. Every call comes on the window's thread, whose message loop
 * also runs the view's. Windows has [WebView2]; a macOS WKWebView in an NSWindow fits behind the same calls.
 */
interface WebViewBackend {
    /** Creates the view, hidden, as a child of [parent]; throws with the reason when there is no web view to have. */
    fun create(parent: Long): Long
    fun navigate(view: Long, url: String)

    /** Covers the parent's client area from its top-left corner, in pixels. */
    fun setBounds(view: Long, width: Int, height: Int)
    fun setVisible(view: Long, visible: Boolean)

    /** Gives the page the keyboard. */
    fun focus(view: Long)

    /** Gives the window itself, and so CarPlay, the keyboard. */
    fun focusParent(parent: Long)

    /** Whether the window itself has the keyboard rather than the page. */
    fun parentHasFocus(parent: Long): Boolean

    /** Whether the page, or any window inside [parent], has the keyboard. */
    fun pageHasFocus(parent: Long): Boolean

    /** Whether a mouse button is down. */
    fun mouseHeld(): Boolean
    fun destroy(view: Long)
}

class WebViewUnavailable(message: String) : Exception(message)

/**
 * The web UI as the main window's [WindowChild]: it covers the window, and has the keyboard, while [showsUi] says
 * so ([covers]), and hides otherwise, so that CarPlay's picture shows and takes the keys; the window shows and hides
 * it through a fade. [backend] is made in [attach], on the
 * window's thread; when it cannot be (no WebView2 runtime, no DLL), [url] opens in the default browser instead and
 * the log says why. [onEmbedded] hears which of the two it is.
 *
 * Without [takesKeyboard] (the controls window) the window keeps the keyboard, so its keys stay CarPlay's: a click on
 * the page takes it, and the window takes it back at the first layout with the mouse button up, so a button held on
 * the page is not let go of. The page never keeps it, where its keys went nowhere or moved its focus about.
 */
class EmbeddedUi(
    private val url: String,
    private val showsUi: () -> Boolean,
    private val backend: () -> WebViewBackend,
    /** Opens [url] when there is no web view to have; null leaves the window empty. */
    private val openBrowser: ((String) -> Unit)?,
    private val log: (String) -> Unit,
    private val onEmbedded: (Boolean) -> Unit,
    private val takesKeyboard: Boolean = true,
) : WindowChild {
    private var web: WebViewBackend? = null
    private var parent = 0L
    private var view = 0L
    private var width = -1
    private var height = -1
    private var visible = false

    override fun attach(nativeWindow: Long) {
        parent = nativeWindow
        try {
            val made = backend()
            view = made.create(nativeWindow)
            made.navigate(view, url)
            web = made
        } catch (error: Throwable) {
            // A missing DLL is an UnsatisfiedLinkError; nothing here may take the window down.
            if (error is VirtualMachineError) throw error
            val reason = error.message ?: error.javaClass.simpleName
            onEmbedded(false)
            val browser = openBrowser
            if (browser == null) {
                log("the page cannot show in the window ($reason)")
                return
            }
            log("the UI cannot show in the window ($reason); opening it in the default browser: $url")
            runCatching { browser(url) }.onFailure { log("could not open the browser: $it") }
            return
        }
        onEmbedded(true)
    }

    override val covers: Boolean get() = showsUi()

    override fun layout(width: Int, height: Int, shown: Boolean) {
        val made = web ?: return
        if (width != this.width || height != this.height) {
            this.width = width
            this.height = height
            made.setBounds(view, width, height)
        }
        val show = shown
        if (!takesKeyboard) {
            if (show != visible) {
                visible = show
                made.setVisible(view, show)
            }
            if (made.pageHasFocus(parent) && !made.mouseHeld()) made.focusParent(parent)
            return
        }
        if (show != visible) {
            visible = show
            made.setVisible(view, show)
            if (show) made.focus(view) else made.focusParent(parent)
        } else if (show && made.parentHasFocus(parent)) {
            made.focus(view)
        }
    }

    override fun detach() {
        val made = web ?: return
        web = null
        made.destroy(view)
    }
}
