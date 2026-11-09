package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.ui.WebViewBackend
import io.github.numberonebot.chairplay.update.UpdateResult
import java.nio.file.Path

/**
 * What the app asks of the operating system, one contract per concern. [Platforms.current] is the one the app runs on;
 * each adapter is made the first time it is asked for, so a system's libraries load only when they are used.
 */
internal interface Platform {
    /** The desktop's own facts: the system's name, its theme, and the app's data folder; its message box and pages. */
    val shell: DesktopShell

    /** The claim that keeps a second copy from running on this PC. */
    val oneCopy: OneCopyClaim

    /** TCP keep-alive timing for the session's sockets. */
    val keepAlive: KeepAliveTuning

    /** Where the system locates this PC. */
    val location: LocationSource

    /** The system firewall: whether the iPhone can get in, and adding the rule that lets it. */
    val firewall: Firewall

    /** The PC's Wi-Fi, its wired adapters and the addresses of its physical adapters. */
    val network: PcNetwork

    /** The PC's Bluetooth radio, its paired devices, and RFCOMM to them. */
    val bluetooth: Bluetooth

    /** The PC's default sound devices and its multichannel outputs. */
    val audio: SystemAudio

    /** The GPU video decoding FFmpeg can use here; null decodes in software only. */
    val video: HardwareVideo?

    /** What the system adds to the app's windows: their title bar, their close and drag, their taskbar button. */
    val windows: WindowHooks

    /** The web view that holds the settings page in a window; the library loads when this is called. */
    fun webView(userData: Path, debug: Boolean): WebViewBackend

    /** Shows the update dialog for [result]; true when the user chose to open the releases page. */
    fun updateDialog(result: UpdateResult): Boolean
}
