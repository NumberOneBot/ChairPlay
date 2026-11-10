package io.github.numberonebot.chairplay.gps

// What the page asks of the GPS output (A1's coordinator implements it, UiController hands the HTTP posts to it), and
// what the coordinator asks of the USB setup (A3 implements it). Frozen by A0. gps.method, gps.enabled and
// gps.usb.device are written only through these: the settings endpoint refuses them.

enum class CommandOutcome(val key: String) {
    ACCEPTED("accepted"),
    /** The same method again, or nothing to do: no operation (S-01, V13). */
    NO_CHANGE("no-change"),
    /** The generation or operation named is no longer the current one. */
    STALE("stale"),
    /** Another operation holds the app; the page waits for it. */
    BUSY("busy"),
    INVALID("invalid"),
    /** Enable with USB not ready: the setup opened with intent ENABLE, enabled still saved as it was (F-14, V56). */
    SETUP_OPENED("setup-opened"),
}

enum class SetupAction(val key: String) {
    REVEAL("reveal"),
    TOOLS("tools"),
    RETRY("retry"),
    /** The final Enable USB GPS: one atomic command, even with usb already selected (V44). */
    ENABLE("enable"),
    /** Done: setup finished, the switch as it was. */
    DONE("done"),
    CANCEL("cancel");

    companion object {
        fun of(key: String): SetupAction? = entries.firstOrNull { it.key == key }
    }
}

interface GpsCommands {
    fun output(): GpsOutput
    fun usbSetup(): UsbSetupView

    /** POST gps-method {method, generation}. */
    fun selectMethod(method: GpsMethod, generation: Long): CommandOutcome

    /** POST gps-enabled {enabled}. */
    fun setEnabled(enabled: Boolean): CommandOutcome

    /** POST usb-setup {action: "open", intent}. */
    fun openUsbSetup(intent: SetupIntent): CommandOutcome

    /** POST usb-setup {action, operationId, step?}; [step] only for retry. */
    fun usbSetupAction(operationId: Long, action: SetupAction, step: SetupStep? = null): CommandOutcome
}

/** Whether the prepared USB iPhone can send now, for the coordinator's start and resume. */
sealed interface UsbReadiness {
    data class Ready(val device: UsbDevice) : UsbReadiness
    data class NotReady(val reason: GpsReason, val step: SetupStep?) : UsbReadiness
}

/** The USB setup controller (A3), driven by the coordinator. */
interface UsbSetupPort {
    fun view(): UsbSetupView

    /** Opens the wizard, probes, and opens the first step not done; sends no position. */
    fun open(intent: SetupIntent): CommandOutcome

    fun act(operationId: Long, action: SetupAction, step: SetupStep? = null): CommandOutcome

    /** Probes the prepared phone without opening the wizard: at start, and when a USB iPhone arrives. */
    fun readiness(): UsbReadiness

    /**
     * Set by the coordinator. The wizard's ENABLE or DONE, after checking readiness again: the coordinator saves
     * gps.usb.device, and for ENABLE selects usb, saves enabled and starts the one writer, atomically.
     */
    var onFinish: (operationId: Long, device: UsbDevice, enable: Boolean) -> CommandOutcome

    /** Set by the coordinator: the view changed, for the page's next state. */
    var onChanged: () -> Unit
}
