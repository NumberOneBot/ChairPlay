package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.config.Theme
import io.github.numberonebot.chairplay.media.nightMode
import io.github.numberonebot.chairplay.phone.PhoneLink
import io.github.numberonebot.chairplay.platform.Platforms
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** How often display.theme=system looks at the system's theme. */
private const val THEME_POLL_SECONDS = 5L

/**
 * CarPlay's light or dark look: it goes out once a session is active ([send]), and again when the CarPlay page saves
 * another theme ([saved]), as a car's headlights turn CarPlay dark while it runs; with display.theme=system a change of
 * the system's theme ([systemDark]) follows, looked at on [watch]. The sends go through [touchSender] to the [active] link.
 */
internal class CarPlayTheme(
    theme: Theme,
    watch: ScheduledExecutorService,
    private val touchSender: ExecutorService,
    private val active: () -> PhoneLink?,
    private val systemDark: () -> Boolean? = Platforms.current.shell::appsDark,
) {
    // Set from the session thread and read by the watch thread.
    private val theme = AtomicReference(theme)
    private val lastNight = AtomicReference<Boolean?>()

    init {
        watch.scheduleWithFixedDelay({
            val link = active()
            if (link != null && this.theme.get() == Theme.SYSTEM && nightMode(Theme.SYSTEM, systemDark) != lastNight.get()) {
                send(link)
            }
        }, THEME_POLL_SECONDS, THEME_POLL_SECONDS, TimeUnit.SECONDS)
    }

    /** Tells [link]'s phone the look the theme calls for now. */
    fun send(link: PhoneLink) {
        val night = nightMode(theme.get(), systemDark) ?: return
        lastNight.set(night)
        touchSender.execute { log("CarPlay theme ${if (night) "dark" else "light"} sent=${link.nightMode(night)}") }
    }

    /** The CarPlay page saved display.theme as [value]: the running session takes it at once. */
    fun saved(value: String) {
        theme.set(Theme.entries.firstOrNull { it.name.lowercase() == value.trim().lowercase() } ?: Theme.SYSTEM)
        active()?.let(::send)
    }
}
