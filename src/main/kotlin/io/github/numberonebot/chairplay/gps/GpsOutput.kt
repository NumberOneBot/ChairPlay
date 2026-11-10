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
    /** Plugged in but a prerequisite went (trust, Developer Mode, the image): [UsbSetupView] says which. */
    USB_NOT_READY("usb-not-ready"),
    USB_HELPER_FAILED("usb-helper-failed"),
    /** The car is nowhere yet (placed=false): the page lets the user place it. */
    NO_POSITION("no-position"),
}

enum class OperationKind(val key: String) {
    /** APPLY: a reconnect puts the chosen method in force (D8): the old one stops, the new one starts. */
    APPLY("apply"), ENABLE("enable"), DISABLE("disable"), RESUME("resume"), SHUTDOWN("shutdown"),
}

enum class OperationStage(val key: String) {
    QUIESCE("quiesce"), LOCATE("locate"), RETURN("return"), CLEAR("clear"), CLOSE("close"), START("start"),
}

/** The one operation running; while there is one the switch waits and the car stands (D9). */
data class GpsOperation(
    val id: Long,
    val generation: Long,
    val kind: OperationKind,
    val stage: OperationStage,
    val target: GpsMethod?,
    val startedAtMillis: Long,
)

enum class NoticeTone(val key: String) { INFO("info"), WARN("warn"), BAD("bad") }

enum class NoticeCode(val key: String) {
    /** Return, clear and close all went; info, gone after 10 s (UI-21, UI-21a). */
    RESET_SENT("reset-sent"),
    /** Windows gave no place to return to; args: reason = denied | unknown | timeout. */
    WINDOWS_UNAVAILABLE("windows-unavailable"),
    /** usb + enabled saved, no phone: the warning above Settings' content (UI-23); stays until it changes. */
    USB_UNAVAILABLE("usb-unavailable"),
    /** The chosen method, or the switch, waits for a CarPlay reconnect (D4, D8); stays until it is in force. */
    RECONNECT_NEEDED("reconnect-needed"),
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
    /** The method the user chose (gps.method). */
    val selected: GpsMethod,
    /** The method in force: [selected] once a CarPlay reconnect applied it (D8). */
    val applied: GpsMethod,
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
    val notices: List<GpsNotice>,
) {
    fun toPage(): Map<String, Any?> = linkedMapOf(
        "selected" to selected.key,
        "applied" to applied.key,
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
        "notices" to notices.map {
            linkedMapOf("id" to it.id, "tone" to it.tone.key, "code" to it.code.key, "args" to it.args, "expiresAt" to it.expiresAtMillis)
        },
    )
}
