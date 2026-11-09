package io.github.numberonebot.chairplay.config

import java.util.Properties

// The kinds of value settings.properties holds, each read by its key and refused, naming the key, when it is not one.

/** A whole number of zero or more; blank means 0. */
internal fun Properties.nonNegativeSetting(key: String): Int {
    val raw = getProperty(key)?.trim()?.ifEmpty { null } ?: return 0
    val value = raw.toIntOrNull()
    require(value != null && value >= 0) { "$key must be a whole number of zero or more, not $raw" }
    return value
}

/** A whole number of zero or more; blank means [default]. */
internal fun Properties.nonNegativeOr(key: String, default: Int): Int =
    if (getProperty(key)?.trim().isNullOrEmpty()) default else nonNegativeSetting(key)

internal fun Properties.booleanSetting(key: String, default: Boolean): Boolean =
    when (val raw = getProperty(key)?.trim()?.lowercase()?.ifEmpty { null } ?: return default) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("$key must be true or false, not $raw")
    }

/** One of [choices] by its (lower-case) name, or by an old name in [aliases], which the refusal does not offer; blank means [default]. */
internal fun <T> Properties.choiceSetting(key: String, default: T, choices: Map<String, T>, aliases: Map<String, T> = emptyMap()): T {
    val raw = getProperty(key)?.trim()?.lowercase()?.ifEmpty { null } ?: return default
    if (raw in aliases) return aliases.getValue(raw)
    require(raw in choices) { "$key must be one of ${choices.keys.joinToString()}, not $raw" }
    @Suppress("UNCHECKED_CAST")
    return choices[raw] as T
}

/** An optional positive whole number; blank means unset. */
internal fun Properties.positiveSetting(key: String): Int? {
    val raw = getProperty(key)?.trim()?.ifEmpty { null } ?: return null
    val value = raw.toIntOrNull()
    require(value != null && value > 0) { "$key must be a positive whole number, not $raw" }
    return value
}

/** A number in [range]; blank means [default]. */
internal fun Properties.decimalSetting(key: String, default: Double, range: ClosedFloatingPointRange<Double>): Double {
    val raw = getProperty(key)?.trim()?.ifEmpty { null } ?: return default
    val value = raw.toDoubleOrNull()
    require(value != null && value in range) { "$key must be a number from ${range.start.toInt()} to ${range.endInclusive.toInt()}, not $raw" }
    return value
}

internal fun Properties.evenSetting(key: String, default: Int): Int {
    val value = getProperty(key)?.trim()?.toIntOrNull() ?: default
    require(value > 0 && value % 2 == 0) { "$key must be a positive even number, not $value" }
    return value
}
