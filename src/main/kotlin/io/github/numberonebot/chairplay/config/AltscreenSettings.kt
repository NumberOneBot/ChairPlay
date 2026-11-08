package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.phone.ClusterSpec
import io.github.numberonebot.chairplay.phone.Insets
import io.github.numberonebot.chairplay.phone.ScreenArea
import java.util.Properties

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

/**
 * The AltScreen window's title, with its stream size; without a route every mode looks alike, so the title says
 * which one is on, and the cards' place only where the view places them. AltScreen is Apple's name for it (the
 * altScreen feature and altScreenURLs).
 */
fun altscreenTitle(stream: WindowSize, content: AltscreenContent, cards: AltscreenCards): String =
    "AltScreen · ${stream.width}×${stream.height} · ${content.key}" + if (content.placesCards) " · cards ${cards.key}" else ""

/** The second CarPlay screen, a car's instrument cluster: the stream's [size] and what it starts with. */
data class Altscreen(
    val size: WindowSize,
    val content: AltscreenContent,
    val cards: AltscreenCards = AltscreenCards.TOP,
    /** altscreen.hud.stream: whether the iPhone keeps drawing a view under the HUD ([drawnView]). */
    val streamUnderHud: Boolean = false,
)

/**
 * Whether the HUD shows the [session]'s route and song: as the view picked ([content]), or for a demo; on another
 * view it is shown only while that view's picture is on its way, and shows them not, nor its waiting line.
 */
fun hudShowsSession(content: AltscreenContent?, session: Boolean, demo: Boolean): Boolean =
    demo || (session && content == AltscreenContent.HUD)

/**
 * The iPhone's view drawn while the AltScreen is on [content]: that view, the HUD's being the map view picked last
 * ([mapView]); while the window shows the HUD ([hud], also over a view for a demo), only if [underHud] keeps it
 * running, so the view comes back at once, else none.
 */
fun drawnView(content: AltscreenContent, mapView: AltscreenContent, hud: Boolean, underHud: Boolean): AltscreenContent? =
    (if (content == AltscreenContent.HUD) mapView else content).takeIf { !hud || underHud }

/**
 * The AltScreen as the iPhone is told of it: [alt]'s size and view, and the view [areas]. Nothing covers a window on a
 * PC, so the safe area (where Apple Maps keeps its attribution and car marker) is the whole panel unless altscreen.safe.*
 * in [settings] leave room for an overlay.
 */
internal fun altscreenCluster(alt: Altscreen, settings: Properties, areas: List<ScreenArea>): ClusterSpec {
    val (width, height) = alt.size.width to alt.size.height
    val safe = Insets(
        top = settings.nonNegativeSetting("altscreen.safe.top"),
        bottom = settings.nonNegativeSetting("altscreen.safe.bottom"),
        left = settings.nonNegativeSetting("altscreen.safe.left"),
        right = settings.nonNegativeSetting("altscreen.safe.right"),
    )
    require(safe.top + safe.bottom < height && safe.left + safe.right < width) {
        "altscreen.safe.* leave nothing of the ${width}x$height altscreen"
    }
    return ClusterSpec(width, height, safe, areas, alt.content, alt.cards)
}

