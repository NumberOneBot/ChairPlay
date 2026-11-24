package io.github.numberonebot.chairplay.bridge

/**
 * Whose screen a game's car display shows, decided as a car's head unit decides it (owner, 2026-11-24): CarPlay's
 * until the car button (or Back on CarPlay's home screen, [carButton]) gives it to the car; CarPlay's again for as long
 * as Siri or a call lasts ([voice]), as CarPlay borrows a car's screen; and CarPlay's for good from the game's CarPlay
 * button ([carPlay]). Each change goes to [publish], [CARPLAY] or [CAR]. Nothing goes to the iPhone, which keeps
 * sending its picture throughout.
 */
class BridgeScreen(private val publish: (Int) -> Unit) {
    private var car = false
    private var voice = false
    private var shown = CARPLAY

    @Synchronized fun carButton() = update { car = true }

    @Synchronized fun carPlay() = update { car = false }

    @Synchronized fun voice(active: Boolean) = update { voice = active }

    private fun update(change: () -> Unit) {
        change()
        val now = if (car && !voice) CAR else CARPLAY
        if (now == shown) return
        shown = now
        publish(now)
    }

    companion object {
        const val CARPLAY = 0
        const val CAR = 1
    }
}
