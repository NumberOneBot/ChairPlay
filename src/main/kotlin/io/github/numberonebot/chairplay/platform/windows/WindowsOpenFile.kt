package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import java.nio.file.Path
import java.nio.file.Paths

/** Windows' Open dialog in the existing desktop UI DLL; it opens on the calling thread, which is not GLFW's. */
internal object WindowsOpenFile {
    private const val PATH_CHARS = 32_768

    private interface Api : Library {
        fun cpw_open_file(title: WString, filterName: WString, filterSpec: WString, path: CharArray, pathChars: Int): Int
    }

    private val api: Api by lazy {
        val dll = WindowsWebView.library() ?: error("chairplay_webview.dll was not found")
        Native.load(dll.absolutePath, Api::class.java)
    }

    /** The file the user chose, or null when they cancelled. [patterns] are like `*.gpx`. */
    fun show(title: String, filterName: String, patterns: List<String>): Path? {
        val buffer = CharArray(PATH_CHARS)
        val result = api.cpw_open_file(WString(title), WString(filterName), WString(patterns.joinToString(";")), buffer, PATH_CHARS)
        check(result >= 0) { "Windows open dialog failed (HRESULT 0x${result.toUInt().toString(16)})" }
        if (result == 0) return null
        return Paths.get(String(buffer, 0, buffer.indexOf('\u0000')))
    }
}
