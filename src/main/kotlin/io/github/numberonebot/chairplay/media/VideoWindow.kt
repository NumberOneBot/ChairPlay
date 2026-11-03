package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.WindowPlacement
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.config.fittedTo
import io.github.numberonebot.chairplay.config.isVisibleOn
import io.github.numberonebot.chairplay.config.openingSize
import io.github.numberonebot.chairplay.input.PointerEvent
import io.github.numberonebot.chairplay.input.Size
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.GL33C.*
import java.awt.image.BufferedImage
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The CarPlay screen in a GLFW window. GLFW wants its window and event loop on one thread (the main
 * one), so [run] owns that thread until the window closes. Other threads only call [show] and [close]:
 * [show] leaves the newest frame in an [AtomicReference] and wakes the loop, so a frame that arrives
 * while the previous one is still being drawn simply replaces it — nothing queues, latency stays one frame.
 * Each frame is released exactly once, by whichever side takes it out of [latest]: [show] for a frame
 * replaced unseen, the GL thread once the upload has copied the planes into textures.
 *
 * The window opens [width] x [height] where Windows puts it, or at [placement] when that is still on a monitor,
 * and hands where it was to [onPlacement] when it closes. Several windows share one loop through [runWindows].
 */
class VideoWindow(
    private val width: Int,
    private val height: Int,
    private val placement: WindowPlacement? = null,
    private val title: String = "ChairPlay",
    /** The picture's shape: the window keeps it while resized and opens with it; null resizes freely. */
    private var aspect: WindowSize? = null,
    /** False keeps the window at its size: no resizing by its edges, no maximize button. */
    private val resizable: Boolean = true,
    /** The smallest size its edges can be dragged to, before Windows' display scale; null leaves it to Windows. */
    private var minSize: WindowSize? = null,
    /** False when [minSize] is in screen pixels already, for a window with no page to scale with the display. */
    private val minScales: Boolean = true,
    /** True makes the close button hide the window, which [setShown] brings back, instead of closing it. */
    private val hideOnClose: Boolean = false,
    /** Whether the window shows as it opens; one that does not waits for [setShown]. */
    shown: Boolean = true,
    /**
     * True when [width] and [height] are a page's, in the web view's pixels: the window opens at them times the
     * monitor's display scale, follows it to a monitor of another scale, and takes back only its place.
     */
    private val pageSized: Boolean = false,
    /** The picture it shows, in its pixels: [minSize] never keeps the window above it, so it can always show it 1:1. */
    stream: WindowSize? = null,
    /** True moves the window by a press anywhere on it, as by its title bar; it then gets no left-button clicks. */
    private val dragAnywhere: Boolean = false,
) : Closeable {
    /** The picture it shows, as it opened with it or [setStream] changed it since. */
    @Volatile
    var stream: WindowSize? = stream
        private set
    private val streamRequest = AtomicReference<WindowSize?>()
    private val minRequest = AtomicReference<WindowSize?>()
    // The picture the window last took the shape and size of, on the GL thread.
    private var appliedStream: WindowSize? = stream
    private val latest = AtomicReference<DecodedFrame?>()
    private val closing = AtomicBoolean()
    private val minimizing = AtomicBoolean()
    val commands = WindowCommands(::wake)
    fun toggleFullscreen() = commands.toggleFullscreen()
    private val screenshotRequest = AtomicReference<((BufferedImage) -> Unit)?>()
    internal val titles = WindowTitle()
    private val shownRequest = AtomicReference<Boolean?>()
    private val resizeRequest = AtomicReference<WindowSize?>()
    private val clearRequest = AtomicBoolean()

    /** Safe from any thread: drops the picture shown, the window black until the next one, as a session ends. */
    fun clearPicture() {
        clearRequest.set(true)
        wake()
    }

    /** Runs on the GL thread when the client area takes another size, minimized aside. */
    @Volatile
    var onResize: (Size) -> Unit = {}

    /** A safe area to frame (insets in the picture's pixels) until [until] on System.nanoTime's clock. */
    private class FrameRequest(val top: Int, val bottom: Int, val left: Int, val right: Int, val until: Long, val of: Size?)

    @Volatile
    private var frame: FrameRequest? = null

    /**
     * Safe from any thread: frames the picture's safe area, the insets in the pixels of a frame [of] that size (the
     * picture's own when null), in green dots for [millis], over whatever the window shows, the picture or the HUD.
     */
    fun showFrame(top: Int, bottom: Int, left: Int, right: Int, millis: Long = 1_000, of: Size? = null) {
        frame = FrameRequest(top, bottom, left, right, System.nanoTime() + millis * 1_000_000, of)
        wake()
        // One more pass when it is due to go.
        Thread({
            Thread.sleep(millis + 20)
            wake()
        }, "frame-end").apply { isDaemon = true }.start()
    }

    /**
     * Safe from any thread: the picture is [size] from now on, as a connection with another display size brings. The
     * window takes its shape, if it keeps one, and its smallest size; one at one pixel per pixel of the old picture
     * goes to the new one's size, another keeps its width. [min] replaces the smallest size, for a minimum that follows
     * the picture's shape.
     */
    fun setStream(size: WindowSize, min: WindowSize? = null) {
        stream = size
        minRequest.set(min)
        streamRequest.set(size)
        wake()
    }

    /** Safe from any thread: gives the client area [width] x [height] screen pixels, a maximized window restored first. */
    fun resizeTo(width: Int, height: Int) {
        resizeRequest.set(WindowSize(width, height))
        wake()
    }

    /** Whether the window is on screen rather than hidden by its close button or by [setShown]. */
    @Volatile
    var visible = shown
        private set

    /** Runs on the GL thread when the window shows or hides. */
    @Volatile
    var onVisibility: (Boolean) -> Unit = {}

    /** Safe from any thread: shows or hides the window on the next loop pass; a shown one comes to the front. */
    fun setShown(shown: Boolean) {
        shownRequest.set(shown)
        wake()
    }

    /** What the window shows: [overlay] on black (the AltScreen's HUD), or the picture of the iPhone's view [key]. */
    private data class View(val hud: Boolean, val key: Any?)

    @Volatile
    private var view = View(false, null)
    // What to do once the window is black on the view wanted: ask the iPhone for it; true if it drew it already.
    private val onBlack = AtomicReference<(() -> Boolean)?>()

    /** Whether the window shows (or is going over to) [overlay] on black instead of the picture: the AltScreen's HUD. */
    val hud: Boolean get() = view.hud

    /**
     * Safe from any thread: shows the HUD ([hud]) or the iPhone's view [key], through black when that changes. [onBlack]
     * runs once the window is black, on a thread of its own, to ask the iPhone for the view: it starts drawing it only
     * then, so nothing of the change shows; the window stays black until the view's pictures are in (or [hud] needs none,
     * or [onBlack] says the iPhone drew the view already). Unchanged, [onBlack] runs at once. [log] tells the waits.
     */
    fun showView(hud: Boolean, key: Any? = null, onBlack: () -> Boolean = { false }) {
        // Under the HUD the iPhone's view is not seen: changing it is no change of what shows.
        this.onBlack.set(onBlack)
        view = View(hud, if (hud) null else key)
        wake()
    }

    /** The window's own lines in the app's log. */
    @Volatile
    var log: (String) -> Unit = {}
    private val reveal = RevealGate(log = { log(it) })

    /** What [hud] shows; set before [runWindows]. */
    var overlay: WindowOverlay? = null
    private var overlayOpen = false

    /** Drawn over whatever the window shows, the picture or [overlay]: the GPS car's gauges; set before [runWindows]. */
    var topLayer: WindowOverlay? = null
    private var topLayerOpen = false

    /** A native view over the picture (the web UI), laid out with the window; set before [runWindows]. */
    var child: WindowChild? = null

    /** Safe from any thread: draws the window again, as the HUD does when its data changes. */
    fun repaint() = wake()
    // GLFW rejects glfwPostEmptyEvent outside init..terminate, which frames and close() can fall outside of.
    private val running = AtomicBoolean()

    /**
     * Runs on the GL thread when a window that hides on its close button is asked to close from outside the app
     * (taskkill without /F), which asks the app to end; the close button and Alt+F4 only hide it.
     */
    @Volatile
    var onOutsideClose: () -> Unit = {}
    private val closeSource = CloseSource()

    /** Runs on the GL thread as the window closes, with its last place and restored size. */
    @Volatile
    var onPlacement: (WindowPlacement) -> Unit = {}

    /** Runs on the GL thread after a frame has been swapped to the screen. */
    @Volatile
    var onFrameShown: () -> Unit = {}

    /**
     * Receives left-button mouse samples on the GL thread, in framebuffer pixels (the space the picture is
     * letterboxed in). With [framebufferSize] and [pictureSize] that is all the mapping to the screen needs.
     */
    @Volatile
    private var inputHandler: (PointerEvent) -> Unit = {}

    @Volatile
    var framebufferSize = Size(0, 0)
        private set

    /** Size of the newest frame on screen; zero until the first one. */
    @Volatile
    var pictureSize = Size(0, 0)
        private set

    fun setInputHandler(handler: (PointerEvent) -> Unit) { inputHandler = handler }

    /** Receives GLFW key codes, actions (press, repeat, release) and modifier bits on the GL thread. */
    @Volatile var keyHandler: (key: Int, action: Int, mods: Int) -> Unit = { _, _, _ -> }
    @Volatile var characterHandler: (Int) -> Unit = {}
    @Volatile var inputFocusLost: () -> Unit = {}

    /** Receives the vertical scroll offset GLFW reports, on the GL thread. */
    @Volatile
    var scrollHandler: (dy: Double) -> Unit = {}

    fun show(frame: DecodedFrame) {
        latest.getAndSet(frame)?.release()
        wake()
    }

    /** Safe from any thread: iconifies the window and leaves the session running, like a car's Home button. */
    fun minimize() {
        minimizing.set(true)
        wake()
    }

    /**
     * Safe from any thread: hands the picture on screen, at the size the iPhone sent it, to [onImage] on a thread of
     * its own. Nothing happens before the first picture.
     */
    fun screenshot(onImage: (BufferedImage) -> Unit) {
        screenshotRequest.set(onImage)
        wake()
    }

    /** Safe from any thread: GLFW sets a title on the main thread only, so the next loop pass applies it. */
    fun setTitle(title: String) {
        titles.base(title)
        wake()
    }

    /** Safe from any thread; [run] then returns and releases the window. */
    override fun close() {
        closing.set(true)
        wake()
    }

    private fun wake() {
        if (running.get()) glfwPostEmptyEvent()
    }

    /** Creates the window and draws until it is closed by the user or by [close]. Call on the main thread. */
    fun run() = runWindows(this)

    // GL-thread state of an open window.
    private var handle = 0L
    private var capabilities: GLCapabilities? = null
    private var tracker: PlacementTracker? = null
    // The picture's programs and textures, made once the window's context is current.
    private var gl: PictureGl? = null
    // The child (the settings over CarPlay) and the HUD (over the AltScreen's picture) each come and go through black.
    private var childFade: Fade<Boolean>? = null
    private var viewFade: Fade<View>? = null
    private var picture: Size? = null
    private val w = IntArray(1)
    private val h = IntArray(1)

    /** Creates the window, hidden until it is in place, with its own GL context. GLFW is initialised. */
    internal fun open() {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        // Hidden until it is in place, so it does not flash at the default position first.
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, if (resizable) GLFW_TRUE else GLFW_FALSE)
        // GLFW scales a page's size by the monitor's scale as it creates the window, and again on WM_DPICHANGED.
        if (pageSized) glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE)
        val restore = placement?.takeIf { it.isVisibleOn(workAreas()) }
        val remembered = openingSize(WindowSize(width, height), restore, pageSized)
        val size = aspect?.let { remembered.fittedTo(it) } ?: remembered
        val window = glfwCreateWindow(size.width, size.height, title, 0, 0)
        check(window != 0L) { "cannot create the window" }
        handle = window.also(commands::open)
        // Windows applies it to edge drags only, so a maximized window still fills the screen.
        aspect?.let { glfwSetWindowAspectRatio(window, it.width, it.height) }
        if (restore != null) glfwSetWindowPos(window, restore.x, restore.y)
        TitleBar.paint(glfwGetWin32Window(window), dialog = !resizable)
        // A remembered or configured size below it grows to it here. It scales with the monitor, as the web view's
        // pages do, and follows the window to a monitor of another scale.
        // A window that keeps the picture's shape stops at the smallest size of that shape covering the minimum, so
        // the picture fills it there too.
        if (minSize != null) {
            limitSize(window)
            glfwSetWindowContentScaleCallback(window) { handle, _, _ -> limitSize(handle) }
        }
        if (hideOnClose) {
            closeSource.watch(glfwGetWin32Window(window))
            glfwSetWindowCloseCallback(window) { handle ->
                glfwSetWindowShouldClose(handle, false)
                // The close button hides the window; a close asked from outside (taskkill) ends the app.
                if (closeSource.userClosed()) applyShown(handle, false) else onOutsideClose()
            }
        }
        if (dragAnywhere) ClientDrag.install(glfwGetWin32Window(window))
        WindowIcon.apply(window)
        tracker = PlacementTracker(window, restore?.maximized ?: false)
        if (visible) {
            if (restore?.maximized == true) glfwMaximizeWindow(window) else glfwShowWindow(window)
        }
        glfwMakeContextCurrent(window)
        // Draw as soon as a frame is ready instead of waiting for vertical blank, which would add up to a frame.
        glfwSwapInterval(0)
        capabilities = GL.createCapabilities()
        installWindowInput(window, { event, size -> framebufferSize = size; inputHandler(event) },
            { key, action, mods -> keyHandler(key, action, mods) }, { scrollHandler(it) }, { characterHandler(it) }, { inputFocusLost() })
        val gl = PictureGl().also { gl = it }
        // Black from the start, like the title bar, instead of the system's white until the first frame.
        glfwGetFramebufferSize(window, w, h)
        gl.clear(w[0], h[0])
        glfwSwapBuffers(window)
        // While an edge is dragged Windows runs its own message loop, and runWindows' passes wait for it to end: the
        // window draws, and lays out the web view, from the repaints Windows asks for meanwhile, or the part it grows
        // by stays white until the mouse lets go.
        glfwSetWindowRefreshCallback(window) { _ -> step() }
        running.set(true)
        child?.attach(glfwGetWin32Window(window))
    }

    private fun limitSize(window: Long) {
        val min = minSize ?: return
        val scale = FloatArray(1)
        glfwGetWindowContentScale(window, scale, null)
        val size = windowSizeLimit(min, aspect, stream, scale[0], minScales)
        glfwSetWindowSizeLimits(window, size.width, size.height, GLFW_DONT_CARE, GLFW_DONT_CARE)
        minimumSize = Size(size.width, size.height)
    }

    /** [setStream] on the GL thread: the new picture's shape and smallest size, and the window's size for it. */
    private fun applyStream(window: Long, size: WindowSize) {
        val old = appliedStream
        appliedStream = size
        if (size == old) return
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetWindowSize(window, w, h)
        val wasActual = old != null && w[0] == old.width && h[0] == old.height
        if (aspect != null) {
            aspect = size
            glfwSetWindowAspectRatio(window, size.width, size.height)
        }
        limitSize(window)
        // A maximized window keeps filling the screen.
        if (glfwGetWindowMonitor(window) != 0L || glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE) return
        when {
            wasActual -> glfwSetWindowSize(window, size.width, size.height)
            aspect != null -> WindowSize(w[0], h[0]).fittedTo(size).let { glfwSetWindowSize(window, it.width, it.height) }
        }
    }

    /** The smallest client area the window takes, in screen pixels, with the monitor's scale; zero without a limit. */
    @Volatile
    var minimumSize = Size(0, 0)
        private set

    private fun applyShown(window: Long, shown: Boolean) {
        if (shown) {
            // A minimized window comes back too, not only a hidden one.
            if (glfwGetWindowAttrib(window, GLFW_ICONIFIED) == GLFW_TRUE) glfwRestoreWindow(window)
            glfwShowWindow(window)
            glfwFocusWindow(window)
        } else {
            glfwHideWindow(window)
        }
        if (shown != visible) {
            visible = shown
            onVisibility(shown)
        }
    }

    // Set while a pass runs, so a repaint Windows asks for in the middle of one does not start another.
    private var stepping = false

    /** One pass of the event loop for this window; false once it should close. */
    internal fun step(): Boolean {
        if (stepping) return true
        stepping = true
        try {
            return pass()
        } finally {
            stepping = false
        }
    }

    private fun pass(): Boolean {
        val window = handle
        if (glfwWindowShouldClose(window) || closing.get()) return false
        glfwMakeContextCurrent(window)
        GL.setCapabilities(capabilities)
        val gl = checkNotNull(gl)
        tracker?.let { commands.apply(window, it) }
        if (minimizing.getAndSet(false)) glfwIconifyWindow(window)
        shownRequest.getAndSet(null)?.let { applyShown(window, it) }
        streamRequest.getAndSet(null)?.let { size ->
            minRequest.getAndSet(null)?.let { minSize = it }
            applyStream(window, size)
        }
        resizeRequest.getAndSet(null)?.let { size ->
            if (glfwGetWindowMonitor(window) != 0L) return@let
            // A maximized window keeps filling the screen whatever size it is given.
            if (glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE) glfwRestoreWindow(window)
            glfwSetWindowSize(window, size.width, size.height)
        }
        if (clearRequest.getAndSet(false)) {
            latest.getAndSet(null)?.release()
            picture = null
        }
        val fresh = latest.getAndSet(null)
        if (fresh != null) {
            // glTexImage2D and glTexSubImage2D have read client memory by the time they return.
            gl.upload(fresh)
            reveal.picture(meanLuma(fresh))
            fresh.release()
            val size = Size(fresh.width, fresh.height)
            picture = size
            pictureSize = size
        }
        // A resize repaints the last picture even when no new one has arrived, or black before the first.
        glfwGetFramebufferSize(window, w, h)
        val resized = Size(w[0], h[0]).takeIf { it != framebufferSize && it.width > 0 && it.height > 0 }
        framebufferSize = Size(w[0], h[0])
        titles.next(framebufferSize, pictureSize)?.let { glfwSetWindowTitle(window, it) }
        resized?.let(onResize)
        // What the window shows changes through black: the settings over CarPlay, the AltScreen's HUD and its views.
        val cover = child?.let { visible -> (childFade ?: Fade(visible.covers).also { childFade = it }).apply { update(visible.covers) } }
        child?.layout(w[0], h[0], cover?.shown ?: true)
        val wanted = view
        val content = (viewFade ?: Fade(wanted).also { viewFade = it }).apply { update(wanted, reveal.open()) }
        if (content.shown == wanted) {
            onBlack.getAndSet(null)?.let { ask ->
                // Black on another of the iPhone's views: it waits for the view's pictures. The HUD needs none, and a
                // change taken back before black never left the view on screen.
                val wait = if (content.brightness == 0f && !wanted.hud) reveal.hold() else null
                Thread({
                    val drawn = ask()
                    wait?.let { reveal.sent(it, drawn) }
                }, "view-ask").apply { isDaemon = true }.start()
            }
        }
        val showsHud = content.shown.hud
        val shown = picture
        val layer = overlay
        when {
            showsHud -> {
                gl.clear(w[0], h[0])
                if (layer != null) {
                    if (!overlayOpen) {
                        layer.open()
                        overlayOpen = true
                    }
                    layer.paint(w[0], h[0])
                    // The overlay changes GL state; the picture's own is set again for the next pass.
                    gl.restore()
                }
            }
            shown == null -> gl.clear(w[0], h[0])
            else -> gl.draw(shown, w[0], h[0])
        }
        gl.dim(1f - minOf(cover?.brightness ?: 1f, content.brightness), w[0], h[0])
        topLayer?.let { top ->
            if (!topLayerOpen) {
                top.open()
                topLayerOpen = true
            }
            // Over the whole window, not the picture's letterbox, and after the dimming: a change of view fades under it.
            glViewport(0, 0, w[0], h[0])
            top.paint(w[0], h[0])
            gl.restore()
        }
        drawFrame(gl, w[0], h[0])
        val shot = screenshotRequest.getAndSet(null)
        if (shot != null && showsHud) {
            // The HUD is ours, not the iPhone's picture: save what the window shows.
            val pixels = gl.readFramebuffer(w[0], h[0])
            val (fw, fh) = w[0] to h[0]
            Thread({ shot(screenshotImage(fw, fh, pixels)) }, "screenshot").apply { isDaemon = true }.start()
        }
        glfwSwapBuffers(window)
        if (fresh != null) onFrameShown()
        if (shot != null && !showsHud && shown != null) {
            val pixels = gl.readPicture(shown)
            Thread({ shot(screenshotImage(shown.width, shown.height, pixels)) }, "screenshot").apply { isDaemon = true }.start()
        }
        return true
    }

    /** Hands over the place, frees the GL objects and destroys the window. */
    internal fun dispose() {
        val window = handle
        if (window == 0L) return
        running.set(false)
        latest.getAndSet(null)?.release()
        tracker?.let { runCatching { onPlacement(it.placement()) } }
        glfwMakeContextCurrent(window)
        GL.setCapabilities(capabilities)
        if (overlayOpen) runCatching { overlay?.close() }
        overlayOpen = false
        if (topLayerOpen) runCatching { topLayer?.close() }
        topLayerOpen = false
        child?.let { runCatching { it.detach() } }
        gl?.delete()
        gl = null
        glfwMakeContextCurrent(0)
        glfwDestroyWindow(window)
        handle = 0L
    }

    /** The safe-area frame [showFrame] asked for, while it lasts, over the picture as the window fits it in. */
    private fun drawFrame(gl: PictureGl, fw: Int, fh: Int) {
        val request = frame ?: return
        if (System.nanoTime() >= request.until) {
            frame = null
            return
        }
        if (fw == 0 || fh == 0) return
        val shape = frameShape(picture, stream, opened = Size(width, height))
        val rect = safeAreaRect(shape, fw, fh, request.top, request.bottom, request.left, request.right, request.of ?: shape)
        gl.frame(rect, fw, fh)
    }

    /** Whether a fade or the top layer's motion is under way, which needs passes of the loop at the screen's pace. */
    internal val animating: Boolean get() = childFade?.moving == true || viewFade?.moving == true || topLayer?.animating == true

}
