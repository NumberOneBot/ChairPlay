package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.config.ALTSCREEN_MIN_WIDTH
import io.github.numberonebot.chairplay.config.AltscreenCards
import io.github.numberonebot.chairplay.config.AltscreenContent
import io.github.numberonebot.chairplay.config.ControlsWindowStore
import io.github.numberonebot.chairplay.config.DesktopConfig
import io.github.numberonebot.chairplay.config.MAIN_WINDOW_MIN_SIZE
import io.github.numberonebot.chairplay.config.WindowPlacementStore
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.config.altscreenTitle
import io.github.numberonebot.chairplay.config.fittedTo
import io.github.numberonebot.chairplay.media.VideoWindow
import java.nio.file.Path

private val CONTROLS_SIZE = WindowSize(207, 412)

/**
 * The app's three windows, each opening where it was left (window.properties) and saving its place as it closes:
 * CarPlay's, titled [mainTitle]; the car's controls; and the AltScreen.
 */
internal class AppWindows(dir: Path, config: DesktopConfig, mainTitle: String) {
    val main: VideoWindow

    /** Whether the controls window was shown as the app last closed. */
    val controlsShown = ControlsWindowStore(dir)
    val controls: VideoWindow
    val altscreen: VideoWindow

    init {
        val placements = WindowPlacementStore(dir)
        main = VideoWindow(
            config.window.width, config.window.height, placements.load(config.window), mainTitle,
            aspect = WindowSize(config.displayWidth, config.displayHeight).takeIf { config.keepAspect },
            minSize = MAIN_WINDOW_MIN_SIZE,
            stream = WindowSize(config.displayWidth, config.displayHeight),
        ).also { it.commands.setAlwaysOnTop(config.windowOnTop.main) }
        main.onPlacement = { placement ->
            runCatching { placements.save(placement, config.window) }.onFailure { log("window place not saved: $it") }
        }
        // The car's controls as buttons in a window of their own, at a fixed size: the web UI's controls page. Its close
        // button and the settings screen's trigger hide and show it, and it opens as it was left; until then
        // window.controls decides.
        controls = run {
            val placements = WindowPlacementStore(dir, prefix = "controls.")
            VideoWindow(
                CONTROLS_SIZE.width, CONTROLS_SIZE.height, placements.load(CONTROLS_SIZE), "Controls",
                resizable = false, hideOnClose = true,
                shown = runCatching { controlsShown.load(default = config.controlsWindow) }.getOrDefault(config.controlsWindow),
                pageSized = true,
            ).also {
                it.onPlacement = { placement ->
                    runCatching { placements.save(placement, CONTROLS_SIZE) }.onFailure { error -> log("controls window place not saved: $error") }
                }
            }
        }
        // The altscreen (altscreen.enabled) gets a window of its own beside the main one; it takes no clicks, so a press
        // anywhere on it moves it. Its close button hides it, and the settings screen's trigger shows it again; it opens
        // at every start it is on for, and waits hidden while it is off, until a connection turns it on.
        val altPlacements = WindowPlacementStore(dir, prefix = "altscreen.")
        altscreen = config.altscreenSize.let { size ->
            val alt = config.altscreen
            VideoWindow(
                size.width, size.height, altPlacements.load(size),
                altscreenTitle(size, alt?.content ?: AltscreenContent.MAP_CARD, alt?.cards ?: AltscreenCards.TOP),
                aspect = size.takeIf { config.keepAspect }, hideOnClose = true, shown = alt != null,
                minSize = WindowSize(ALTSCREEN_MIN_WIDTH, 1).fittedTo(size), minScales = false,
                stream = size, dragAnywhere = true,
            ).also {
                it.commands.setAlwaysOnTop(config.windowOnTop.altscreen)
                it.onPlacement = { placement ->
                    val now = it.stream ?: size
                    runCatching { altPlacements.save(placement, now) }.onFailure { error -> log("altscreen place not saved: $error") }
                }
            }
        }
    }

    fun saved(changes: Map<String, String>) {
        changes["window.always.on.top"]?.let { main.commands.setAlwaysOnTop(it == "true") }
        changes["altscreen.always.on.top"]?.let { altscreen.commands.setAlwaysOnTop(it == "true") }
    }
}
