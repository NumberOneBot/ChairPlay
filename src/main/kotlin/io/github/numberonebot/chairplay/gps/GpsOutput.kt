package io.github.numberonebot.chairplay.gps

// The GPS output's state as the GPS page reads it (state.gps.output): the method chosen and the switch saved, kept
// apart from whether the phone is reachable and whether positions go. Frozen by A0 (desktop-docs/usb-gps-spec/10-a0-contracts.md);
// a change is a contract change for the page and the setup.

/** gps.method: which channel takes the car's position to the iPhone. */
enum class GpsMethod(val key: String) {
    CARPLAY("carplay"),
    USB("usb");

    companion object {
        fun of(key: String): GpsMethod? = entries.firstOrNull { it.key == key }
    }
}

/** Whether the selected method can send now. */
enum class Availability(val key: String) { READY("ready"), UNAVAILABLE("unavailable"), PREPARING("preparing"), ERROR("error") }

/** Why the selected method does not send; codes, never English, for the page to word. */
enum class GpsReason(val key: String) {
    CARPLAY_NOT_CONNECTED("carplay-not-connected"),
    /** The connected session identified without the GPS this method needs: the user reconnects (D4). */
    CARPLAY_RECONNECT_NEEDED("carplay-reconnect-needed"),
    USB_SETUP_NEEDED("usb-setup-needed"),
    USB_NOT_CONNECTED("usb-not-connected"),
    USB_SEVERAL("usb-several"),
    /** Plugged in but a prerequisite went (trust, Developer Mode, the image): [UsbSetupView] says which. */
    USB_NOT_READY("usb-not-ready"),
    USB_HELPER_FAILED("usb-helper-failed"),
    /** The car is nowhere yet (placed=false): the page lets the user place it. */
    NO_POSITION("no-position"),
}

enum class OperationKind(val key: String) {
    SWITCH("switch"), ENABLE("enable"), DISABLE("disable"), RESUME("resume"), RESTORE("restore"), SHUTDOWN("shutdown"),
}

enum class OperationStage(val key: String) {
    QUIESCE("quiesce"), LOCATE("locate"), RETURN("return"), CLEAR("clear"), CLOSE("close"), START("start"),
}

/** The one operation running; while there is one, the whole app's input is held (S-09). */
data class GpsOperation(
    val id: Long,
    val generation: Long,
    val kind: OperationKind,
    val stage: OperationStage,
    val target: GpsMethod?,
    val startedAtMillis: Long,
)

/** Whether a USB simulation this app started may still be on the iPhone. */
enum class Cleanup(val key: String) { CLEAN("clean"), NEEDED("needed"), ATTEMPTING("attempting"), UNCONFIRMED("unconfirmed") }

enum class NoticeTone(val key: String) { INFO("info"), WARN("warn"), BAD("bad") }

enum class NoticeCode(val key: String) {
    /** Return, clear and close all went; info, gone after 10 s (UI-21, UI-21a). */
    RESET_SENT("reset-sent"),
    /** Windows gave no place to return to; args: reason = denied | unknown | timeout. */
    WINDOWS_UNAVAILABLE("windows-unavailable"),
    /** usb + enabled saved, no phone: the warning above Settings' content (UI-23); stays until it changes. */
    USB_UNAVAILABLE("usb-unavailable"),
    /** A reset was skipped or failed: the simulation may still be on the iPhone. */
    CLEANUP_UNCONFIRMED("cleanup-unconfirmed"),
    /** The connected CarPlay session needs a reconnect for the selected method's GPS (D4); stays until it matches. */
    RECONNECT_NEEDED("reconnect-needed"),
    /** The new method could not start; args: reason = a [GpsReason] key or a [UsbErrorCode] key. */
    START_FAILED("start-failed"),
}

data class GpsNotice(
    val id: Long,
    val tone: NoticeTone,
    val code: NoticeCode,
    val args: Map<String, String> = emptyMap(),
    val expiresAtMillis: Long? = null,
)

/** The USB iPhone as the page names it: [ref] is opaque (never the UDID), [name] the phone's own. */
data class UsbDeviceView(val ref: String, val name: String)

data class GpsOutput(
    val selected: GpsMethod,
    val enabledIntent: Boolean,
    val activeMethod: GpsMethod?,
    val running: Boolean,
    val availability: Availability,
    val reason: GpsReason?,
    /** A USB iPhone was prepared before (gps.usb.device saved): the "complete setup" hint goes. */
    val usbPrepared: Boolean,
    val device: UsbDeviceView?,
    /** Commands the method's channel took this run: CarPlay updates, or helper-acknowledged USB sets. */
    val successfulSends: Long,
    /** The method's fixes a second: gps.rate for CarPlay, gps.usb.rate for USB. */
    val rateHz: Int,
    /** Moves on with each accepted method change; a gps-method command names the one it saw. */
    val generation: Long,
    val operation: GpsOperation?,
    val cleanup: Cleanup,
    val notices: List<GpsNotice>,
) {
    fun toPage(): Map<String, Any?> = linkedMapOf(
        "selected" to selected.key,
        "enabledIntent" to enabledIntent,
        "activeMethod" to activeMethod?.key,
        "running" to running,
        "availability" to availability.key,
        "reason" to reason?.key,
        "usbPrepared" to usbPrepared,
        "device" to device?.let { mapOf("ref" to it.ref, "name" to it.name) },
        "successfulSends" to successfulSends,
        "rateHz" to rateHz,
        "generation" to generation,
        "operation" to operation?.let {
            linkedMapOf(
                "id" to it.id, "generation" to it.generation, "kind" to it.kind.key, "stage" to it.stage.key,
                "target" to it.target?.key, "startedAt" to it.startedAtMillis,
            )
        },
        "cleanup" to cleanup.key,
        "notices" to notices.map {
            linkedMapOf("id" to it.id, "tone" to it.tone.key, "code" to it.code.key, "args" to it.args, "expiresAt" to it.expiresAtMillis)
        },
    )
}
