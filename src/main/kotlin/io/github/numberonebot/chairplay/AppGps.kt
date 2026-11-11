package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.carplay.CarPlayGpsChannel
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import io.github.numberonebot.chairplay.gps.DeadlineLocator
import io.github.numberonebot.chairplay.gps.GpsCar
import io.github.numberonebot.chairplay.gps.GpsCoordinator
import io.github.numberonebot.chairplay.gps.GpsSettingsStore
import io.github.numberonebot.chairplay.gps.InputHold
import io.github.numberonebot.chairplay.gps.UnavailableUsbHelper
import io.github.numberonebot.chairplay.gps.UsbGpsChannel
import io.github.numberonebot.chairplay.gps.UsbHelper
import io.github.numberonebot.chairplay.gps.setup.ThreadSetupExecutor
import io.github.numberonebot.chairplay.gps.setup.UsbSetupController
import io.github.numberonebot.chairplay.platform.Platforms

/**
 * The GPS output as the app makes it: CarPlay's and USB's channels around the one [driving] car, the USB setup, and
 * the coordinator that owns them (gps/GpsCoordinator.kt). [saveGps] is the settings' save for the coordinator's own
 * keys; [session] says whether a CarPlay session runs.
 */
internal class AppGps(
    private val driving: Driving,
    saveGps: (Map<String, String>) -> Unit,
    session: () -> Boolean,
    log: (String) -> Unit,
    /** Null in an app without the USB helper ([io.github.numberonebot.chairplay.gps.usb.appUsbHelper]). */
    private val helper: UsbHelper? = null,
) {
    val carplay = CarPlayGpsChannel(session) { driving.location }

    private val position: () -> GeoPoint? = { if (driving.placed) driving.drive.snapshot().position else null }

    private val usb = UsbGpsChannel({ helper }, position, { driving.settings.usbRate }, log)

    // Windows' place, asked once at a time for the setup and the coordinator alike (V47).
    private val locator = DeadlineLocator(log, Platforms.current.location::read)

    private val setup = UsbSetupController(
        helper ?: UnavailableUsbHelper, locator, { driving.placed }, { driving.settings.usbDevice }, ThreadSetupExecutor(log),
    )

    val coordinator = GpsCoordinator(
        store = object : GpsSettingsStore {
            override fun current(): GpsSettings = driving.settings
            override fun save(changes: Map<String, String>) = saveGps(changes)
        },
        car = object : GpsCar {
            override fun position(): GeoPoint? = this@AppGps.position()
        },
        carplay = carplay,
        usb = usb,
        setup = setup,
        locator = locator,
        // The window loop's next pass puts a hold, or its end, in force on the car.
        hold = object : InputHold {
            override fun hold() = driving.wake()
            override fun release() = driving.wake()
        },
        log = log,
    )

    init {
        driving.live = coordinator::carLive
        driving.held = { coordinator.holding }
    }

    fun start() {
        usb.start()
        coordinator.start()
    }

    /** The GPS page's state: the car, the output and the USB setup. */
    fun state(): Map<String, Any?> = driving.state() + mapOf(
        "output" to coordinator.output().toPage(),
        "usbSetup" to coordinator.usbSetup().toPage(),
    )

    fun close() {
        coordinator.close()
        setup.close()
        usb.close()
        helper?.close()
    }
}
