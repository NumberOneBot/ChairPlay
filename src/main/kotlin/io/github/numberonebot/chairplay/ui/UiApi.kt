package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.drive.WindowsLocation
import java.nio.file.Path

// What the screens and the app ask of each other: the app's actions and windows as the screens see them, and the
// HTTP server's view of the screens' side (UiApi), with the small types they pass.

/** What the screens ask the app to do with the iPhone. */
interface UiActions {
    /** Starts connecting, as the app does on its own with ui.autoconnect. */
    fun connect()

    /** Ends the session or bring-up and starts a new one, as Ctrl+R does. */
    fun reconnect()

    /** Ends the session or bring-up; none starts until Connect. */
    fun disconnect()
}

/** A window of the app the settings screen shows and hides: the AltScreen's, the controls'. */
interface UiWindow {
    val visible: Boolean

    /** False while the window waits, hidden, for a setting to turn it on: the AltScreen's while altscreen.enabled is off. */
    val available: Boolean get() = true

    /** Safe from any thread: the window shows or hides on its own thread soon after. */
    fun setShown(shown: Boolean)
}

/** A window that shows a picture, for its page's Actual size: the main window, the AltScreen's. */
interface UiMainWindow {
    /** False while the window waits, hidden, for a setting to turn it on: it has no Actual size meanwhile. */
    val available: Boolean get() = true

    /** The client area, in screen pixels. */
    val width: Int
    val height: Int

    /** The smallest client area its edges and a resize can reach, in screen pixels: Windows' display scale grows it. */
    val minWidth: Int get() = 0
    val minHeight: Int get() = 0

    /** The picture it shows now, one screen pixel per picture pixel at Actual size; null for the display size the app started with. */
    val picture: WindowSize? get() = null

    /** Safe from any thread: resizes the client area to [width] x [height] screen pixels soon after. */
    fun resizeTo(width: Int, height: Int)
}

/** The picture the window shows, or [display] before its stream says otherwise. */
internal fun UiMainWindow.pictureOr(display: WindowSize) = picture ?: display

/** The window's client area, its picture (else [display]), and its smallest size, for its page's Actual size. */
internal fun UiMainWindow.sizes(display: WindowSize): Map<String, Int> {
    val picture = pictureOr(display)
    return mapOf(
        "width" to width, "height" to height, "pictureWidth" to picture.width,
        "pictureHeight" to picture.height, "minWidth" to minWidth, "minHeight" to minHeight,
    )
}

/** The HUD's demos on the AltScreen, for the Experiments page: "maneuvers" or "lanes", one at a time. Safe from any thread. */
interface UiDemo {
    /** The demo playing, or null. */
    val playing: String?

    /** Whether one can play: the AltScreen is on. */
    val available: Boolean

    /** Plays [kind], in place of one playing, or stops it (null); false when the AltScreen is off. */
    fun play(kind: String?): Boolean
}

/**
 * The AltScreen's window drawing a safe area for a moment; insets in the pixels of a [width] x [height] frame, the
 * size as the settings now give it, which the window scales to its picture. Safe from any thread.
 */
fun interface UiSafeAreaFrame {
    fun show(top: Int, bottom: Int, left: Int, right: Int, width: Int, height: Int)
}

/** The controls window's page: the car's buttons, and what it shows beside them. Safe from any thread. */
interface UiControls {
    /**
     * Presses ([down]) or releases the button [action] names: a key of controls.properties without its "key."
     * ("knob.select", "volume.up"). False when there is no such button.
     */
    fun control(action: String, down: Boolean): Boolean

    /**
     * A key pressed on a page, which has the keyboard while it shows, handed on as if pressed on the window: its name
     * as controls.properties writes it ("Shift+F11"), [phase] "press", "repeat" or "release". [everything] over the
     * controls window's page; over the settings only the keys that neither move about the page nor type work. False
     * when the key does nothing there.
     */
    fun key(name: String, phase: String, everything: Boolean): Boolean = false
    fun keyboardFocus(client: String, focused: Boolean) {}

    /** Shows [content] ("hud", "map", "map-card", "card") on the AltScreen; false when it cannot be shown. */
    fun altscreenContent(content: String): Boolean

    /** Sets the app's volume to [level] percent (the Audio page's slider), unmuting it; false when there is none. */
    fun volume(level: Int): Boolean = false

    /** What the page shows beside the buttons: their keys, the volume, the AltScreen's content, whether Alt is held. */
    fun state(): Map<String, Any?>
}

/** Whether Windows Firewall lets the iPhone in, as read at start. */
enum class FirewallStatus {
    PRESENT, MISSING, BLOCKED, UNKNOWN,

    /** Off for the networks the PC is on: nothing to add, nothing to warn of. */
    OFF,
}

/** What the start screen's setup steps refer to. */
data class SetupInfo(
    val mfiDirectory: Path,
    val firewall: FirewallStatus,
    /** The netsh commands that add the firewall rules, from an administrator prompt. */
    val firewallCommands: String,
    val settingsFile: Path,
    val displayWidth: Int,
    val displayHeight: Int,
    /** ui.accent, #rrggbb, which the pages colour themselves from. */
    val accent: String = DesktopConfig.DEFAULT_ACCENT,
)

/** The sound devices by the names audio.output and audio.input match against. */
data class AudioDeviceNames(val outputs: List<String>, val inputs: List<String>)

/** A save's outcome: the problems by key, or, when there are none, when each saved setting applies. */
class SaveResult(val errors: Map<String, String>, val applies: Map<String, String>)

/** What the HTTP server asks of the app; the JSON messages are maps and lists for [Json]. */
interface UiApi {
    /** Moves on with every change of what [state] returns. */
    val version: Long
    fun state(): Map<String, Any?>
    fun settings(): Map<String, Any?>
    fun saveSettings(changes: Map<String, String>): SaveResult

    /**
     * The saved Wi-Fi password, for the eye beside it; null when none is saved. It is in settings.properties as
     * written, so the page may show it when asked; the state, the settings, and the log never carry it.
     */
    fun savedPassphrase(): String? = null

    /** Null when connecting started, else why it cannot. */
    fun connect(): String?
    fun reconnect()
    fun disconnect()

    /** Shows the screen named "start", "settings" or "carplay"; false when it cannot be shown. */
    fun show(screen: String): Boolean

    /** Shows or hides the window named "altscreen" or "controls"; false when there is no such window. */
    fun window(name: String, visible: Boolean): Boolean

    /** A button of the controls window pressed or released ([UiControls.control]); false when there is none. */
    fun control(action: String, down: Boolean): Boolean

    /** A key pressed on a page, handed on to the app ([UiControls.key]); false when it does nothing there. */
    fun key(name: String, phase: String, everything: Boolean): Boolean = false

    /** A focused browser page renews a short lease that pauses native background shortcuts. */
    fun keyboardFocus(client: String, focused: Boolean) {}

    /** The controls window's AltScreen modes. */
    fun altscreenContent(content: String): Boolean

    /** Opens the Windows page or folder [place] names ([UiController.open]); false when there is none or it failed. */
    fun open(place: String): Boolean

    /** The logs and screenshots folders' sizes in bytes, by name ([UiPlaces.folderSizes]). */
    fun folderSizes(): Map<String, Long> = emptyMap()

    /**
     * Resizes the window named "main" or "altscreen" to one screen pixel per picture pixel; false when there is no such
     * window.
     */
    fun actualSize(window: String): Boolean

    /**
     * Shows the AltScreen's saved safe area on its window for a moment, or [view]'s ("map", "map-card", "card"), its
     * margins inside its part; false without an AltScreen or for another view.
     */
    fun showAltscreenFrame(view: String? = null): Boolean

    /** Adds the firewall rules with the administrator's consent; false when it was not given or they failed. */
    fun addFirewallRule(): Boolean

    /** Forget this iPhone: ChairPlay's pairing records go and Get started opens; false when there is nothing to forget. */
    fun forget(): Boolean

    /** Plays a HUD demo on the AltScreen ("maneuvers" or "lanes"), or stops it (null); false when none can play. */
    fun demo(kind: String?): Boolean = false

    /** Sets the app's volume to [level] percent, from the Audio page's slider; false without a volume to set. */
    fun volume(level: Int): Boolean = false

    /** Where Windows locates this PC, as latitude and longitude, or the error "denied" or "unknown". */
    fun windowsLocation(): Map<String, Any?> = mapOf("error" to "unknown")

    /**
     * GPS experiments: presses the GPS car's pedals and turns its wheel as a script asks, on top of the keys and a
     * gamepad, until it asks again (all zero lets go); false without a car.
     */
    fun gpsDrive(throttle: Double, brake: Double, steer: Double): Boolean = false

    /** Saves the CarPlay screens as F12 does, to look at where the iPhone puts the car; false without them. */
    fun screenshot(): Boolean = false

    /** The GPS page's Drive mode switch, as F10 turns it; false while the GPS is off, which keeps it off. */
    fun driveMode(on: Boolean): Boolean = false

    /**
     * The Keyboard page's bindings: each action's keys, defaults and fixed keys, and the wheel and repeat settings as
     * controls.properties names them; null when the app has none to change.
     */
    fun controls(): Map<String, Any?>?

    /** Saves controls.properties [changes] and puts them in force at once, or says why the start would refuse them. */
    fun saveControls(changes: Map<String, String>): SaveResult

    /** Waits up to [timeoutMillis] for [version] to differ from [seen], and returns it. */
    fun awaitChange(seen: Long, timeoutMillis: Long): Long
}

/** Where Windows located this PC, as the GPS page takes it: latitude and longitude, or the error "denied" or "unknown". */
internal fun WindowsLocation.Result.toPage(): Map<String, Any?> = when (this) {
    is WindowsLocation.Found -> mapOf("latitude" to place.latitude, "longitude" to place.longitude)
    WindowsLocation.Denied -> mapOf("error" to "denied")
    WindowsLocation.Unknown -> mapOf("error" to "unknown")
}
