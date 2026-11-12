package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import io.github.numberonebot.chairplay.update.UpdateResult

/** The Windows Task Dialog in the existing desktop UI DLL, with Open/Cancel for an available release. */
internal object WindowsUpdateDialog {
    private interface Api : Library {
        fun cpw_update_dialog(heading: WString, text: WString, offerOpen: Int): Int
    }

    private val api: Api by lazy {
        val dll = WindowsWebView.library() ?: error("chairplay_webview.dll was not found")
        Native.load(dll.absolutePath, Api::class.java)
    }

    fun show(result: UpdateResult): Boolean {
        val (heading, text) = when (result) {
            is UpdateResult.Available -> "ChairPlay ${result.latest} is available" to
                (result.current?.let { "You have version $it." } ?: "You have a development build from ${result.built}.") +
                " Open the releases page on GitHub to download the update."
            UpdateResult.Current -> "ChairPlay is up to date" to "No newer release is available"
            UpdateResult.Failed -> "Couldn't check for updates" to "Check your internet connection and try again"
            UpdateResult.Development -> "This is a development build" to
                "This build has no release version to compare. Open the releases page on GitHub to see the latest version."
        }
        val offerOpen = result is UpdateResult.Available || result == UpdateResult.Development
        val button = api.cpw_update_dialog(WString(heading), WString(text), if (offerOpen) 1 else 0)
        check(button >= 0) { "Windows update dialog failed (HRESULT 0x${button.toUInt().toString(16)})" }
        return button == 100
    }
}
