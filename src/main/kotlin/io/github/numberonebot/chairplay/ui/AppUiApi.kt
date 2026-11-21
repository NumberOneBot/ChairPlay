package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.config.SupportStore
import io.github.numberonebot.chairplay.gps.GpsCommands

/**
 * The controller's API, with the GPS car's calls, which only the app's windows and car can answer, and the setup
 * [checks] as a page asks for them. A named class, not an anonymous object in [ChairPlayUi]: a changed [UiApi] then
 * recompiles it, and its test lists every call it forwards.
 */
internal class AppUiApi(
    private val controller: UiController,
    private val updateCheck: () -> Boolean = { false },
    private val checks: SetupChecks? = null,
    private val gps: () -> GpsCommands? = { null },
    private val track: (String) -> Boolean = { false },
    private val support: SupportStore? = null,
    private val driving: () -> UiDriving?,
) : UiApi by controller {
    // The card's state lives beside the controller's, whose file has no room for it.
    override fun state(): Map<String, Any?> = controller.state() + ("supportDue" to (support?.due() == true))

    override fun support(action: String): Boolean {
        val store = support ?: return false
        val done = when (action) {
            "done" -> runCatching { store.supported() }
            "later" -> runCatching { store.later() }
            else -> return false
        }.isSuccess
        controller.changed()
        return done
    }

    override fun gpsCommands(): GpsCommands? = gps()

    override fun checkUpdates(): Boolean = updateCheck()

    override fun checkSetup() {
        if (checks?.check() == true) controller.setupChanged()
    }

    override fun gpsDrive(throttle: Double, brake: Double, steer: Double) =
        driving()?.gpsDrive(throttle, brake, steer) ?: false

    override fun screenshot() = driving()?.screenshot() ?: false

    override fun driveMode(on: Boolean) = driving()?.driveMode(on) ?: false

    override fun track(action: String) = track.invoke(action)
}
