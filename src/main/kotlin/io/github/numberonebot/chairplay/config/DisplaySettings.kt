package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.phone.Dock
import io.github.numberonebot.chairplay.phone.Insets
import io.github.numberonebot.chairplay.phone.ScreenSpec
import io.github.numberonebot.chairplay.phone.TouchMode
import java.util.Properties

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
    private val dock: Dock = settings.choiceSetting(
        "display.dock", Dock.AUTO, mapOf("auto" to Dock.AUTO, "driver" to Dock.DRIVER, "bottom" to Dock.BOTTOM),
    )

    /** Pixels at each edge CarPlay keeps its controls out of; all zero declares none, as before. */
    private val safe: Insets = Insets(
        top = settings.nonNegativeSetting("display.safe.top"),
        bottom = settings.nonNegativeSetting("display.safe.bottom"),
        left = settings.nonNegativeSetting("display.safe.left"),
        right = settings.nonNegativeSetting("display.safe.right"),
    ).also { insets ->
        require(insets.left + insets.right < width) {
            "display.safe.left and display.safe.right leave no width on a $width-pixel screen"
        }
        require(insets.top + insets.bottom < height) {
            "display.safe.top and display.safe.bottom leave no height on a $height-pixel screen"
        }
    }

    /** The screen less its insets, where the picture CarPlay keeps its controls inside lies; the whole screen without insets. */
    val safeRect: AreaRect = AreaRect(safe.left, safe.top, width - safe.left - safe.right, height - safe.top - safe.bottom)

    /** Whether the CarPlay picture is published for other apps on this PC to show (display.share). */
    val share = settings.booleanSetting("display.share", false)

    /** /info's default is true too, so an older file that lacks it declares the same. */
    private val safeAreaDrawOutside: Boolean = settings.booleanSetting("display.safe.draw.outside", true)

    /**
     * square (the default, as a window is rectangular): the picture fills the screen with right-angled corners
     * (mainDisplayCornerMasks); rounded leaves CarPlay's rounded corners.
     */
    private val roundedCorners: Boolean = settings.choiceSetting("display.corners", false, mapOf("rounded" to true, "square" to false))

    /**
     * display.touch, what the screen's touch can do, as /info's features and primaryInputDevice say it. With touch,
     * swipe or tap, CarPlay shows scroll buttons in its lists; none (the default, for a mouse) makes the knob (the keys
     * and the controls window, always declared) the primary input, and CarPlay leaves them out, while clicks and
     * drags still reach it as taps.
     */
    private val touch: TouchMode = settings.choiceSetting(
        "display.touch", TouchMode.NONE, mapOf("swipe" to TouchMode.SWIPE, "tap" to TouchMode.TAP, "none" to TouchMode.NONE),
    )

    /** The main screen as the iPhone is told of it. */
    val screen = ScreenSpec(width, height, fps, physicalMm, safe, roundedCorners, dock, touch, safeAreaDrawOutside)

    /** What the iPhone is told about its screen, for the start-up log. */
    fun summary(): String {
        val (widthMm, heightMm) = physicalMm
        val ppi = Math.round(width * MM_PER_INCH / widthMm)
        return "display ${width}x$height px, ${widthMm}x$heightMm mm ($ppi ppi), $fps fps; " +
            "window ${window.width}x${window.height}"
    }
}
