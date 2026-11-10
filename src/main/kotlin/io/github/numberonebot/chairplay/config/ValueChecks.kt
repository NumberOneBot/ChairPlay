package io.github.numberonebot.chairplay.config

import io.github.numberonebot.chairplay.drive.Coordinates

// The checks of single values the settings screens save, each giving the message for a value it refuses or null.

internal fun checkBoolean(value: String): String? =
    if (value.trim().lowercase() in setOf("true", "false")) null else "Must be true or false"

internal fun checkOptionalSize(value: String, min: Int): String? {
    if (value.isBlank()) return null
    val number = value.trim().toIntOrNull()
    return when {
        number == null || number < 1 -> "Enter a positive whole number of pixels, or leave it blank"
        number < min -> "The main window is at least $min pixels"
        number > MAX_PIXELS -> TOO_MANY_PIXELS
        else -> null
    }
}

internal fun checkDeviceName(value: String): String? = when {
    value.any(Char::isISOControl) -> "The name cannot hold control characters"
    value.trim().toByteArray(Charsets.UTF_8).size > 63 -> "The name is at most 63 bytes (fewer letters outside English)"
    else -> null
}

internal val address = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")

internal fun checkAddress(value: String): String? =
    if (value.isBlank() || address.matches(value.trim())) null
    else "Enter a Bluetooth address such as 01:23:45:67:89:AB, or leave it blank"

internal fun checkPositive(value: String, unit: String): String? =
    if ((value.trim().toIntOrNull() ?: 0) > 0) null else "Enter a positive whole number $unit"

internal fun checkOptionalPositive(value: String): String? =
    if (value.isBlank() || (value.trim().toIntOrNull() ?: 0) > 0) null else "Enter a positive whole number, or leave it blank"

internal fun checkNonNegative(value: String): String? =
    if ((value.trim().toIntOrNull() ?: -1) >= 0) null else "Enter zero or more pixels"

internal fun checkMillis(value: String): String? =
    if ((value.trim().toIntOrNull() ?: -1) >= 0) null else "Enter zero or more milliseconds"

internal fun checkOptionalNonNegative(value: String): String? =
    if (value.isBlank() || (value.trim().toIntOrNull() ?: -1) >= 0) null else "Enter zero or more pixels, or leave it blank"

internal fun checkDuckDecibels(value: String): String? =
    if (value.trim().toIntOrNull() in DesktopConfig.DUCK_DECIBELS) null else "Enter a level from -30 to 0 dB"

internal fun checkStart(value: String): String? =
    if (value.isBlank() || Coordinates.parse(value) != null) null
    else "Enter a latitude and longitude, such as 50.4501, 30.5234, or paste a map link"

internal fun checkNumber(value: String, range: ClosedFloatingPointRange<Double>, what: String, unit: String): String? =
    if (value.isBlank() || value.trim().toDoubleOrNull()?.let { it in range } == true) null
    else "Enter $what from ${range.start.toInt()} to ${range.endInclusive.toInt()} $unit"

internal fun checkWhole(value: String, range: IntRange, what: String, unit: String): String? =
    if (value.isBlank() || value.trim().toIntOrNull()?.let { it in range } == true) null
    else "Enter $what from ${range.first} to ${range.last} $unit"

internal fun checkFps(value: String): String? =
    if (value.trim().toIntOrNull() in 30..60) null else "Enter a frame rate from 30 to 60"

internal fun checkPositiveEven(value: String): String? {
    val number = value.trim().toIntOrNull()
    return when {
        number == null || number < 1 || number % 2 != 0 -> "Enter a positive even number of pixels"
        number > MAX_PIXELS -> TOO_MANY_PIXELS
        else -> null
    }
}

internal fun checkAreaRect(value: String): String? {
    if (value.isBlank()) return null
    val rect = AreaRect.parse(value) ?: return "Enter four whole numbers: x, y, width, height"
    return if (!rect.isLargeEnough) "Enter a width and height of at least $MIN_AREA px" else null
}

/** The most pixels a side of a screen or window may have. */
internal const val MAX_PIXELS = 4096
internal const val TOO_MANY_PIXELS = "Enter a smaller number of pixels"
