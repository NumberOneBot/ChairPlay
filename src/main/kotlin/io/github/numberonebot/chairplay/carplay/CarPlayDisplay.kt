package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import io.github.numberonebot.chairplay.phone.AltscreenCards
import io.github.numberonebot.chairplay.phone.AltscreenContent
import io.github.numberonebot.chairplay.phone.ClusterSpec
import io.github.numberonebot.chairplay.phone.Dock
import io.github.numberonebot.chairplay.phone.Insets
import io.github.numberonebot.chairplay.phone.ScreenArea
import io.github.numberonebot.chairplay.phone.ScreenSpec
import io.github.numberonebot.chairplay.phone.TouchMode

// /info's display features and primary input device (CarPlay's knob, low- and high-fidelity touch).
private const val FEATURE_KNOB = 0x02
private const val FEATURE_TAP_TOUCH = 0x04
private const val FEATURE_SWIPE_TOUCH = 0x08
private const val INPUT_TOUCH = 1
private const val INPUT_KNOB = 3

// The Audi Virtual Cockpit's 12.3-inch Kombi panel; DiPlay's cluster display reports the same 292 mm.
private const val ALTSCREEN_WIDTH_MM = 292

/** The insets as /info takes them; none for all zero. */
private fun Insets.toAirPlay(): AirPlayInsets? = if (isNone) null else AirPlayInsets(top, bottom, left, right)

/**
 * The main screen as /info declares it: [ScreenSpec.touch] as its features and primary input, the knob always
 * declared; the dock edge as one view area over the whole screen, auto declaring none; insets all zero as no safe area.
 * Square corners fill the screen (mainDisplayCornerMasks) and, as upstream, leave out a safe area that is not set.
 */
fun ScreenSpec.toAirPlay(): AirPlayDisplayConfig {
    val (features, input) = when (touch) {
        TouchMode.SWIPE -> FEATURE_KNOB or FEATURE_SWIPE_TOUCH to INPUT_TOUCH
        TouchMode.TAP -> FEATURE_KNOB or FEATURE_TAP_TOUCH to INPUT_TOUCH
        TouchMode.NONE -> FEATURE_KNOB to INPUT_KNOB
    }
    val edge = when (dock) {
        Dock.AUTO -> null
        Dock.DRIVER -> AirPlayInfoPlist.DOCK_EDGE_DRIVER_SIDE
        Dock.BOTTOM -> AirPlayInfoPlist.DOCK_EDGE_BOTTOM
    }
    val safeArea = safe.toAirPlay()
    return AirPlayDisplayConfig(
        widthPixels = width,
        heightPixels = height,
        widthPhysicalMm = physicalMm.first,
        heightPhysicalMm = physicalMm.second,
        fps = fps,
        features = features,
        primaryInputDevice = input,
        viewAreas = edge?.let { listOf(AirPlayViewArea(width, height, dockEdge = it)) },
        safeArea = safeArea,
        safeAreaDrawOutside = drawOutsideSafe,
        mainDisplayCornerMasks = if (roundedCorners) null else true,
        mainDisplayDisableSafeArea = !roundedCorners && safeArea == null,
    )
}

/** The area as /info declares it, its insets given from the whole frame. */
fun ScreenArea.toAirPlay(): AirPlayViewArea = AirPlayViewArea(width, height, originX = x, originY = y, safeArea = safe?.let {
    AirPlayInsets(it.top, it.bottom, it.left, it.right)
})

/**
 * The AltScreen as /info declares it: DiPlay's cluster display, with even margins as for its virtual cluster rather
 * than its car cluster's crop, and the view areas. DiPlay rounds its cluster stream to multiples of 8; the AltScreen
 * goes out at the size set, 1440x540 by default for Audi's panel (the MHI2Q CarPlay project declares it as 1440x542).
 * Nothing covers a window on a PC, so the safe area is the whole panel unless insets leave room for an overlay.
 */
fun ClusterSpec.toCarPlay(): AirPlayDisplayConfig = CarPlayClusterDisplay.config(
    widthPixels = width,
    heightPixels = height,
    scalePercent = 100,
    content = content.carPlay,
).copy(
    widthPixels = width,
    heightPixels = height,
    heightPhysicalMm = Math.round(ALTSCREEN_WIDTH_MM * height.toDouble() / width).toInt(),
    safeArea = safe.toAirPlay(),
    initialUrl = altscreenUrl(content, cards),
    viewAreas = areas.map { it.toAirPlay() },
)

/** The cluster content the iPhone lists in its altScreenURLs that the view shows; the HUD's data comes with the turn card. */
private val AltscreenContent.carPlay: CarPlayClusterDisplay.Content
    get() = when (this) {
        AltscreenContent.HUD -> CarPlayClusterDisplay.Content.TURN_CARD
        AltscreenContent.MAP -> CarPlayClusterDisplay.Content.MAP
        AltscreenContent.MAP_CARD -> CarPlayClusterDisplay.Content.INSTRUMENTS
        AltscreenContent.CARD -> CarPlayClusterDisplay.Content.TURN_CARD
    }

/** The cluster URL's query that places the ETA and the turn card; auto has none. */
private val AltscreenCards.query: String
    get() = when (this) {
        AltscreenCards.AUTO -> ""
        AltscreenCards.LEFT -> "showETA=yes&maneuverLayout=leftAligned"
        AltscreenCards.RIGHT -> "showETA=yes&maneuverLayout=rightAligned"
        AltscreenCards.TOP -> "showETA=yes&maneuverLayout=topAligned"
    }

/** The cluster URL the iPhone is asked for: [content] with the [cards] layout. */
fun altscreenUrl(content: AltscreenContent, cards: AltscreenCards): String =
    if (cards.query.isEmpty()) content.carPlay.url else "${content.carPlay.url}?${cards.query}"
