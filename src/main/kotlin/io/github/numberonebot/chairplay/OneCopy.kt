package io.github.numberonebot.chairplay

import io.github.numberonebot.chairplay.platform.OneCopyClaim
import io.github.numberonebot.chairplay.platform.Platforms
import java.io.Closeable
import kotlin.system.exitProcess

/**
 * One ChairPlay on this PC, whatever its folder. [InstanceLock] locks a copy's own folder, so the installed copy and a
 * portable one could both run and fight over the iPhone's Bluetooth channel and port 7000. The platform's claim on a
 * name in the user's session is held while a copy runs: one that finds it taken already knows another copy is running.
 */
internal object OneCopy {
    const val NAME = "ChairPlay"

    /** How long a copy that is ending may still hold the name after letting go of its folder. */
    const val GOING_WAIT_MILLIS = 5_000L
}

/** The copy held for the whole run; never closed, so it goes with the process. */
private var held: Closeable? = null

/**
 * Holds the claim for this run, or, with another copy of ChairPlay running from another folder, says so in a message
 * box ([show]) and ends this one before it starts a session.
 */
internal fun claimOneCopy(
    waitMillis: Long,
    show: (String) -> Unit = { Platforms.current.shell.showError(it) },
    exit: (Int) -> Unit = { exitProcess(it) },
    claim: OneCopyClaim = Platforms.current.oneCopy,
) {
    held = claim.claim(OneCopy.NAME, waitMillis) ?: run {
        log("another ChairPlay is running from another folder; this one ends")
        runCatching { show("ChairPlay is already running from another folder. Close it, then start this one again.") }
        exit(1)
        return
    }
}
