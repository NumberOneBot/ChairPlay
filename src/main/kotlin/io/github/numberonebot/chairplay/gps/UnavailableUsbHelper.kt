package io.github.numberonebot.chairplay.gps

/** The helper of an app that has none: every call fails as the helper's would, so USB is unavailable and nothing else. */
object UnavailableUsbHelper : UsbHelper {
    private val failed = UsbResult.Failed(UsbErrorCode.HELPER_FAILED, "no USB helper in this build")

    override fun components() = AppleComponents.UNKNOWN
    override fun devices() = failed
    override fun probe(udid: String) = failed
    override fun revealDeveloperMode(udid: String) = failed
    override fun toolsStatus(udid: String) = failed
    override fun prepareTools(udid: String, progress: (DownloadProgress) -> Unit, cancel: Cancellation) = failed
    override fun openSession(udid: String) = failed
    override fun close() {}
}
