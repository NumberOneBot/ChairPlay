package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * USB developer simulation as the GPS output's channel (A4): one [UsbSession] while it sends, fed the car's place
 * [rateHz] times a second (the session keeps only the newest, latest-wins); the attached iPhone looked for each
 * [pollMillis]. [helper] is null while the app has none (no runtime), which makes USB unavailable, never CarPlay.
 */
class UsbGpsChannel(
    private val helper: () -> UsbHelper?,
    private val position: () -> GeoPoint?,
    private val rateHz: () -> Int,
    private val log: (String) -> Unit = {},
    private val pollMillis: Long = 1_000,
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "usb-gps").apply { isDaemon = true } },
) : GpsChannel, AutoCloseable {
    override val method = GpsMethod.USB
    override var onChanged: () -> Unit = {}

    @Volatile private var attached: UsbDevice? = null
    @Volatile private var session: UsbSession? = null
    @Volatile private var lastSends = 0L
    private var pump: ScheduledFuture<*>? = null
    private var watch: ScheduledFuture<*>? = null

    /** Starts looking for the attached iPhone. */
    fun start() {
        watch = scheduler.scheduleWithFixedDelay(::look, 0, pollMillis, TimeUnit.MILLISECONDS)
    }

    private fun look() {
        val found = helper()?.devices()
        // Several iPhones never really happen (D11): the first one.
        val now = (found as? UsbResult.Ok)?.value?.firstOrNull()
        // usbmux silent (Apple Devices closed) or no drivers: the cable may well be in; the setup says what to do.
        val silent = found is UsbResult.Failed && found.code in UNREACHABLE
        if (now?.udid != attached?.udid || silent != usbmuxSilent) {
            attached = now
            usbmuxSilent = silent
            onChanged()
        }
    }

    @Volatile private var usbmuxSilent = false

    override fun state(): ChannelState {
        if (helper() == null) return ChannelState(GpsReason.USB_HELPER_FAILED)
        if (usbmuxSilent) return ChannelState(GpsReason.USB_NOT_READY)
        val device = attached ?: return ChannelState(GpsReason.USB_NOT_CONNECTED)
        return ChannelState(null, device = device.view())
    }

    @Synchronized
    override fun start(seed: GeoPoint, device: UsbDevice?): GpsReason? {
        val helper = helper() ?: return GpsReason.USB_HELPER_FAILED
        if (device == null) return GpsReason.USB_NOT_CONNECTED
        if (session != null) return null
        val opened = when (val result = helper.openSession(device.udid)) {
            is UsbResult.Failed -> return reason(result.code).also { log("GPS: USB session not opened: ${result.code.key}") }
            is UsbResult.Ok -> result.value
        }
        opened.onLost = { code -> lost(opened, code) }
        session = opened
        opened.offer(seed)
        val period = 1_000L / rateHz().coerceIn(1, 20)
        pump = scheduler.scheduleAtFixedRate({ position()?.let { session?.offer(it) } }, period, period, TimeUnit.MILLISECONDS)
        return null
    }

    @Synchronized
    private fun lost(which: UsbSession, code: UsbErrorCode) {
        if (session !== which) return
        log("GPS: USB session lost: ${code.key}")
        stopPump()
        lastSends = which.acknowledged
        session = null
        onChanged()
    }

    @Synchronized
    override fun quiesce() {
        // Offers no more; the session's close drops what it still holds.
        stopPump()
    }

    private fun stopPump() {
        pump?.cancel(false)
        pump = null
    }

    @Synchronized
    override fun release(onStage: (OperationStage) -> Unit): StepOutcome {
        stopPump()
        val own = session ?: return StepOutcome.SKIPPED
        onStage(OperationStage.CLOSE)
        val closed = runCatching { own.close() }.fold({ StepOutcome.DONE }, { StepOutcome.FAILED })
        lastSends = own.acknowledged
        session = null
        return closed
    }

    override val running: Boolean get() = session != null

    override val sends: Long get() = session?.acknowledged ?: lastSends

    override fun close() {
        watch?.cancel(false)
        scheduler.shutdownNow()
    }

    companion object {
        private val UNREACHABLE = setOf(UsbErrorCode.USBMUX_UNAVAILABLE, UsbErrorCode.DRIVERS_MISSING)

        fun reason(code: UsbErrorCode): GpsReason = when (code) {
            UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> GpsReason.USB_NOT_CONNECTED
            UsbErrorCode.LOCKED, UsbErrorCode.NOT_TRUSTED, UsbErrorCode.DEVELOPER_MODE_OFF, UsbErrorCode.IMAGE_MISSING,
            UsbErrorCode.SERVICE_UNAVAILABLE -> GpsReason.USB_NOT_READY
            else -> GpsReason.USB_HELPER_FAILED
        }
    }
}

/** The page's name for an iPhone: never its UDID, a short hash of it as the reference (the setup's too). */
fun UsbDevice.view() = UsbDeviceView(
    MessageDigest.getInstance("SHA-256").digest(udid.toByteArray()).take(6).joinToString("") { "%02x".format(it) }, name,
)
