package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.platform.Platform
import io.github.numberonebot.chairplay.platform.SystemPage

/**
 * The system as the settings page sees it: its [id] picks the page's texts, it shows only the [features] listed, and a
 * button that opens a system page only for the [pages] (place names, as [UiController.open] takes) the system has.
 */
data class UiPlatform(val id: String, val features: List<String>, val pages: List<String>)

/** The page's names for the system's own pages. */
internal val SYSTEM_PLACES = mapOf(
    "bluetooth" to SystemPage.BLUETOOTH,
    "wifi" to SystemPage.WIFI,
    "firewall" to SystemPage.FIREWALL,
    "firewall-rules" to SystemPage.FIREWALL_RULES,
    "location" to SystemPage.LOCATION,
    "apple-devices-app" to SystemPage.APPLE_DEVICES,
)

internal fun Platform.forPage() =
    UiPlatform(id, features.map { it.key }, SYSTEM_PLACES.filterValues { shell.systemPage(it) != null }.keys.toList())
