package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import io.github.numberonebot.chairplay.phone.PhoneLink

/**
 * updateViewArea for the AltScreen: DiPlay's AirPlayInfoPlist.viewAreaCommand, which it sends for the main screen
 * alone, with the AltScreen's display. The iPhone acted on the main screen's only with the animation and the adjacent
 * areas, and it moves the AltScreen's too.
 */
fun altscreenViewAreaCommand(index: Int, count: Int): Map<String, Any?> = linkedMapOf(
    "type" to "updateViewArea",
    "params" to linkedMapOf(
        "uuid" to AirPlayInfoPlist.ALT_UUID,
        "viewAreaIndex" to index,
        "animationDurationMillis" to VIEW_AREA_ANIMATION_MILLIS,
        "adjacentViewAreas" to (0 until count).filter { it != index },
    ),
)

/** Sends [command] to [link]'s iPhone; false when it did not go out, or for a phone that is not CarPlay's. */
fun altscreenCommand(link: PhoneLink, command: Map<String, Any?>): Boolean = (link as? CarPlayLink)?.session?.sendCommand(command) == true

/**
 * Moves CarPlay on the AltScreen to a view area: [send] the session's updateViewArea, then [showView], which asks for
 * the AltScreen's view again with showUI once the move is over ([AREA_MOVE_SETTLE_MILLIS]). Seen on an iPhone 15 Pro Max (AirPlay 870.14): after updateViewArea alone the
 * view stays blurred, mid-way through its move, and once asked for again it is drawn in the new area, the rest of the
 * frame near black. [count] is how many areas the connection declared.
 */
class AltscreenAreaMove(
    private val count: () -> Int,
    private val send: (Map<String, Any?>) -> Boolean,
    private val showView: () -> Unit,
) {
    /** False when the iPhone was not sent the move. */
    operator fun invoke(index: Int): Boolean {
        if (!send(altscreenViewAreaCommand(index, count()))) return false
        showView()
        return true
    }
}

/**
 * How long after updateViewArea the AltScreen's view is asked for again. Asked for 2 ms after, it stayed blurred: the
 * move's new codec configuration comes about 300 ms after the command, for its 300 ms; asked for a second after, the
 * view was drawn in the new area.
 */
const val AREA_MOVE_SETTLE_MILLIS = 1_000L

private const val VIEW_AREA_ANIMATION_MILLIS = 300
