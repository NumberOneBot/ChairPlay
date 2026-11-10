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
        val now = when (val found = helper()?.devices()) {
            is UsbResult.Ok -> found.value.singleOrNull()
            else -> null
        }
        if (now?.udid != attached?.udid) {
            attached = now
            onChanged()
        }
    }

    override fun state(): ChannelState {
        if (helper() == null) return ChannelState(GpsReason.USB_HELPER_FAILED)
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
        // Offers no more; the session's returnTo or close stops what it still holds.
        stopPump()
    }

    private fun stopPump() {
        pump?.cancel(false)
        pump = null
    }

    @Synchronized
    override fun release(returnTo: GeoPoint?, onStage: (OperationStage) -> Unit): Release {
        stopPump()
        val live = session
        val own = live ?: attached?.let { device ->
            // A simulation left by an earlier session or run: a session of its own, for the clear (V49, V50).
            (helper()?.openSession(device.udid) as? UsbResult.Ok)?.value
        } ?: return Release.NOTHING
        val returned = if (returnTo == null) StepOutcome.SKIPPED else {
            onStage(OperationStage.RETURN)
            outcome(own.returnTo(returnTo))
        }
        onStage(OperationStage.CLEAR)
        val cleared = outcome(own.clear())
        onStage(OperationStage.CLOSE)
        val closed = runCatching { own.close() }.fold({ StepOutcome.DONE }, { StepOutcome.FAILED })
        if (live != null) lastSends = live.acknowledged
        session = null
        return Release(returned, cleared, closed)
    }

    override val running: Boolean get() = session != null

    override val sends: Long get() = session?.acknowledged ?: lastSends

    override fun close() {
        watch?.cancel(false)
        scheduler.shutdownNow()
    }

    private fun outcome(result: UsbResult<Unit>) = if (result is UsbResult.Ok) StepOutcome.DONE else StepOutcome.FAILED

    companion object {
        fun reason(code: UsbErrorCode): GpsReason = when (code) {
            UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> GpsReason.USB_NOT_CONNECTED
            UsbErrorCode.SEVERAL_DEVICES -> GpsReason.USB_SEVERAL
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
