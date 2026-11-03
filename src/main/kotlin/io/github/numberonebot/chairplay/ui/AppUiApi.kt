package io.github.numberonebot.chairplay.ui

/**
 * The controller's API, with the GPS car's calls, which only the app's windows and car can answer. A named class, not an
 * anonymous object in [ChairPlayUi]: a changed [UiApi] then recompiles it, and its test lists every call it forwards.
 */
internal class AppUiApi(
    private val controller: UiController,
    private val updateCheck: () -> Boolean = { false },
    private val driving: () -> UiDriving?,
) : UiApi by controller {
    override fun checkUpdates(): Boolean = updateCheck()

    override fun gpsDrive(throttle: Double, brake: Double, steer: Double) =
        driving()?.gpsDrive(throttle, brake, steer) ?: false

    override fun screenshot() = driving()?.screenshot() ?: false

    override fun driveMode(on: Boolean) = driving()?.driveMode(on) ?: false
}
