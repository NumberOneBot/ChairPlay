package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.GpsMethod
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep

/**
 * The GPS output's posts (A0 §3): gps-method {method, generation}, gps-enabled {enabled} and usb-setup {action,
 * intent?, operationId?, step?}, answered {outcome}: 200 for accepted, no-change and setup-opened, 409 for busy and stale
 * (and without a coordinator), 400 for a post that does not parse. Null for any other route.
 */
internal fun gpsPost(api: UiApi, route: String, body: Map<*, *>): Pair<Int, Map<String, Any?>>? {
    if (route !in ROUTES) return null
    val commands = api.gpsCommands() ?: return 409 to mapOf("outcome" to CommandOutcome.BUSY.key)
    val outcome = when (route) {
        "gps-method" -> {
            val method = (body["method"] as? String)?.let(GpsMethod::of)
            val generation = (body["generation"] as? Number)?.toLong()
            if (method == null || generation == null) null else commands.selectMethod(method, generation)
        }
        "gps-enabled" -> (body["enabled"] as? Boolean)?.let(commands::setEnabled)
        else -> setup(commands, body)
    } ?: CommandOutcome.INVALID
    val status = when (outcome) {
        CommandOutcome.ACCEPTED, CommandOutcome.NO_CHANGE, CommandOutcome.SETUP_OPENED -> 200
        CommandOutcome.BUSY, CommandOutcome.STALE -> 409
        CommandOutcome.INVALID -> 400
    }
    return status to mapOf("outcome" to outcome.key)
}

private fun setup(commands: io.github.numberonebot.chairplay.gps.GpsCommands, body: Map<*, *>): CommandOutcome? {
    val action = body["action"] as? String ?: return null
    if (action == "open") return (body["intent"] as? String)?.let(SetupIntent::of)?.let(commands::openUsbSetup)
    val act = SetupAction.of(action) ?: return null
    val operationId = (body["operationId"] as? Number)?.toLong() ?: return null
    val step = body["step"]?.let { key -> SetupStep.entries.firstOrNull { it.key == key } ?: return null }
    return commands.usbSetupAction(operationId, act, step)
}

private val ROUTES = setOf("gps-method", "gps-enabled", "usb-setup")
