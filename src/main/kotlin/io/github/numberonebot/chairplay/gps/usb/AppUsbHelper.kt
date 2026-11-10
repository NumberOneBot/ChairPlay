package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.gps.DeveloperToolsStore
import io.github.numberonebot.chairplay.gps.UsbHelper
import java.nio.file.Path

/**
 * The app's USB helper: the frozen one its image ships ([packaged]), or, for a development run, the package run by
 * the Python CHAIRPLAY_USB_GPS_PYTHON names from the folder CHAIRPLAY_USB_GPS_TOOLS names (tools/usb-gps); null with
 * neither, which leaves USB unavailable. Its store is the data folder's cache/developer-tools.
 */
fun appUsbHelper(
    dataDirectory: Path,
    packaged: Path?,
    log: (String) -> Unit,
    environment: (String) -> String? = System::getenv,
): UsbHelper? {
    val store = DeveloperToolsStore.root(dataDirectory)
    val launcher = packaged?.let { ProcessHelperLauncher.packaged(it, store) } ?: run {
        val python = environment("CHAIRPLAY_USB_GPS_PYTHON")?.takeIf { it.isNotBlank() } ?: return null
        val tools = environment("CHAIRPLAY_USB_GPS_TOOLS")?.takeIf { it.isNotBlank() } ?: return null
        log("USB GPS: development helper from $tools")
        ProcessHelperLauncher.module(Path.of(python), Path.of(tools), store)
    }
    return ProcessUsbHelper(launcher, log)
}
