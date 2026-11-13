package io.github.numberonebot.chairplay.platform

/** Which of a window's closes its close button (or Alt+F4) asked for, rather than something outside the app. */
fun interface CloseWatch {
    /** Whether the close being handled followed the close button or Alt+F4; asked once per close. */
    fun userClosed(): Boolean
}

/**
 * What the system adds to the app's GLFW windows beyond GLFW, each [window] a GLFW handle. Every call runs on the GL
 * thread, which pumps the window's messages.
 */
interface WindowHooks {
    /** The system's own handle of [window], for a native view to attach to. */
    fun nativeHandle(window: Long): Long

    /** Paints the title bar dark; a [dialog] window, of a fixed size, keeps only its close button. */
    fun paintTitleBar(window: Long, dialog: Boolean)

    /** Follows the window's messages from now on, to tell its close button from a close asked from outside. */
    fun watchClose(window: Long): CloseWatch

    /** Moves the window by a press anywhere on its picture, as by its title bar; its edges keep sizing it. */
    fun dragByPicture(window: Long)

    /**
     * Hands the keyboard's track keys to [key] while the window has the focus, as GLFW hands it keys (action GLFW_PRESS,
     * GLFW_REPEAT or GLFW_RELEASE, no modifiers) under MediaKeys' codes, and keeps them from the system, so no other
     * player acts on them too.
     */
    fun mediaKeys(window: Long, key: (key: Int, action: Int, mods: Int) -> Unit)

    /** Flashes the window's taskbar button until the window comes to the front ([on]), or stops it. */
    fun flash(window: Long, on: Boolean)
}
