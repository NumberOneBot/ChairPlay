package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.drive.CarInput
import io.github.numberonebot.chairplay.drive.DriveControl
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.drive.GlfwGamepad
import io.github.numberonebot.chairplay.input.GamepadControls
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.ui.UiDriving

/**
 * The GPS car as the GPS page and its experiments reach it: drive mode, the car driven by a script, and the CarPlay
 * screens saved. Each change goes in on the next pass of the window loop, which [window]'s repaint wakes.
 */
internal fun uiDriving(driving: Driving, window: VideoWindow, screenshots: () -> Unit, log: (String) -> Unit) = object : UiDriving {
    init {
        // A game found, or gps.external saved off: the repaint wakes a loop waiting for an event, to follow or close it.
        driving.wake = window::repaint
    }

    override fun driveMode(on: Boolean): Boolean {
        val done = driving.driveMode(on)
        if (done) log("drive mode ${if (on) "on" else "off"} from the GPS page")
        window.repaint()
        return done
    }

    override fun gpsDrive(throttle: Double, brake: Double, steer: Double): Boolean {
        driving.control.scripted = CarInput(throttle, brake, steer)
        window.repaint()
        return driving.settings.enabled
    }

    override fun screenshot(): Boolean = true.also { screenshots() }
}

/**
 * The window loop's pass, on the main thread: the GPS car's, then a gamepad's buttons as the knob and the AltScreen's
 * zoom ([GamepadControls]) sent to [controls], the GPS on or off; a gamepad there is looked at now and then.
 */
internal fun windowPass(driving: Driving, controls: AppControls, buttons: GamepadControls = GamepadControls()): () -> Double? = {
    val globalDue = controls.global.pass()
    val due = driving.pass()
    val pad = GlfwGamepad.buttons()
    buttons.pass(pad).forEach(controls::send)
    listOfNotNull(due, globalDue, controls.volumeTitlePass(), pad?.let { DriveControl.GAMEPAD_POLL_SECONDS }).minOrNull()
}
