package io.github.numberonebot.chairplay.hud

import io.github.numberonebot.chairplay.data.Artwork
import io.github.numberonebot.chairplay.drive.GaugeMotion
import io.github.numberonebot.chairplay.hud.maneuver.ManeuverView
import io.github.numberonebot.chairplay.hud.maneuver.RepaintTimer
import io.github.numberonebot.chairplay.media.WindowOverlay
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS
import org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES
import org.lwjgl.nanovg.NanoVGGL3.nvgCreate
import org.lwjgl.nanovg.NanoVGGL3.nvgDelete
import java.nio.ByteBuffer

/**
 * What the HUD needs at a moment: whether a session runs, its data, the cover the iPhone sent last, and whether the GPS
 * car's wells are in ([drive]: 0 gone, 1 in place, linear in time, as GaugeMotion.there gives it) and how small
 * ([driveSmallness], as GaugeMotion.smallness gives it), which keep the route and the song between them.
 */
class HudView(
    val connected: Boolean,
    val state: HudState,
    val artwork: Artwork?,
    val drive: Float = 0f,
    val driveSmallness: Float = 0f,
)

/**
 * Draws the HUD with NanoVG on black, centred, as desktop-docs/hud-spec.md lays it out:
 * the song, the route or both in the middle, and a call on top; no clock, signal or battery, which the CarPlay
 * screen shows. Sizes
 * follow the window's height. Text is in Inter; distances and calls use wide Roboto Flex SemiBold; the waiting message
 * uses Reddit Sans Bold, with Inter Bold for missing glyphs ([HUD_FONTS]). The fonts ship with the app, so the HUD looks the
 * same on every system. Symbols the fonts lack (play, pause) are drawn as vectors.
 *
 * The maneuver is the MHI2Q project's animated renderer ([ManeuverView]) where its library runs, else a simple
 * vector arrow. Every part rises from the dark and fades back into it ([HudMotion]); the song moves between the
 * middle and its column as guidance comes and goes. The animations ask for frames through [repaint], which draws the
 * window again from any thread.
 */
class HudPainter(
    private val view: () -> HudView,
    private val log: (String) -> Unit = {},
    repaint: (() -> Unit)? = null,
    /** ui.accent as 0xRRGGBB: the route's colour. */
    accent: Int = 0x52C4FF,
) : WindowOverlay {
    internal val accentColor = 0xFF000000.toInt() or accent
    internal var vg = 0L
        private set
    private var fonts = false
    // NanoVG reads the font files from these buffers for as long as the context lives.
    private val fontData = mutableListOf<ByteBuffer>()
    internal var cover = -1
    internal var coverId: Int? = null
    // The song keeps its cover while the iPhone sends it a new one, as it does when playback starts.
    internal val songCover = SongCover()
    internal var emptyCoverNote = -1
    private val color = NVGColor.create()
    internal val paint = NVGPaint.create()
    private val frames = repaint?.let { RepaintTimer(it) }
    internal val maneuverView = ManeuverView(frames, log)
    private val motion = HudMotion()
    // What each part showed last, kept while it fades into the dark after its data is gone.
    private var shownMedia: HudMedia? = null
    private var shownManeuver: HudManeuver? = null
    private var shownArrival: HudArrival? = null
    private var shownCall: String? = null

    override fun open() {
        vg = nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)
        check(vg != 0L) { "cannot create the HUD's NanoVG context" }
        fun font(name: String, file: String): Boolean {
            val bytes = HudPainter::class.java.getResourceAsStream("/fonts/$file")?.use { it.readBytes() } ?: return false
            val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).flip()
            fontData += buffer
            return nvgCreateFontMem(vg, name, buffer, false) >= 0
        }
        fonts = HUD_FONTS.all { font(it.name, it.file) }
        if (fonts) {
            HUD_FONTS.forEach { face -> face.fallback?.let { nvgAddFallbackFont(vg, face.name, it) } }
        } else log("HUD: the fonts are missing from the app; the HUD draws no text")
        HudPainter::class.java.getResourceAsStream("/icons/music-note.png")?.use { it.readBytes() }
            ?.let(::coverPixels)?.let { pixels ->
                emptyCoverNote = nvgCreateImageRGBA(vg, pixels.width, pixels.height, NVG_IMAGE_GENERATE_MIPMAPS, pixels.rgba)
            }
        if (emptyCoverNote < 0) log("HUD: the empty-cover music note could not be loaded")
    }

    override fun close() {
        if (vg != 0L) maneuverView.close(vg)
        if (cover >= 0) nvgDeleteImage(vg, cover)
        if (emptyCoverNote >= 0) nvgDeleteImage(vg, emptyCoverNote)
        emptyCoverNote = -1
        if (vg != 0L) nvgDelete(vg)
        vg = 0L
        fontData.clear()
    }

    override fun paint(width: Int, height: Int) {
        if (vg == 0L || width == 0 || height == 0) return
        val now = view()
        val state = now.state
        val connected = now.connected
        val shown = motion.update(connected, state.media != null, state.maneuver != null, state.call != null, System.nanoTime())
        if (connected && state.media != null) shownMedia = state.media else if (shown.media == 0f) shownMedia = null
        if (connected && state.maneuver != null) {
            shownManeuver = state.maneuver
            shownArrival = state.arrival
        } else if (shown.route == 0f) {
            shownManeuver = null
            shownArrival = null
        }
        if (connected && state.call != null) shownCall = state.call else if (shown.call == 0f) shownCall = null
        // Also while the maneuver is hidden (a call) or fading out, so it never animates from a stale one.
        maneuverView.update(shownManeuver)
        nvgBeginFrame(vg, width.toFloat(), height.toFloat(), 1f)
        val h = height.toFloat()
        val w = width.toFloat()
        // No clock, signal or battery: the CarPlay screen always shows them.
        // In the app's display face, as the main window's waiting screen says where the connection stands.
        emerging(shown.waiting, w / 2, h / 2) { centred(HUD_WAITING, w / 2, h / 2, 0.07f * h, GREY, STATUS) }
        // The song and the route each keep one layout round the middle and are only moved and scaled: beside each other,
        // and in drive mode into their slots between the wells, the song over the route, by the wells' own motion and
        // curve, so the blocks move as the wells do; the window draws on while they move.
        val driving = GaugeMotion.eased(now.drive)
        val smallness = GaugeMotion.sized(now.driveSmallness)
        shownManeuver?.let { maneuver ->
            val box = routeBox(w, h, 1f)
            val block = routeBlock(maneuver, shownArrival, box, h)
            val place = routePlacement(w, h, shown.routePlace, block.width, block.height, block.viewHeight, driving, smallness)
            val sharp = block.viewHeight * maxOf(1f, routeAloneScale(w, h, block.width, block.viewHeight))
            placed(place, (box.left + box.right) / 2, h / 2) { navigation(maneuver, shownArrival, box, h, shown.route, sharp) }
        }
        shownMedia?.let { song ->
            placed(songPlacement(w, h, shown.mediaPlace, driving, smallness), w / 2, h / 2) {
                media(song, now, mediaBox(w, h), h, shown.media)
            }
        }
        shownCall?.let { call ->
            val stage = driveStage(w, h, smallness)
            val place = HudPlace(w / 2, h / 2, 1f)
                .toward(HudPlace(stage.centerX, stage.centerY, fitScale(w * (1 - 2 * HUD_SIDE), 0.30f * h, stage)), driving)
            placed(place, w / 2, h / 2) { callCard(call, w, h, shown.call) }
        }
        nvgEndFrame(vg)
        if (shown.moving) frames?.after(FRAME_MILLIS)
    }

    /** Draws [content], laid out round [cx], [cy], moved and scaled to [place]. */
    private inline fun placed(place: HudPlace, cx: Float, cy: Float, content: () -> Unit) {
        if (place == HudPlace(cx, cy, 1f)) return content()
        nvgSave(vg)
        nvgTranslate(vg, place.x, place.y)
        nvgScale(vg, place.scale, place.scale)
        nvgTranslate(vg, -cx, -cy)
        content()
        nvgRestore(vg)
    }

    /**
     * Draws [content] [presence] of the way out of the dark: see-through, and a little smaller around ([cx], [cy]),
     * until it is fully there; nothing while it is gone.
     */
    internal inline fun emerging(presence: Float, cx: Float, cy: Float, content: () -> Unit) {
        if (presence <= 0f) return
        nvgSave(vg)
        nvgGlobalAlpha(vg, presence)
        if (presence < 1f) {
            val scale = lerp(EMERGE_SCALE, 1f, presence)
            nvgTranslate(vg, cx, cy)
            nvgScale(vg, scale, scale)
            nvgTranslate(vg, -cx, -cy)
        }
        content()
        nvgRestore(vg)
    }

    private fun callCard(call: String, w: Float, h: Float, presence: Float) {
        val left = HUD_SIDE * w
        val right = w - left
        val height = 0.30f * h
        val y = h / 2 - height / 2
        emerging(presence, w / 2, h / 2) {
            nvgBeginPath(vg)
            nvgRoundedRect(vg, left, y, right - left, height, height * 0.2f)
            fill(CALL_GROUND)
            // The call in the wide accent face; a long name shrinks a little before it is cut.
            val width = right - left - 0.1f * h
            val size = fitSize(call, width, 0.09f * h, 0.075f * h, WIDE)
            centred(fit(call, width, size, WIDE), w / 2, y + height / 2, size, GREEN, WIDE)
        }
    }

    // --- text and colour helpers ---

    /** Draws [text]; returns its width. */
    internal fun text(text: String, x: Float, y: Float, size: Float, rgb: Int, font: String, align: Int): Float {
        if (!fonts || text.isEmpty()) return 0f
        nvgFontFace(vg, font)
        nvgFontSize(vg, size)
        nvgTextAlign(vg, align or NVG_ALIGN_MIDDLE)
        nvgFillColor(vg, rgba(rgb))
        nvgText(vg, x, y, text)
        return bounds(text, size, font)
    }

    /**
     * How wide [text] is at [size] in [font], as drawn at scale 1. NanoVG measures at the size the current transform
     * draws it, in whole pixels, so under a scale (the HUD's blocks fitting between the GPS car's wells, or rising from
     * the dark) a width measured in the transform wobbles from frame to frame, and a line fitted by it jumps.
     */
    private fun bounds(text: String, size: Float, font: String): Float {
        nvgSave(vg)
        nvgResetTransform(vg)
        nvgFontFace(vg, font)
        nvgFontSize(vg, size)
        val width = nvgTextBounds(vg, 0f, 0f, text, null as java.nio.FloatBuffer?)
        nvgRestore(vg)
        return width
    }

    internal fun centred(text: String, x: Float, y: Float, size: Float, rgb: Int, font: String = REGULAR) {
        text(text, x, y, size, rgb, font, NVG_ALIGN_CENTER)
    }

    /** The largest size from [size] down to [smallest] at which [text] fits [width]; [smallest] if none does. */
    internal fun fitSize(text: String, width: Float, size: Float, smallest: Float, font: String): Float {
        if (!fonts || text.isEmpty()) return size
        val measured = bounds(text, size, font)
        return if (measured <= width) size else (size * width / measured).coerceAtLeast(smallest)
    }

    /** How wide [text] is at [size] in [font]. */
    internal fun measure(text: String, size: Float, font: String): Float {
        if (!fonts || text.isEmpty()) return 0f
        return bounds(text, size, font)
    }

    /** [text] cut with an ellipsis to [width] at [size]. */
    internal fun fit(text: String, width: Float, size: Float, font: String): String {
        if (!fonts) return text
        fun measure(s: String) = bounds(s, size, font)
        if (measure(text) <= width) return text
        // The longest start that fits with the ellipsis, found by halving: a few measures, not one per character.
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (measure(text.substring(0, mid) + "…") <= width) low = mid else high = mid - 1
        }
        return text.substring(0, low).trimEnd() + "…"
    }

    internal fun fill(rgb: Int) {
        nvgFillColor(vg, rgba(rgb))
        nvgFill(vg)
    }

    internal fun stroke(rgb: Int) {
        nvgStrokeColor(vg, rgba(rgb))
        nvgStroke(vg)
    }

    private fun rgba(argb: Int): NVGColor =
        nvgRGBA((argb shr 16 and 0xFF).toByte(), (argb shr 8 and 0xFF).toByte(), (argb and 0xFF).toByte(), (argb ushr 24).toByte(), color)
}

/** What the HUD says while no iPhone is connected, in the main window's words. */
internal const val HUD_WAITING = "Waiting for the iPhone…"
