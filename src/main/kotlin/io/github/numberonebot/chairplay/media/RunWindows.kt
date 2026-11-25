package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.appLog
import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.log.LogLevel
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback

/** GLFW's errors as warning lines in [log], each as `[glfw] 0x<code>: <description>`. */
fun glfwErrorCallback(log: AppLog): GLFWErrorCallback = GLFWErrorCallback.create { code, description ->
    log.log(LogLevel.WARNING, "[glfw] 0x${code.toString(16)}: ${GLFWErrorCallback.getDescription(description)}")
}

/**
 * GLFW's default window hints. With [startupShow] the window's first show takes the show state its starter gave the
 * process (STARTUPINFO: a shortcut's Run, `start /min`, or CreateProcess with SW_SHOWMINNOACTIVE), so it can start
 * minimized; without, or when the starter gave none, it shows as GLFW shows any window.
 */
internal fun defaultWindowHints(startupShow: Boolean) {
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_WIN32_SHOWDEFAULT, if (startupShow) GLFW_TRUE else GLFW_FALSE)
}

/** The wait between passes while a fade runs: a little over a frame at 120 Hz. */
private const val FADE_FRAME_SECONDS = 0.008

/**
 * Runs the GLFW event loop for [main] and [others] on the calling (main) thread until [main] closes. GLFW wants
 * every window and its events on one thread, so the windows take turns after each wake-up. Closing one of
 * [others] closes only that window. [pass] runs on each pass, before the windows draw, and says how soon the next
 * is due (the GPS car while it drives); null leaves the loop to sleep until something happens. [opened] runs once every
 * window is open, as the loop starts.
 */
fun runWindows(main: VideoWindow, vararg others: VideoWindow, pass: () -> Double? = { null }, opened: () -> Unit = {}) {
    glfwErrorCallback(appLog).set()
    check(glfwInit()) { "cannot initialise GLFW" }
    val open = mutableListOf(main)
    try {
        main.open()
        for (window in others) {
            window.open()
            open += window
        }
        opened()
        var due: Double? = null
        while (true) {
            // A fade under way draws at about the screen's pace; otherwise the loop sleeps until something happens.
            val wait = listOfNotNull(due, FADE_FRAME_SECONDS.takeIf { open.any { it.animating } }).minOrNull()
            if (wait != null) glfwWaitEventsTimeout(wait) else glfwWaitEvents()
            due = pass()
            if (!main.step()) break
            for (window in open.drop(1)) {
                if (!window.step()) {
                    window.dispose()
                    open -= window
                }
            }
        }
    } finally {
        open.forEach { it.dispose() }
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}
