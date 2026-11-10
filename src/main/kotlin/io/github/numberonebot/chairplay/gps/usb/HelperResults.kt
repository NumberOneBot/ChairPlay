package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.DownloadProgress
import io.github.numberonebot.chairplay.gps.UsbProbe

/** The helper's results as the port's types; a missing or wrong-typed field throws, read as a protocol failure. */
internal object HelperResults {
    fun probe(udid: String, result: Map<String, Any?>) = UsbProbe(
        udid = udid,
        trusted = result["trusted"] as Boolean?,
        locked = result["locked"] as Boolean?,
        developerMode = DeveloperModeStatus.entries.firstOrNull { it.key == result["developerMode"] } ?: DeveloperModeStatus.UNKNOWN,
        iosVersion = result["ios"] as String?,
        imageMounted = result["imageMounted"] as Boolean?,
        serviceReady = result["serviceReady"] as Boolean?,
    )

    /** A download progress event's bytes; null for the other stages. */
    fun download(event: Map<String, Any?>): DownloadProgress? {
        if (event["event"] != "progress" || event["stage"] != "download") return null
        val received = event["receivedBytes"] as? Long ?: 0L
        return DownloadProgress(received, event["totalBytes"] as? Long)
    }
}
