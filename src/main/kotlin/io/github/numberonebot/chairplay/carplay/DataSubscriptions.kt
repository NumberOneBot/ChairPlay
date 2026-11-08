package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.catalog.Iap2Endpoints
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * More of three subscriptions upstream's control client already sends (Iap2ControlMessages.subscriptions), sent
 * again on the same link right after them. A second Start replaces the first, as upstream's album recovery
 * relies on for now playing. Only the requested parameters grow: every message here is already in the
 * identification's MessagesSentByAccessory, which upstream builds with no way to add to it, so nothing new is
 * declared and the identification the iPhone accepts stays as it is. The parameter ids are Apple's order, the
 * same as the fields of [CarPlayDataMessages]; upstream's own requests are all included.
 *
 * Not asked for: StartRouteGuidanceUpdates already requests all three of its parameters; StartPowerUpdates' other
 * parameters are about current drawn over a cable; persistent ids, likes and bans of the media item; the call's
 * address-book id; and the call-control flags of CommunicationsUpdate, for calls this accessory cannot place.
 * Lane guidance (0x5204), lists (0x4171) and every other message need the identification to declare them.
 */
internal object DataSubscriptions {
    fun frames(): List<Iap2Frame> = listOf(
        Iap2Messages.build(Iap2Endpoints.START_NOW_PLAYING_UPDATES) {
            group(0) {
                // Title, media type, duration, album, track and disc number and count, artist, album artist,
                // genre, composer, artwork transfer, chapter count.
                listOf(1, 2, 4, 6, 7, 8, 9, 10, 12, 14, 16, 18, 26, 27).forEach(::void)
            }
            group(1) {
                // Status, elapsed time, queue index and count, shuffle, repeat, app name, speed, app bundle id.
                listOf(0, 1, 2, 3, 5, 6, 7, 12, 16).forEach(::void)
            }
        },
        // Signal, registration, airplane mode, carrier name, carrier id, telephony, mute, call count, voicemail count.
        Iap2Messages.build(Iap2Endpoints.START_COMMUNICATIONS_UPDATES) {
            listOf(0, 1, 2, 3, 4, 5, 8, 9, 10).forEach(::void)
        },
        // Remote id, name, status, direction, UUID, label, service, conference, conference group, disconnect reason.
        Iap2Messages.build(Iap2Endpoints.START_CALL_STATE_UPDATES) {
            listOf(0, 1, 2, 3, 4, 7, 8, 9, 10, 11).forEach(::void)
        },
    )

    /** Sends [frames] with [send]; a failure is logged and left to the control loop, which notices a dead link. */
    fun send(send: (Iap2Frame) -> Unit, log: (String) -> Unit) {
        try {
            frames().forEach(send)
            log("iap2 extra data subscriptions sent")
        } catch (error: Exception) {
            log("iap2 extra data subscriptions not sent: ${error.javaClass.simpleName}: ${error.message}")
        }
    }
}
