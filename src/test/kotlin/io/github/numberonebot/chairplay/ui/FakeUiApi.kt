package io.github.numberonebot.chairplay.ui

/** A UiApi for UiServerTest: it records what the server asks of it and answers as a running app would. */
internal class FakeUiApi : UiApi {
    private val lock = Object()
    private var phase = "idle"
    @Volatile override var version = 0L
    override fun savedPassphrase() = "secret123"
    var saved: Map<String, String>? = null
    var connectError: String? = null
    val calls = mutableListOf<String>()

    /** The app's page is in its window, and waits there, black; the server tells each page its own of these. */
    @Volatile var embedded = false
    @Volatile var waiting = false

    fun change(newPhase: String) = synchronized(lock) {
        phase = newPhase
        version++
        lock.notifyAll()
    }

    /** Moves the version on, as the app does when who holds the settings changes, so the streams send the state. */
    fun bump() = synchronized(lock) {
        version++
        lock.notifyAll()
    }

    override fun state(): Map<String, Any?> = synchronized(lock) {
        linkedMapOf("version" to version, "phase" to phase, "embedded" to embedded, "waiting" to waiting)
    }
    override fun settings(): Map<String, Any?> = mapOf("values" to emptyMap<String, String>())
    override fun saveSettings(changes: Map<String, String>): SaveResult {
        if (changes["window.width"] == "x") return SaveResult(mapOf("window.width" to "bad"), emptyMap())
        saved = changes
        return SaveResult(emptyMap(), mapOf("window.width" to "restart"))
    }
    override fun connect(): String? = connectError ?: null.also { calls += "connect" }
    override fun reconnect() {
        calls += "reconnect"
    }
    override fun disconnect() {
        calls += "disconnect"
    }
    override fun show(screen: String): Boolean {
        calls += "show $screen"
        return screen != "carplay"
    }
    override fun window(name: String, visible: Boolean): Boolean {
        calls += "window $name $visible"
        return name == "controls"
    }
    override fun control(action: String, down: Boolean): Boolean {
        calls += "control $action $down"
        return action != "eject"
    }
    override fun key(name: String, phase: String, everything: Boolean): Boolean {
        calls += "key $name $phase $everything"
        return name != "Up" || everything
    }
    override fun keyboardFocus(client: String, focused: Boolean) { calls += "keyboard focus $client $focused" }
    override fun altscreenContent(content: String): Boolean {
        calls += "altscreen $content"
        return content == "hud"
    }
    override fun open(place: String): Boolean {
        calls += "open $place"
        return place == "bluetooth"
    }
    override fun showAltscreenFrame(view: String?): Boolean {
        calls += if (view == null) "altscreen frame" else "altscreen frame $view"
        return true
    }
    override fun forget(): Boolean {
        calls += "forget"
        return true
    }

    override fun demo(kind: String?): Boolean {
        calls += "demo $kind"
        return kind != "tetris"
    }

    override fun gpsDrive(throttle: Double, brake: Double, steer: Double): Boolean {
        calls += "gpsDrive $throttle $brake $steer"
        return true
    }

    override fun screenshot(): Boolean {
        calls += "screenshot"
        return true
    }

    override fun driveMode(on: Boolean): Boolean {
        calls += "driveMode $on"
        return true
    }


    override fun volume(level: Int): Boolean {
        calls += "volume $level"
        return true
    }
    var consent = true
    override fun addFirewallRule(): Boolean {
        calls += "firewall rule"
        return consent
    }
    var hasControls = true
    var savedControls: Map<String, String>? = null
    override fun controls(): Map<String, Any?>? = if (hasControls) mapOf("actions" to emptyMap<String, Any>()) else null
    override fun saveControls(changes: Map<String, String>): SaveResult {
        if (changes["key.home"] == "Escape") return SaveResult(mapOf("key.home" to "Escape always goes back"), emptyMap())
        savedControls = changes
        return SaveResult(emptyMap(), emptyMap())
    }
    var mainWindow = true
    override fun actualSize(window: String): Boolean {
        calls += "actual size $window"
        return mainWindow
    }
    override fun awaitChange(seen: Long, timeoutMillis: Long): Long = synchronized(lock) {
        if (version == seen) lock.wait(timeoutMillis)
        version
    }
}
