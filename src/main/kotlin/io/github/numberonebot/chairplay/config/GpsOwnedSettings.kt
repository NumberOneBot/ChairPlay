package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.gps.GpsMethod

/**
 * The gps.* settings the GPS output's coordinator alone writes, through its own commands: a page's settings save is
 * refused them, so the method, the switch and the prepared iPhone never change past its lifecycle (A0 §3).
 */
internal object GpsOwnedSettings {
    val keys = setOf("gps.method", "gps.enabled", "gps.usb.device")

    /** A page's save of one of [keys]. */
    const val REFUSED = "Set with the GPS page's controls"

    /** The problem with the coordinator's [value] for [key], or null. */
    fun check(key: String, value: String): String? = when (key) {
        "gps.method" -> if (GpsMethod.of(value.trim()) != null) null else "Choose carplay or usb"
        "gps.enabled" -> if (value.trim() == "true" || value.trim() == "false") null else "Enter true or false"
        else -> null
    }
}
