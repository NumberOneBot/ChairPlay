package io.github.numberonebot.chairplay.ui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.io.File
import java.nio.file.Path

/**
 * WebView2, Edge's web view that comes with Windows 11 and updated Windows 10, through chairplay_webview.dll
 * (src/native/webview). The DLL is looked up on java.library.path and next to the exe, with WebView2Loader.dll
 * beside it loaded first so the DLL finds it. [userData] is the folder WebView2 keeps its profile in; [debug]
 * allows the developer tools (F12) and the context menu.
 */
class WebView2(private val userData: Path, private val debug: Boolean) : WebViewBackend {
    @Suppress("FunctionName")
    private interface Api : Library {
        fun cpw_create(parent: Pointer, userData: WString, debug: Int, error: ByteArray, capacity: Int): Pointer?
        fun cpw_navigate(view: Pointer, url: WString)
        fun cpw_set_bounds(view: Pointer, x: Int, y: Int, width: Int, height: Int)
        fun cpw_set_visible(view: Pointer, visible: Int)
        fun cpw_focus(view: Pointer)
        fun cpw_destroy(view: Pointer)
    }

    @Suppress("FunctionName")
    private interface Focus : Library {
        fun GetFocus(): Pointer?
        fun SetFocus(window: Pointer?): Pointer?
        fun IsChild(parent: Pointer, window: Pointer): Boolean
        fun GetAsyncKeyState(key: Int): Short
    }

    private val api: Api = load()
    private val focus: Focus = Native.load("user32", Focus::class.java)

    private fun load(): Api {
        val dirs = System.getProperty("java.library.path").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() } +
            listOfNotNull(System.getProperty("jpackage.app-path")?.let { File(it).parent })
        val dll = dirs.map { File(it, DLL) }.firstOrNull { it.isFile }
            ?: throw WebViewUnavailable("$DLL is neither next to the exe nor on java.library.path")
        File(dll.parentFile, LOADER).takeIf { it.isFile }?.let { NativeLibrary.getInstance(it.absolutePath) }
        return Native.load(dll.absolutePath, Api::class.java)
    }

    override fun create(parent: Long): Long {
        userData.toFile().mkdirs()
        val error = ByteArray(512)
        val view = api.cpw_create(Pointer(parent), WString(userData.toString()), if (debug) 1 else 0, error, error.size)
            ?: throw WebViewUnavailable(Native.toString(error, "UTF-8").ifEmpty { "WebView2 could not start" })
        return Pointer.nativeValue(view)
    }

    override fun navigate(view: Long, url: String) = api.cpw_navigate(Pointer(view), WString(url))

    override fun setBounds(view: Long, width: Int, height: Int) = api.cpw_set_bounds(Pointer(view), 0, 0, width, height)

    override fun setVisible(view: Long, visible: Boolean) = api.cpw_set_visible(Pointer(view), if (visible) 1 else 0)

    override fun focus(view: Long) = api.cpw_focus(Pointer(view))

    override fun focusParent(parent: Long) {
        focus.SetFocus(Pointer(parent))
    }

    override fun parentHasFocus(parent: Long): Boolean = focus.GetFocus()?.let { Pointer.nativeValue(it) } == parent

    override fun pageHasFocus(parent: Long): Boolean = focus.GetFocus()?.let { focus.IsChild(Pointer(parent), it) } ?: false

    // The high bit: the button is down now.
    override fun mouseHeld(): Boolean =
        listOf(VK_LBUTTON, VK_RBUTTON, VK_MBUTTON).any { focus.GetAsyncKeyState(it).toInt() and 0x8000 != 0 }

    override fun destroy(view: Long) = api.cpw_destroy(Pointer(view))

    companion object {
        const val DLL = "chairplay_webview.dll"
        const val LOADER = "WebView2Loader.dll"
        private const val VK_LBUTTON = 0x01
        private const val VK_RBUTTON = 0x02
        private const val VK_MBUTTON = 0x04
    }
}
