package io.github.numberonebot.chairplay.phone

/** What the altscreen shows: one of the cluster contents the iPhone lists in its altScreenURLs (carplay.altscreenUrl). */
enum class AltscreenContent(val key: String) {
    /** Our HUD: the iPhone sends the turn card, the window shows the HUD's own data. */
    HUD("hud"),
    MAP("map"),
    MAP_CARD("map-card"),
    CARD("card");

    /** Whether altscreen.cards places anything in this view: Map + card's turn card; the others have none to place. */
    val placesCards: Boolean get() = this == MAP_CARD

    /** F11's view after this one: the HUD goes to [mapView], the iPhone's view picked last; any other to the HUD. */
    fun toggled(mapView: AltscreenContent): AltscreenContent = if (this == HUD) mapView else HUD
}

/**
 * Where CarPlay lays out the navigation app's ETA and turn card on the altscreen, on Map + card: the other views
 * have none to place. DashBoard reads it from the cluster URL for every app (notes of the MHI2Q CarPlay project).
 * Without one, AUTO, it takes the driver's side the main screen was declared with (display.right.hand.drive): seen
 * with Google Maps, the left for a left-hand drive car, the right for a right-hand drive one. Neither Google Maps nor
 * Apple Maps takes the card away for showETA=no&maneuverLayout=no, so there is no off.
 */
enum class AltscreenCards(val key: String) {
    AUTO("auto"),
    LEFT("left"),
    RIGHT("right"),
    TOP("top");

    fun next(): AltscreenCards = entries[(ordinal + 1) % entries.size]
}
