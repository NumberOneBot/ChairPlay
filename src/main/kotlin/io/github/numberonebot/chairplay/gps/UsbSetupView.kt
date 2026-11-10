package io.github.numberonebot.chairplay.gps

// The USB setup wizard's state as the page reads it (state.gps.usbSetup). Frozen by A0; the setup controller (A3) makes it.

enum class SetupStep(val key: String) {
    USB("usb"), TRUST("trust"), DEVELOPER_MODE("developer-mode"), TOOLS("tools"), READY("ready"),
}

/**
 * A step's state. Unknown is not "missing": a check not made yet, or lost to a passing disconnect, keeps a step done
 * before as it was (03 §3).
 */
enum class StepStatus(val key: String) {
    UNKNOWN("unknown"),
    CHECKING("checking"),
    /** Waits on the user or the phone; the page shows Waiting. */
    WAITING("waiting"),
    /** Needs a button of the step (Get Apple Devices, Show Developer Mode, Download, Connect, Try again). */
    ACTION("action"),
    /** Something is going on the PC's side: downloading, connecting the image. */
    WORKING("working"),
    DONE("done"),
    ERROR("error"),
}

/**
 * [detail] says which state of the step, [error] what failed (status ERROR); both codes of 10-a0-contracts.md §4:
 * usb: no-device, drivers-missing, apple-devices-closed, several-devices, locked;
 * trust: trust-prompt, locked; developer-mode: hidden, revealed, link-lost;
 * tools: missing, downloading, cached, connecting, unsupported-ios.
 */
data class StepState(val status: StepStatus, val detail: String? = null, val error: String? = null)

enum class SetupIntent(val key: String) {
    /** Opened from USB setup: Done at the end leaves the switch as it was. */
    PREPARE("prepare"),
    /** Opened by turning the switch on with USB not ready: the final Enable USB GPS saves enabled (F-14, V56). */
    ENABLE("enable"),
    /** Opened to get a prepared USB back after a prerequisite went. */
    RESUME("resume");

    companion object {
        fun of(key: String): SetupIntent? = entries.firstOrNull { it.key == key }
    }
}

data class DownloadProgress(val receivedBytes: Long, val totalBytes: Long?)

data class UsbSetupView(
    val open: Boolean,
    val operationId: Long?,
    val generation: Long,
    val intent: SetupIntent,
    val device: UsbDeviceView?,
    val steps: Map<SetupStep, StepState>,
    /** The first step not done, the one shown open. */
    val current: SetupStep,
    val download: DownloadProgress?,
    /** Windows gave no return point: a warning on Ready, Enable USB GPS still works (F-11, UI-17). */
    val windowsWarning: Boolean,
    /** The car has a valid place; without one Enable waits for the user to set it (L-09). */
    val positionValid: Boolean,
) {
    fun toPage(): Map<String, Any?> = linkedMapOf(
        "open" to open,
        "operationId" to operationId,
        "generation" to generation,
        "intent" to intent.key,
        "device" to device?.let { mapOf("ref" to it.ref, "name" to it.name) },
        "steps" to steps.entries.associate { (step, state) ->
            step.key to linkedMapOf("status" to state.status.key, "detail" to state.detail, "error" to state.error)
        },
        "current" to current.key,
        "download" to download?.let { mapOf("receivedBytes" to it.receivedBytes, "totalBytes" to it.totalBytes) },
        "windowsWarning" to windowsWarning,
        "positionValid" to positionValid,
    )

    companion object {
        /** No wizard open: what the state carries between setups. */
        fun closed(generation: Long = 0) = UsbSetupView(
            open = false, operationId = null, generation = generation, intent = SetupIntent.PREPARE, device = null,
            steps = SetupStep.entries.associateWith { StepState(StepStatus.UNKNOWN) }, current = SetupStep.USB,
            download = null, windowsWarning = false, positionValid = false,
        )
    }
}
