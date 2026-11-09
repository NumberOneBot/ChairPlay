package io.github.numberonebot.chairplay

private const val SCREENSHOT_PICTURE_WAIT_NANOS = 30_000_000_000L

/**
 * Diagnostics: CHAIRPLAY_SCREENSHOT_AFTER=<seconds> runs [shoot] that long after the first picture ([hasPicture]), or
 * after half a minute without one, so the windows the app draws itself can be looked at without an iPhone.
 */
internal fun screenshotAfterPicture(hasPicture: () -> Boolean, shoot: () -> Unit) {
    System.getenv("CHAIRPLAY_SCREENSHOT_AFTER")?.toLongOrNull()?.let { seconds ->
        Thread({
            val giveUp = System.nanoTime() + SCREENSHOT_PICTURE_WAIT_NANOS
            while (!hasPicture() && System.nanoTime() < giveUp) Thread.sleep(200)
            Thread.sleep(seconds * 1000)
            shoot()
        }, "screenshot-timer").apply { isDaemon = true }.start()
    }
}
