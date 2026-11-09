package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.input.GlobalKeyboardCallbacks
import io.github.numberonebot.chairplay.input.GlobalKeyboardSource
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser.*
import com.sun.jna.ptr.IntByReference
import org.lwjgl.glfw.GLFW.*
import java.util.UUID

/** INPUTSINK only observes keys. No hooks, NOLEGACY, registrations of hotkeys, or synthetic key events. */
internal class WindowsRawKeyboard(private val callbacks: GlobalKeyboardCallbacks) : GlobalKeyboardSource {
    @Volatile private var running = true
    @Volatile private var window: HWND? = null
    private var blocked = false
    private var ownFocus = false
    private var missingForeground = false
    private val pressed = HashMap<Pair<Long, Int>, Int>()
    private val startupHeld = WindowsHeldAtStart()
    private val user get() = User32.INSTANCE
    private val raw get() = WindowsRawApi.instance
    private val thread = Thread(::run, "global-keyboard").apply { isDaemon = true }

    override fun start() { thread.start() }

    override fun close() {
        running = false
        window?.let { user.PostMessage(it, WM_CLOSE, WPARAM(), LPARAM()) }
        if (Thread.currentThread() !== thread) thread.join(2000)
    }

    private fun run() {
        if (!Platform.isWindows()) { callbacks.failed("Global shortcuts need Windows"); return }
        val name = "ChairPlayRawKeys-${UUID.randomUUID()}"
        var registered = false
        var sessionEvents = false
        var instance: HMODULE? = null
        var clazz: WNDCLASSEX? = null
        try {
            instance = Kernel32.INSTANCE.GetModuleHandle(null)
            val procedure = WindowProc { hwnd, message, wp, lp ->
                try {
                    when (message) {
                        0x00ff -> read(lp, wp.toInt() == 1) // WM_INPUT, RIM_INPUTSINK
                        0x00fe -> if (wp.toInt() == 2) releaseAll() // device removed
                        0x02b1 -> { // WTS session lock / unlock / disconnect / reconnect
                            when (wp.toInt()) {
                                2, 4, 7 -> blocked = true
                                1, 3, 8 -> blocked = false
                            }
                            releaseAll()
                        }
                        0x0218 -> { blocked = wp.toInt() == 4; releaseAll() } // WM_POWERBROADCAST
                        0x0113 -> reconcile() // WM_TIMER
                        WM_CLOSE -> user.PostQuitMessage(0)
                    }
                } catch (failure: Throwable) {
                    running = false
                    callbacks.failed("Global shortcuts stopped: ${failure.message ?: "Windows input error"}")
                    user.PostQuitMessage(0)
                }
                // WM_INPUT foreground delivery requires DefWindowProc for system cleanup.
                user.DefWindowProc(hwnd, message, wp, lp)
            }
            clazz = WNDCLASSEX().apply { hInstance = instance; lpszClassName = name; lpfnWndProc = procedure }
            check(user.RegisterClassEx(clazz).toInt() != 0) { "Cannot register keyboard window (${Native.getLastError()})" }
            window = user.CreateWindowEx(0, name, "", 0, 0, 0, 0, 0, null, null, instance, null)
            val hwnd = checkNotNull(window) { "Cannot create keyboard window (${Native.getLastError()})" }
            check(WindowsRawApi.keyboardTarget() == null) { "Another component already receives raw keyboard input" }
            WindowsRawApi.device(0x100 or 0x2000, hwnd).use {
                check(raw.RegisterRawInputDevices(it, 1, WindowsRawApi.deviceSize)) { "Cannot register keyboard input (${Native.getLastError()})" }
            }
            registered = true
            sessionEvents = KeyboardSessionApi.instance.WTSRegisterSessionNotification(hwnd, 0)
            check(sessionEvents) { "Cannot monitor Windows session changes (${Native.getLastError()})" }
            check(raw.SetTimer(hwnd, Pointer.createConstant(1), 100, null) != null) { "Cannot monitor held keys" }
            seedHeld()
            callbacks.ready()
            val message = MSG()
            while (running) {
                val result = user.GetMessage(message, null, 0, 0)
                check(result != -1) { "Cannot read keyboard messages (${Native.getLastError()})" }
                if (result == 0) break
                user.TranslateMessage(message)
                user.DispatchMessage(message)
            }
        } catch (failure: Throwable) {
            callbacks.failed("Global shortcuts unavailable: ${failure.message ?: "Windows input error"}")
        } finally {
            callbacks.reset()
            val hwnd = window
            if (hwnd != null) {
                if (registered && runCatching { WindowsRawApi.keyboardTarget() == hwnd.pointer }.getOrDefault(false)) {
                    WindowsRawApi.device(1, null).use { raw.RegisterRawInputDevices(it, 1, WindowsRawApi.deviceSize) }
                }
                if (sessionEvents) KeyboardSessionApi.instance.WTSUnRegisterSessionNotification(hwnd)
                raw.KillTimer(hwnd, Pointer.createConstant(1))
                user.DestroyWindow(hwnd)
            }
            window = null
            if (clazz != null) user.UnregisterClass(name, instance)
        }
    }

    private fun read(handle: LPARAM, backgroundDelivery: Boolean) {
        val size = IntByReference()
        val pointer = Pointer(handle.toLong())
        check(raw.GetRawInputData(pointer, 0x10000003, null, size, WindowsRawApi.headerSize) != -1)
        require(size.value in (WindowsRawApi.headerSize + 16)..4096)
        Memory(size.value.toLong()).use { data ->
            val read = raw.GetRawInputData(pointer, 0x10000003, data, size, WindowsRawApi.headerSize)
            check(read == size.value && read >= WindowsRawApi.headerSize + 16)
            if (data.getInt(0) != 1) return
            val offset = WindowsRawApi.headerSize.toLong()
            var scan = data.getShort(offset).toInt() and 0xffff
            var flags = data.getShort(offset + 2).toInt() and 0xffff
            val virtual = data.getShort(offset + 6).toInt() and 0xffff
            if (scan == 0) {
                val mapped = user.MapVirtualKeyEx(virtual, 4, user.GetKeyboardLayout(0))
                scan = mapped and 0xff
                flags = flags or if (mapped and 0xff00 == 0xe000) 2 else 0
            }
            val key = WindowsScanCodes.key(scan, flags, virtual) ?: return
            val device = Pointer.nativeValue(data.getPointer(8))
            val identity = device to key
            val down = flags and 1 == 0
            val was = pressed.keys.any { it.second == key } || startupHeld.contains(key, virtual)
            if (down) pressed[identity] = virtual else { pressed.remove(identity); startupHeld.release(key, virtual) }
            if (!down && pressed.keys.any { it.second == key }) return
            val phase = if (!down) GLFW_RELEASE else if (was) GLFW_REPEAT else GLFW_PRESS
            val foreground = ownsForeground()
            // Right Alt with Control is AltGr text entry, never Ctrl+Alt shortcuts.
            val altGr = held(GLFW_KEY_RIGHT_ALT) && (held(GLFW_KEY_LEFT_CONTROL) || held(GLFW_KEY_RIGHT_CONTROL))
            callbacks.key(key, phase, modifiers(), backgroundDelivery && !blocked && !foreground && !altGr)
            if (key == GLFW_KEY_PAUSE && down) { pressed.remove(identity); callbacks.key(key, GLFW_RELEASE, 0, false) }
        }
    }

    private fun isDown(virtual: Int) = user.GetAsyncKeyState(virtual).toInt() and 0x8000 != 0
    private fun held(key: Int) = key in startupHeld || pressed.keys.any { it.second == key }
    private fun modifiers(): Int =
        (if (held(GLFW_KEY_LEFT_SHIFT) || held(GLFW_KEY_RIGHT_SHIFT)) GLFW_MOD_SHIFT else 0) or
            (if (held(GLFW_KEY_LEFT_CONTROL) || held(GLFW_KEY_RIGHT_CONTROL)) GLFW_MOD_CONTROL else 0) or
            (if (held(GLFW_KEY_LEFT_ALT) || held(GLFW_KEY_RIGHT_ALT)) GLFW_MOD_ALT else 0) or
            (if (held(GLFW_KEY_LEFT_SUPER) || held(GLFW_KEY_RIGHT_SUPER)) GLFW_MOD_SUPER else 0)

    private fun ownsForeground(): Boolean {
        val hwnd = user.GetForegroundWindow() ?: return true
        val root = user.GetAncestor(hwnd, 3) ?: hwnd // GA_ROOTOWNER includes WebView2's hosting window.
        val process = IntByReference()
        user.GetWindowThreadProcessId(root, process)
        return process.value.toLong() == ProcessHandle.current().pid()
    }

    private fun seedHeld() {
        for (virtual in 8..254) if (virtual !in 0x10..0x12 && isDown(virtual)) {
            val scan = user.MapVirtualKeyEx(virtual, 4, user.GetKeyboardLayout(0))
            WindowsScanCodes.key(scan and 0xff, if (scan and 0xff00 == 0xe000) 2 else 0, virtual)?.let { startupHeld.remember(it, virtual) }
        }
    }

    private fun releaseAll() {
        callbacks.reset()
        val before = pressed.keys.map { it.second }.toSet() + startupHeld.keys
        pressed.clear(); startupHeld.clear(); seedHeld()
        (before - startupHeld.keys).forEach { callbacks.key(it, GLFW_RELEASE, 0, false) }
    }

    private fun reconcile() {
        val missing = user.GetForegroundWindow() == null
        if (missing != missingForeground) { missingForeground = missing; releaseAll() }
        val foreground = ownsForeground()
        if (foreground && !ownFocus) callbacks.reset()
        ownFocus = foreground
        startupHeld.released(::isDown).forEach { callbacks.key(it, GLFW_RELEASE, 0, false) }
        // Recover missing key-up (secure desktop, unplug, or focus transition); never synthesize key-down.
        val lost = pressed.filterValues { !isDown(it) }.keys.toList()
        for (identity in lost) {
            pressed.remove(identity)
            if (pressed.keys.none { it.second == identity.second }) callbacks.key(identity.second, GLFW_RELEASE, 0, false)
        }
        if (WindowsRawApi.keyboardTarget() != window?.pointer) error("Windows keyboard registration was replaced")
    }
}
