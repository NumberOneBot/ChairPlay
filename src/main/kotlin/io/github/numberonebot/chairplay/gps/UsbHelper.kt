package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import java.nio.file.Path

// The USB helper as the app's Kotlin side sees it: the port A2 implements over its JSON-lines helper process, and A1
// and A3 test against with fakes. Frozen by A0. Every call blocks for at most its own bounded time (A2 sets them,
// about 5 s an RPC); none throws for a phone's or helper's failure: those come back as [UsbResult.Failed].

/** Stable error codes from the helper; the page words them. */
enum class UsbErrorCode(val key: String) {
    NO_DEVICE("no-device"),
    SEVERAL_DEVICES("several-devices"),
    DRIVERS_MISSING("drivers-missing"),
    /** Apple's components are installed but their USB service is not answering: open Apple Devices, then probe again. */
    USBMUX_UNAVAILABLE("usbmux-unavailable"),
    LOCKED("locked"),
    NOT_TRUSTED("not-trusted"),
    DEVELOPER_MODE_OFF("developer-mode-off"),
    IMAGE_MISSING("image-missing"),
    SERVICE_UNAVAILABLE("service-unavailable"),
    UNSUPPORTED_IOS("unsupported-ios"),
    DOWNLOAD_FAILED("download-failed"),
    DISK_FULL("disk-full"),
    VERIFICATION_FAILED("verification-failed"),
    MOUNT_FAILED("mount-failed"),
    STORE_UNAVAILABLE("store-unavailable"),
    TIMEOUT("timeout"),
    CANCELLED("cancelled"),
    LINK_LOST("link-lost"),
    INVALID_COORDINATES("invalid-coordinates"),
    HELPER_FAILED("helper-failed"),
    PROTOCOL("protocol"),
}

sealed interface UsbResult<out T> {
    data class Ok<T>(val value: T) : UsbResult<T>
    data class Failed(val code: UsbErrorCode, val detail: String? = null) : UsbResult<Nothing>
}

/** Apple's USB components on this PC, found by package, process and usbmux, not by one service's name (F-04). */
enum class AppleComponents(val key: String) {
    READY("ready"),
    /** Installed (Apple Devices or iTunes) but usbmux does not answer yet. */
    NOT_RUNNING("not-running"),
    MISSING("missing"),
    UNKNOWN("unknown"),
}

/** A USB-attached iPhone; [udid] stays inside the app (logs and the page get [UsbDeviceView]). */
data class UsbDevice(val udid: String, val name: String, val iosVersion: String?)

enum class DeveloperModeStatus(val key: String) { OFF("off"), ON("on"), UNKNOWN("unknown") }

/** What a probe of one phone found; null where it could not tell. */
data class UsbProbe(
    val udid: String,
    val trusted: Boolean?,
    val locked: Boolean?,
    val developerMode: DeveloperModeStatus,
    val iosVersion: String?,
    /** The developer image is mounted on the phone (any format: Cryptex or personalized). */
    val imageMounted: Boolean?,
    /** The location simulation service answers; mounted alone is not ready (V26). */
    val serviceReady: Boolean?,
)

/** The developer tools for one phone: a compatible package on the PC, and the image on the phone. */
data class ToolsStatus(val packageCached: Boolean, val imageMounted: Boolean, val serviceReady: Boolean)

/** Cancels a long call (a download); the call then ends with [UsbErrorCode.CANCELLED]. */
fun interface Cancellation {
    fun cancelled(): Boolean
}

interface UsbHelper : AutoCloseable {
    fun components(): AppleComponents

    /** USB-attached iPhones only, never Wi-Fi ones. More than one is [UsbErrorCode.SEVERAL_DEVICES] for 1.1 (D6). */
    fun devices(): UsbResult<List<UsbDevice>>

    fun probe(udid: String): UsbResult<UsbProbe>

    /** Shows Developer Mode in the phone's Settings; never turns it on, accepts or restarts (F-07, V20). */
    fun revealDeveloperMode(udid: String): UsbResult<Unit>

    fun toolsStatus(udid: String): UsbResult<ToolsStatus>

    /**
     * Gets the phone's developer tools ready: downloads a package into the store only when none compatible is there
     * ([progress] with bytes), personalizes and mounts it, and checks the service. Sends no position.
     */
    fun prepareTools(udid: String, progress: (DownloadProgress) -> Unit, cancel: Cancellation): UsbResult<Unit>

    /** Opens the one simulation session with [udid]; a second open while one lives fails. */
    fun openSession(udid: String): UsbResult<UsbSession>
}

/**
 * One long-lived simulation session: [offer] keeps only the newest point and at most one set in flight
 * (latest-wins, no burst after a stall); [returnTo], [clear] and [close] go before any offered point.
 */
interface UsbSession : AutoCloseable {
    val id: String

    /** Sets the helper acknowledged in this session. */
    val acknowledged: Long

    fun offer(point: GeoPoint)

    /** Stops the stream and sets [point] once, the place to come back to. */
    fun returnTo(point: GeoPoint): UsbResult<Unit>

    /** Clears the simulation; "ok" means the service took the command, not that the phone's maps moved (S-04). */
    fun clear(): UsbResult<Unit>

    /** Called once when the session dies on its own (cable out, helper gone); not after [close]. */
    var onLost: (UsbErrorCode) -> Unit

    /** Ends the session and its helper; bounded, never throws. */
    override fun close()
}

/** Where the developer tools live: the app's data folder (beside settings.properties), cache/developer-tools (E-01). */
object DeveloperToolsStore {
    fun root(dataDirectory: Path): Path = dataDirectory.resolve("cache").resolve("developer-tools")

    /** A package is published when its folder holds manifest.json, written last (A2's store layout). */
    const val MANIFEST = "manifest.json"
}
