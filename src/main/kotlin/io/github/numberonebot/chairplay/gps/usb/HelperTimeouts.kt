package io.github.numberonebot.chairplay.gps.usb

/**
 * How long each helper call may take. Engineering starting points (03 §5, A0 §5): about 5 s an RPC, each a little
 * above the helper's own bound for the same work, so the helper's precise error usually comes first. A download has
 * no total limit, only [prepareIdleMillis] without progress.
 */
data class HelperTimeouts(
    /** Start to hello and its reply; a frozen helper's first start unpacks nothing (onedir) but imports a lot. */
    val helloMillis: Long = 15_000,
    val rpcMillis: Long = 5_000,
    /** discover, polled once a second by the channel: kept short (the helper bounds usbmux at 3 s). */
    val discoverMillis: Long = 4_000,
    /** probe and tools-status open the USB developer tunnel (the helper bounds them at 15 s). */
    val probeMillis: Long = 20_000,
    val revealMillis: Long = 20_000,
    val openMillis: Long = 35_000,
    /** A set's reply; past it the helper is taken as hung (its own bound is 5 s). */
    val setMillis: Long = 7_000,
    /** return and clear: a set in flight may finish first (5 s), then their own RPC (5 s). */
    val priorityMillis: Long = 12_000,
    /** close's reply: with exitGraceMillis inside the 10 s shutdown budget. */
    val closeMillis: Long = 8_000,
    val exitGraceMillis: Long = 2_000,
    /** A ping after this long without a request keeps the session helper's watchdog (30 s) quiet. */
    val heartbeatMillis: Long = 5_000,
    val prepareIdleMillis: Long = 180_000,
    val cancelGraceMillis: Long = 5_000,
    /** After a failed start, the control helper is started again no sooner than this. */
    val restartBackoffMillis: Long = 5_000,
)
