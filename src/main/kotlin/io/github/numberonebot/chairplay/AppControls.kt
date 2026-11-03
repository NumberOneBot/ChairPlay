package io.github.numberonebot.chairplay

import com.shilapi.xcertplay.airplay.AirPlaySession
import io.github.numberonebot.chairplay.config.AltscreenContent
import io.github.numberonebot.chairplay.drive.Driving
import io.github.numberonebot.chairplay.input.Control
import io.github.numberonebot.chairplay.input.ControlAction
import io.github.numberonebot.chairplay.input.ControlPad
import io.github.numberonebot.chairplay.input.ControlsConfig
import io.github.numberonebot.chairplay.input.KeyControls
import io.github.numberonebot.chairplay.input.KnobOwners
import io.github.numberonebot.chairplay.input.PageKeys
import io.github.numberonebot.chairplay.input.CarPlayTextInput
import io.github.numberonebot.chairplay.media.Volume
import io.github.numberonebot.chairplay.media.VideoWindow
import io.github.numberonebot.chairplay.ui.UiControls
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * What the car's controls do: the keys and the wheel of the app's windows, the controls window's buttons and a page's
 * keys, by the [bindings] in force (the Keyboard page's saves replace them, and the next press reads the new ones).
 * The app's own controls act at once ([screenshots], the [volume], drive mode on the GPS car [driving], and
 * [reconnect]); the rest go to the [active] session through [touchSender], so a tap and a key press reach the iPhone in
 * order. [changed] tells the controls window's page that what it shows changed.
 */
internal class AppControls(
    private val bindings: AtomicReference<ControlsConfig>,
    private val volume: Volume,
    private val driving: Driving,
    private val altView: AltscreenView,
    private val touchSender: ExecutorService,
    private val active: () -> AirPlaySession?,
    private val screenshots: () -> Unit,
    private val reconnect: () -> Unit,
    private val changed: () -> Unit,
) {
    /** The windows' keys and wheel as the bindings map them. */
    val keys = KeyControls { bindings.get() }
    val textInput = CarPlayTextInput(active, touchSender, ::log)
    private val knobOwners = KnobOwners()
    private var knobSession: AirPlaySession? = null
    val global = AppGlobalKeys(bindings::get, active, { key, control, session ->
        send(control, owner = "global:$key", expectedSession = session as AirPlaySession?)
    }, driving.control::key, changed)

    // The controls window's page: a button pressed and released there makes the Controls a key does, through ControlPad.
    private val pad = ControlPad()

    // The keys a page with the keyboard hands on, held and repeated apart from the window's own.
    private val pageKeys = PageKeys { bindings.get() }
    private val altHeld = AtomicBoolean()
    private var volumeTitle: VolumeTitle? = null

    /** Keeps transient volume feedback alive until the window loop is due to restore the title. */
    fun volumeTitlePass(): Double? = volumeTitle?.pass()

    /** Whether the iPhone plays, for the play/pause button's glyph; set once the CarPlay data exists. */
    var playing: () -> Boolean = { false }

    /**
     * A pass of the controls window's loop, which takes the keyboard back from its page once the mouse is up: its window
     * keeps the keyboard, and the page hands it back after each click. Set once the UI exists.
     */
    var controlsPass: () -> Unit = {}

    /** Switches the gauges between large and small and saves it, as the GPS page does; set once the UI exists. */
    var gaugeSize: () -> Unit = {}
    var fullscreen: () -> Unit = {}

    fun close() {
        global.close()
        // Finish final releases before Main closes the CarPlay session and its sender.
        runCatching { touchSender.submit {}.get(1, java.util.concurrent.TimeUnit.SECONDS) }
    }

    fun send(control: Control?, fullscreen: () -> Unit = this.fullscreen, owner: String = "local", expectedSession: AirPlaySession? = active()) {
        control ?: return
        if (active() !== expectedSession) return
        if (control == Control.Fullscreen) {
            fullscreen()
            return
        }
        if (control == Control.GaugeSize) {
            gaugeSize()
            return
        }
        if (control == Control.Screenshot) {
            screenshots()
            return
        }
        if (control is Control.VolumeStep) {
            volume.change(control.steps)
            volumeTitle?.show(volume.level, volume.muted)
            return
        }
        if (control == Control.MuteToggle) {
            volume.toggleMute()
            volumeTitle?.show(volume.level, volume.muted)
            return
        }
        if (control == Control.DriveMode) {
            when {
                driving.toggleKeyboard() -> log("drive mode on: the arrows drive the GPS car")
                driving.settings.enabled -> log("drive mode off")
                else -> log("drive mode needs the GPS: turn it on on the GPS page")
            }
            return
        }
        if (control == Control.Reconnect) {
            reconnect()
            return
        }
        val session = expectedSession ?: return
        touchSender.execute {
            if (active() !== session) return@execute
            if (knobSession !== session) { knobOwners.clear(); knobSession = session }
            when (control) {
                is Control.Knob -> knobOwners.apply(owner, control).forEach { session.sendKnob(it.state, it.momentary) }
                is Control.Media -> session.sendMedia(control.index)
                is Control.Telephony -> session.sendTelephony(control.index)
                Control.Siri -> session.invokeSiri()
                is Control.MapZoom -> repeat(abs(control.steps)) {
                    session.changeMapZoomLevel(zoomIn = control.steps > 0)
                }
                Control.NextAltscreenContent, Control.NextAltscreenCards, is Control.ShowAltscreenContent ->
                    altView.step(control, session)
                Control.Fullscreen, Control.Screenshot, Control.Reconnect, is Control.VolumeStep, Control.MuteToggle, Control.DriveMode, Control.GaugeSize -> Unit
            }
        }
    }

    /**
     * A window's keys, dropped while [covered] (a page covering the main window takes its keys itself, through PageKeys).
     * Alt held shows the keys on the controls window's buttons, as Windows shows access keys; GLFW releases it when a
     * window loses the focus. In drive mode the arrows are the car's pedals and wheel, not the knob.
     */
    fun windowKeys(fullscreen: () -> Unit = this.fullscreen, text: Boolean = false, covered: () -> Boolean): (Int, Int, Int) -> Unit = { key, action, mods ->
        if (key == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT) {
            val held = action != GLFW_RELEASE
            if (altHeld.getAndSet(held) != held) changed()
        }
        if (!covered() && !global.owns(key)) {
            if (text && (textInput.key(key, action, mods) || textInput.capturesKey(key, mods))) send(keys.key(key, GLFW_RELEASE, mods))
            else if (!driving.control.key(key, action, mods)) send(keys.key(key, action, mods), fullscreen)
        }
    }

    fun bindWindows(main: VideoWindow, alt: VideoWindow, controls: VideoWindow, mainVolumeTitle: (String?) -> Unit, covered: () -> Boolean) {
        global.wake = main::repaint
        volumeTitle = VolumeTitle({ controls.visible }, { notice ->
            controls.setTitle(notice?.let { "Controls · $it" } ?: "Controls")
        }, mainVolumeTitle)
        main.keyHandler = windowKeys(text = true, covered = covered)
        main.characterHandler = { if (!covered()) textInput.character(it) }
        main.inputFocusLost = textInput::release
        alt.keyHandler = windowKeys(fullscreen = alt::toggleFullscreen) { false }
        controls.keyHandler = windowKeys { false }
    }

    /** What the settings screen's pages and the controls window's buttons do. */
    val page = object : UiControls {
        override fun keyboardFocus(client: String, focused: Boolean) = global.focus(client, focused)
        override fun key(name: String, phase: String, everything: Boolean): Boolean {
            if (ControlsConfig.parseBinding(name)?.let { global.owns(it.key) } == true) return true
            val control = pageKeys.key(name, phase, everything) ?: return false
            send(control)
            return true
        }

        override fun control(action: String, down: Boolean): Boolean {
            val button = ControlAction.entries.firstOrNull { it.key == "key.$action" } ?: return false
            send(if (down) pad.press(button) else pad.release(button))
            if (!down) controlsPass()
            return true
        }

        override fun altscreenContent(content: String): Boolean {
            val target = AltscreenContent.entries.firstOrNull { it.key == content } ?: return false
            if (altView.content == null || active() == null) return false
            send(Control.ShowAltscreenContent(target))
            altView.bring()
            return true
        }

        override fun volume(level: Int): Boolean {
            volume.set(level)
            return true
        }

        override fun state(): Map<String, Any?> = linkedMapOf(
            "keys" to ControlAction.entries.associate { it.key.removePrefix("key.") to bindings.get().firstKey(it) },
            "volume" to mapOf("level" to volume.level, "muted" to volume.muted),
            "altscreen" to altView.content?.key,
            "altHeld" to altHeld.get(),
            "playing" to playing(),
            "globalKeyboard" to global.state(),
        )
    }
}
