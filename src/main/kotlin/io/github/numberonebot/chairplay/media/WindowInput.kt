package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.input.PointerEvent
import io.github.numberonebot.chairplay.input.Size
import io.github.numberonebot.chairplay.input.toFramebuffer
import io.github.numberonebot.chairplay.platform.WindowHooks
import org.lwjgl.glfw.GLFW.*

/**
 * GLFW input callbacks, with pointer coordinates converted to framebuffer pixels, and the keyboard's track keys, which
 * GLFW does not report, from [hooks] to [key] as well.
 */
internal fun installWindowInput(
    window: Long,
    hooks: WindowHooks,
    pointer: (PointerEvent, Size) -> Unit,
    key: (Int, Int, Int) -> Unit,
    scroll: (Double) -> Unit,
    character: (Int) -> Unit = {},
    focusLost: () -> Unit = {},
) {
    var held = false
    fun emit(x: Double, y: Double) {
        val w = IntArray(1)
        val h = IntArray(1)
        val fw = IntArray(1)
        val fh = IntArray(1)
        glfwGetWindowSize(window, w, h)
        glfwGetFramebufferSize(window, fw, fh)
        val framebuffer = Size(fw[0], fh[0])
        pointer(PointerEvent(x, y, held).toFramebuffer(Size(w[0], h[0]), framebuffer), framebuffer)
    }
    glfwSetCursorPosCallback(window) { _, x, y -> if (held) emit(x, y) }
    glfwSetMouseButtonCallback(window) { _, button, action, _ ->
        // A mouse button that stands for a key presses and lets go of it, held as long as the button is.
        mouseButtonKey(button)?.let { return@glfwSetMouseButtonCallback key(it, action, 0) }
        if (button != GLFW_MOUSE_BUTTON_LEFT) return@glfwSetMouseButtonCallback
        held = action == GLFW_PRESS
        val x = DoubleArray(1)
        val y = DoubleArray(1)
        glfwGetCursorPos(window, x, y)
        emit(x[0], y[0])
    }
    glfwSetKeyCallback(window) { _, code, _, action, mods -> key(code, action, mods) }
    hooks.mediaKeys(window, key)
    glfwSetCharCallback(window) { _, codePoint -> character(codePoint) }
    glfwSetWindowFocusCallback(window) { _, focused -> if (!focused) focusLost() }
    glfwSetScrollCallback(window) { _, _, dy -> scroll(dy) }
}

/**
 * The key a mouse button stands for: the back button (XButton1) is Escape, which always goes back whatever the keys
 * are set to. Null for a button that is no key: the left one touches.
 */
internal fun mouseButtonKey(button: Int): Int? = if (button == GLFW_MOUSE_BUTTON_4) GLFW_KEY_ESCAPE else null
