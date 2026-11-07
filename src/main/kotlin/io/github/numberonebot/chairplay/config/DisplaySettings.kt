package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import java.util.Properties

// /info's display features and primary input device (CarPlay's knob, low- and high-fidelity touch).
private const val FEATURE_KNOB = 0x02
private const val FEATURE_TAP_TOUCH = 0x04
private const val FEATURE_SWIPE_TOUCH = 0x08
private const val INPUT_TOUCH = 1
private const val INPUT_KNOB = 3
private val TOUCH_SWIPE = FEATURE_KNOB or FEATURE_SWIPE_TOUCH to INPUT_TOUCH
private val TOUCH_NONE = FEATURE_KNOB to INPUT_KNOB

internal const val MM_PER_INCH = 25.4

/** Upstream AirPlayDisplaySettings.DEFAULT_WIDTH_PHYSICAL_MM. */
private const val DEFAULT_WIDTH_MM = 200

/** The CarPlay screen as settings.properties ([settings]) sets it (display.*), and the window it shows in (window.*). */
internal class DisplaySettings(settings: Properties) {
    // 1280x720 by default: iOS draws at @3x from about that size, and a window of it fits most screens.
    val width = settings.evenSetting("display.width", 1280)
    val height = settings.evenSetting("display.height", 720)
    private val fps = (settings.positiveSetting("display.fps") ?: 60).also {
        require(it in 30..60) { "display.fps must be between 30 and 60, not $it" }
    }

    /**
     * The physical size in /info, from which iOS picks its scale: millimetres as given, else from
     * display.ppi, else upstream's 200 mm width. A missing height follows the aspect ratio, as /info would.
     */
    private val physicalMm: Pair<Int, Int> = run {
        val ppi = settings.positiveSetting("display.ppi")
        val setWidthMm = settings.positiveSetting("display.physical.width.mm")
        fun fromPpi(pixels: Int) = ppi?.let { Math.round(pixels * MM_PER_INCH / it).toInt() }
        val widthMm = setWidthMm ?: fromPpi(width) ?: DEFAULT_WIDTH_MM
        val heightMm = settings.positiveSetting("display.physical.height.mm")
            ?: (if (setWidthMm == null) fromPpi(height) else null)
            ?: Math.round(widthMm * height.toDouble() / width).toInt()
        widthMm to heightMm
    }

    /** The CarPlay window on the PC; the picture is scaled into it, so it needs not match the display. */
    val window = WindowSize(
        settings.positiveSetting("window.width") ?: width,
        settings.positiveSetting("window.height") ?: height,
    )

    /** Right-hand drive puts CarPlay's dock and controls on the right. */
    val rightHandDrive = settings.booleanSetting("display.right.hand.drive", false)

    /** One view area over the whole screen carrying the dock edge; auto declares none, as before. */
    private val viewAreas: List<AirPlayViewArea>? = settings.choiceSetting(
        "display.dock", null,
        mapOf("auto" to null, "driver" to AirPlayInfoPlist.DOCK_EDGE_DRIVER_SIDE, "bottom" to AirPlayInfoPlist.DOCK_EDGE_BOTTOM),
    )?.let { edge -> listOf(AirPlayViewArea(width, height, dockEdge = edge)) }

    /** Pixels at each edge CarPlay keeps its controls out of; all zero declares none, as before. */
    private val safeArea: AirPlayInsets? = run {
        val insets = AirPlayInsets(
            top = settings.nonNegativeSetting("display.safe.top"),
            bottom = settings.nonNegativeSetting("display.safe.bottom"),
            left = settings.nonNegativeSetting("display.safe.left"),
            right = settings.nonNegativeSetting("display.safe.right"),
        )
        require(insets.left + insets.right < width) {
            "display.safe.left and display.safe.right leave no width on a $width-pixel screen"
        }
        require(insets.top + insets.bottom < height) {
            "display.safe.top and display.safe.bottom leave no height on a $height-pixel screen"
        }
        insets.takeIf { it != AirPlayInsets() }
    }

    /** The screen less its insets, where the picture CarPlay keeps its controls inside lies; the whole screen without insets. */
    val safeRect: AreaRect = AreaRect(
        x = safeArea?.left ?: 0,
        y = safeArea?.top ?: 0,
        width = width - (safeArea?.left ?: 0) - (safeArea?.right ?: 0),
        height = height - (safeArea?.top ?: 0) - (safeArea?.bottom ?: 0),
    )

    /** Whether the CarPlay picture is published for games to show on a car's screen (display.share). */
    val share = settings.booleanSetting("display.share", false)

    /** /info's default is true too, so an older file that lacks it declares the same. */
    private val safeAreaDrawOutside: Boolean = settings.booleanSetting("display.safe.draw.outside", true)

    /**
     * square (the default, as a window is rectangular): the picture fills the screen with right-angled corners
     * (mainDisplayCornerMasks); rounded leaves CarPlay's rounded corners.
     */
    private val squareCorners: Boolean? = settings.choiceSetting("display.corners", true, mapOf("rounded" to null, "square" to true))

    /**
     * display.touch, what the screen's touch can do, as /info's features and primaryInputDevice say it. With touch,
     * swipe or tap, CarPlay shows scroll buttons in its lists; none (the default, for a mouse) makes the knob (the keys
     * and the controls window, always declared) the primary input, and CarPlay leaves them out, while clicks and
     * drags still reach it as taps.
     */
    private val touch: Pair<Int, Int> = settings.choiceSetting(
        "display.touch", TOUCH_NONE,
        mapOf("swipe" to TOUCH_SWIPE, "tap" to (FEATURE_KNOB or FEATURE_TAP_TOUCH to INPUT_TOUCH), "none" to TOUCH_NONE),
    )

    /** What the iPhone is told about its screen, for the start-up log. */
    fun summary(): String {
        val (widthMm, heightMm) = physicalMm
        val ppi = Math.round(width * MM_PER_INCH / widthMm)
        return "display ${width}x$height px, ${widthMm}x$heightMm mm ($ppi ppi), $fps fps; " +
            "window ${window.width}x${window.height}"
    }

    /** The main screen as /info declares it. */
    fun airPlay() = AirPlayDisplayConfig(
        widthPixels = width,
        heightPixels = height,
        widthPhysicalMm = physicalMm.first,
        heightPhysicalMm = physicalMm.second,
        fps = fps,
        features = touch.first,
        primaryInputDevice = touch.second,
        viewAreas = viewAreas,
        safeArea = safeArea,
        safeAreaDrawOutside = safeAreaDrawOutside,
        mainDisplayCornerMasks = squareCorners,
        // Upstream leaves the safe area out with square corners, to avoid a conflict with them; a set one stays.
        mainDisplayDisableSafeArea = squareCorners == true && safeArea == null,
    )
}
