package io.github.numberonebot.chairplay.gps

import io.github.numberonebot.chairplay.drive.GeoPoint
import io.github.numberonebot.chairplay.drive.GpsSettings
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// The coordinator's ports as the tests drive them: every call recorded in one list, in order, as "port.call args".

class Calls : CopyOnWriteArrayList<String>()

class FakeChannel(override val method: GpsMethod, private val calls: Calls) : GpsChannel {
    var state = ChannelState(null)
    var startFails: GpsReason? = null
    var release = Release(StepOutcome.DONE, StepOutcome.DONE, StepOutcome.DONE)
    /** Run inside quiesce, for a test that needs time to pass there. */
    var onQuiesce: () -> Unit = {}
    override var running = false
    override var sends = 0L
    override var onChanged: () -> Unit = {}

    override fun state() = state

    override fun start(seed: GeoPoint, device: UsbDevice?): GpsReason? {
        calls += "${method.key}.start ${seed.latitude},${seed.longitude}${device?.let { " ${it.udid}" }.orEmpty()}"
        if (startFails == null) running = true
        return startFails
    }

    override fun quiesce() {
        onQuiesce()
        calls += "${method.key}.quiesce"
        running = false
    }

    override fun release(returnTo: GeoPoint?, onStage: (OperationStage) -> Unit): Release {
        calls += "${method.key}.release ${returnTo?.let { "${it.latitude},${it.longitude}" } ?: "-"}"
        onStage(OperationStage.CLEAR)
        return release
    }

    /** The channel went on its own (a cable out). */
    fun lose() {
        running = false
        onChanged()
    }
}

class FakeCar(var at: GeoPoint? = GeoPoint(40.7, -74.0), private val calls: Calls) : GpsCar {
    override fun position() = at
    override fun place(point: GeoPoint) {
        calls += "car.place ${point.latitude},${point.longitude}"
        at = point
    }
}

class FakeStore(var settings: GpsSettings, private val calls: Calls) : GpsSettingsStore {
    override fun current() = settings
    override fun save(changes: Map<String, String>) {
        calls += "save " + changes.entries.joinToString(" ") { "${it.key}=${it.value}" }
        for ((key, value) in changes) settings = when (key) {
            "gps.method" -> settings.copy(method = GpsMethod.of(value)!!)
            "gps.enabled" -> settings.copy(enabled = value == "true")
            "gps.usb.device" -> settings.copy(usbDevice = value)
            else -> settings
        }
    }
}

class FakeJournal(var udid: String? = null, private val calls: Calls) : CleanupJournal {
    override fun dirty() = udid
    override fun mark(udid: String) {
        calls += "journal.mark $udid"
        this.udid = udid
    }
    override fun clear() {
        if (udid != null) calls += "journal.clear"
        udid = null
    }
}

class FakeSetup(private val calls: Calls) : UsbSetupPort {
    var readiness: UsbReadiness = UsbReadiness.Ready(PHONE)
    var view = UsbSetupView.closed()
    override var onFinish: (Long, UsbDevice, Boolean) -> CommandOutcome = { _, _, _ -> CommandOutcome.INVALID }
    override var onChanged: () -> Unit = {}
    override fun view() = view
    override fun open(intent: SetupIntent): CommandOutcome {
        calls += "setup.open ${intent.key}"
        view = view.copy(open = true, intent = intent, operationId = 9)
        return CommandOutcome.ACCEPTED
    }
    override fun act(operationId: Long, action: SetupAction, step: SetupStep?) = CommandOutcome.ACCEPTED
    override fun readiness(): UsbReadiness = readiness.also { calls += "setup.readiness" }

    /** The wizard's final button, its checks passed. */
    fun finish(enable: Boolean): CommandOutcome {
        view = view.copy(open = false)
        return onFinish(9, PHONE, enable)
    }

    companion object {
        val PHONE = UsbDevice("UDID-1", "iPhone", "27.2")
    }
}

class CountingHold : InputHold {
    @Volatile var held = 0
    override fun hold() { held++ }
    override fun release() { held-- }
}

/** A coordinator over fakes, with its thread to wait on. */
class GpsHarness(
    settings: GpsSettings = GpsSettings(enabled = false),
    var located: ReturnPoint = ReturnPoint.Found(HOME),
    journal: String? = null,
    timing: GpsTiming = GpsTiming(tickMillis = 60_000),
) {
    val calls = Calls()
    val store = FakeStore(settings, calls)
    val car = FakeCar(calls = calls)
    val carplay = FakeChannel(GpsMethod.CARPLAY, calls)
    val usb = FakeChannel(GpsMethod.USB, calls)
    val setup = FakeSetup(calls)
    val journal = FakeJournal(journal, calls)
    val hold = CountingHold()
    var now = 1_000L
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    val gps = GpsCoordinator(
        store, car, carplay, usb, setup, { _ -> calls += "locate"; located }, this.journal, hold,
        clock = { now }, timing = timing, worker = worker, watchdog = watchdog,
    )

    /** Waits for what the coordinator's thread has queued, a reconcile included. */
    fun idle() {
        repeat(3) { worker.submit {}.get(5, TimeUnit.SECONDS) }
    }

    fun reconcile() {
        gps.poke()
        idle()
    }

    fun output() = gps.output()

    companion object {
        val HOME = GeoPoint(49.84, 24.03)
    }
}
