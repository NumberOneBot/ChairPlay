package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import io.github.numberonebot.chairplay.update.UpdateToast
import java.nio.file.Files
import java.nio.file.Path

/**
 * A Windows toast from the desktop UI DLL: bottom right, no focus taken, gone by itself; Do not disturb and game mode
 * hold it as any other. The app's ID is registered under HKCU, which an unpackaged app needs for a toast, so a
 * deploy without the installer's shortcut shows it too. Its buttons and body open web pages by protocol activation,
 * which works whether ChairPlay still runs or not; the app does not hear of them (owner, 2026-11-21: the simplest).
 */
internal object WindowsToast {
    private const val APP_ID = "NumberOneBot.ChairPlay"

    private interface Api : Library {
        fun cpw_toast(appId: WString, name: WString, icon: WString?, xml: WString): Int
    }

    private val api: Api by lazy {
        val dll = WindowsWebView.library() ?: error("chairplay_webview.dll was not found")
        Native.load(dll.absolutePath, Api::class.java)
    }

    fun show(toast: UpdateToast) {
        // The icon beside ChairPlay.exe; a run through java.exe has none, and Windows draws its own.
        val icon = ProcessHandle.current().info().command().map { Path.of(it).resolveSibling("ChairPlay.ico") }
            .filter(Files::isRegularFile).map { it.toString() }.orElse(null)
        val result = api.cpw_toast(WString(APP_ID), WString("ChairPlay"), icon?.let(::WString), WString(toastXml(toast)))
        check(result >= 0) { "Windows toast failed (HRESULT 0x${result.toUInt().toString(16)})" }
    }
}

/** [toast] in Windows' toast schema (ToastGeneric), its buttons and body opening their pages by protocol. */
internal fun toastXml(toast: UpdateToast): String {
    fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val texts = listOfNotNull(toast.title, toast.body).joinToString("") { "<text>${esc(it)}</text>" }
    val actions = toast.buttons.joinToString("") { (label, url) ->
        """<action content="${esc(label)}" activationType="protocol" arguments="${esc(url)}"/>"""
    }
    return """<toast activationType="protocol" launch="${esc(toast.open)}"><visual><binding template="ToastGeneric">""" +
        """$texts<text placement="attribution">${esc(toast.attribution)}</text></binding></visual>""" +
        "<actions>$actions</actions></toast>"
}
