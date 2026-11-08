package io.github.numberonebot.chairplay.input

import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.phone.CallKey
import io.github.numberonebot.chairplay.phone.KnobInput
import io.github.numberonebot.chairplay.phone.MediaKey
import org.lwjgl.glfw.GLFW.*

/**
 * What a key or the mouse wheel asks of the iPhone, sent to the phone through PhoneControl, or,
 * for [Screenshot], of this PC.
 */
sealed interface Control {
    /** PhoneControl.knob: [momentary] releases at once, as a wheel step does. */
    data class Knob(val input: KnobInput, val momentary: Boolean) : Control
    data class Media(val key: MediaKey) : Control
    data class Telephony(val key: CallKey) : Control
    data object Siri : Control
    /** Saves what is on the CarPlay screen; nothing goes to the iPhone. */
    data object Screenshot : Control
    data object Fullscreen : Control
    /** Ends the session as closing the window does and starts the connection over. */
    data object Reconnect : Control
    /** Zooms the altscreen's map [steps] times, in when positive (PhoneControl.mapZoom). */
    data class MapZoom(val steps: Int) : Control
    /** Asks the iPhone for the altscreen's next content (the cluster URL). */
    data object NextAltscreenContent : Control
    /** Asks the iPhone for the altscreen's next card layout, through the cluster URL. */
    data object NextAltscreenCards : Control
    /** Asks the iPhone for [content] on the altscreen, as the controls window's modes do. */
    data class ShowAltscreenContent(val content: AltscreenContent) : Control
    /** Turns the app's volume [steps] steps up, or down when negative; nothing goes to the iPhone. */
    data class VolumeStep(val steps: Int) : Control
    /** Mutes or unmutes the app; nothing goes to the iPhone. */
    data object MuteToggle : Control
    /** Turns drive mode on or off: the arrows drive the spoofed GPS car; nothing goes to the iPhone. */
    data object DriveMode : Control
    /** Switches the GPS car's gauges between large and small, saved as gps.gauges; nothing goes to the iPhone. */
    data object GaugeSize : Control
}

/**
 * The keyboard and mouse wheel as a car's knob, media keys and phone buttons, bound as [config] says
 * (controls.properties). Knob tilts (its X/Y axes nudge focus in a direction) and the select, back and home
 * buttons hold until the key is released, so CarPlay can tell a long press; knob turns step at once, a held
 * key stepping on every [ControlsConfig.repeatEvery]-th repeat as upstream's CarPlayRemoteKeys does.
 * A key is released by its code alone, so letting go of a modifier first cannot leave a button held, nor can a
 * binding changed while the key is down. [source] gives the bindings in force, which the Keyboard page's saves replace.
 * Called on the GL thread only.
 */
class KeyControls(private val source: () -> ControlsConfig) {
    constructor(config: ControlsConfig = ControlsConfig.DEFAULT) : this({ config })

    private val config get() = source()

    /** The action each held key started, and how many repeats it has had. */
    private val held = HashMap<Int, ControlAction>()
    private val repeats = HashMap<Int, Int>()
    private var scrolled = 0.0
    private var zoomScrolled = 0.0

    /** [action] is GLFW_PRESS, GLFW_REPEAT or GLFW_RELEASE; [mods] GLFW's modifier bits. */
    fun key(key: Int, action: Int, mods: Int): Control? {
        val bound = when (action) {
            GLFW_PRESS -> config.bindings[KeyBinding(key, mods and ControlsConfig.MOD_MASK)]?.also {
                held[key] = it
                repeats[key] = 0
            }
            else -> held[key]
        } ?: return null
        if (action == GLFW_RELEASE) held.remove(key)
        return when (bound) {
            ControlAction.KNOB_NEXT -> turn(key, action, 1)
            ControlAction.KNOB_PREVIOUS -> turn(key, action, -1)
            ControlAction.ALTSCREEN_ZOOM_IN -> if (steps(key, action, config.zoomRepeatEvery)) Control.MapZoom(1) else null
            ControlAction.ALTSCREEN_ZOOM_OUT -> if (steps(key, action, config.zoomRepeatEvery)) Control.MapZoom(-1) else null
            ControlAction.VOLUME_UP -> if (steps(key, action)) Control.VolumeStep(1) else null
            ControlAction.VOLUME_DOWN -> if (steps(key, action)) Control.VolumeStep(-1) else null
            else -> knobHold(bound)?.let { state ->
                when (action) {
                    GLFW_PRESS -> Control.Knob(state, false)
                    GLFW_RELEASE -> Control.Knob(KnobInput(), false)
                    else -> null
                }
            } ?: if (action == GLFW_PRESS) pressControl(bound) else null
        }
    }

    /** [dy] as GLFW reports it: 1.0 per mouse notch up, fractions from a touchpad. Down moves focus forward. */
    fun scroll(dy: Double): Control? {
        if (config.wheel == WheelAction.NONE) return null
        scrolled += if (config.wheelInvert) dy else -dy
        val steps = (scrolled / config.notchesPerStep).toInt()
        if (steps == 0) return null
        scrolled -= steps * config.notchesPerStep
        return Control.Knob(KnobInput(wheel = steps), true)
    }

    /** [dy] over the altscreen window: a zoom step of its map per notch, up zooming in, as a car's zoom control. */
    fun altscreenScroll(dy: Double): Control? {
        zoomScrolled += dy
        val steps = zoomScrolled.toInt()
        if (steps == 0) return null
        zoomScrolled -= steps
        return Control.MapZoom(steps)
    }

    private fun turn(key: Int, action: Int, step: Int): Control? =
        if (steps(key, action)) Control.Knob(KnobInput(wheel = step), true) else null

    /** A step on the press and on every [every]-th repeat of a held key (none when 0); none on release. */
    private fun steps(key: Int, action: Int, every: Int = config.repeatEvery): Boolean =
        when (action) {
            GLFW_PRESS -> true
            GLFW_REPEAT -> {
                val count = (repeats[key] ?: 0) + 1
                repeats[key] = count
                every > 0 && count % every == 0
            }
            else -> {
                repeats.remove(key)
                false
            }
        }
}

/** Full deflection of the knob descriptor's X/Y axes (logical -127..127). */
private const val KNOB_NUDGE = 127

/** The knob state [action] holds while its key or button is down; null for actions that are no knob hold. */
internal fun knobHold(action: ControlAction): KnobInput? = when (action) {
    ControlAction.KNOB_LEFT -> KnobInput(x = -KNOB_NUDGE)
    ControlAction.KNOB_RIGHT -> KnobInput(x = KNOB_NUDGE)
    ControlAction.KNOB_UP -> KnobInput(y = -KNOB_NUDGE)
    ControlAction.KNOB_DOWN -> KnobInput(y = KNOB_NUDGE)
    ControlAction.SELECT -> KnobInput(select = true)
    ControlAction.BACK -> KnobInput(back = true)
    ControlAction.HOME -> KnobInput(home = true)
    else -> null
}

/** What [action] asks for at once, on its press alone; null for knob holds and turns. */
internal fun pressControl(action: ControlAction): Control? = when (action) {
    ControlAction.FULLSCREEN -> Control.Fullscreen
    ControlAction.MEDIA_PREVIOUS -> Control.Media(MediaKey.PREVIOUS)
    ControlAction.MEDIA_PLAY_PAUSE -> Control.Media(MediaKey.PLAY_PAUSE)
    ControlAction.MEDIA_NEXT -> Control.Media(MediaKey.NEXT)
    ControlAction.CALL_ANSWER -> Control.Telephony(CallKey.ANSWER)
    ControlAction.CALL_END -> Control.Telephony(CallKey.HANG_UP)
    ControlAction.SIRI -> Control.Siri
    ControlAction.SCREENSHOT -> Control.Screenshot
    ControlAction.RECONNECT -> Control.Reconnect
    ControlAction.ALTSCREEN_CONTENT -> Control.NextAltscreenContent
    ControlAction.ALTSCREEN_CARDS -> Control.NextAltscreenCards
    ControlAction.VOLUME_MUTE -> Control.MuteToggle
    ControlAction.DRIVE -> Control.DriveMode
    ControlAction.GAUGES -> Control.GaugeSize
    else -> null
}
