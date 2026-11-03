package io.github.numberonebot.chairplay.ui

/**
 * What a POST to /api/[route] with the JSON object [body] asks of [api], and the answer: its status and its JSON.
 * [route] is one UiServer takes posts on.
 */
internal fun postRoute(api: UiApi, route: String, body: Map<*, *>): Pair<Int, Map<String, Any?>> {
    var answer: Pair<Int, Map<String, Any?>> = 404 to mapOf("error" to "no such API")
    fun json(status: Int, reply: Map<String, Any?>) {
        answer = status to reply
    }
    when (route) {
        "settings", "controls" -> {
            val changes = body["changes"] as? Map<*, *>
            if (changes == null || changes.any { (key, value) -> key !is String || value !is String }) {
                json(400, mapOf("error" to "changes must be an object of strings"))
                return answer
            }
            @Suppress("UNCHECKED_CAST")
            val strings = changes as Map<String, String>
            val result = if (route == "settings") api.saveSettings(strings) else api.saveControls(strings)
            if (result.errors.isNotEmpty()) json(400, mapOf("errors" to result.errors))
            else json(200, mapOf("saved" to result.applies.keys.toList().ifEmpty { strings.keys.toList() }, "applies" to result.applies))
        }
        "connect" -> {
            val error = api.connect()
            if (error == null) json(200, mapOf("ok" to true)) else json(409, mapOf("error" to error))
        }
        "reconnect" -> {
            api.reconnect()
            json(200, mapOf("ok" to true))
        }
        "disconnect" -> {
            api.disconnect()
            json(200, mapOf("ok" to true))
        }
        "screen" -> {
            val screen = body["screen"] as? String
            when {
                screen == null -> json(400, mapOf("error" to "screen missing"))
                api.show(screen) -> json(200, mapOf("ok" to true))
                else -> json(409, mapOf("error" to "cannot show $screen now"))
            }
        }
        "window" -> {
            val name = body["name"] as? String
            val visible = body["visible"] as? Boolean
            when {
                name == null || visible == null -> json(400, mapOf("error" to "name and visible missing"))
                api.window(name, visible) -> json(200, mapOf("ok" to true))
                else -> json(404, mapOf("error" to "no window $name"))
            }
        }
        "control" -> {
            val action = body["action"] as? String
            val down = body["down"] as? Boolean
            when {
                action == null || down == null -> json(400, mapOf("error" to "action and down missing"))
                api.control(action, down) -> json(200, mapOf("ok" to true))
                else -> json(404, mapOf("error" to "no button $action"))
            }
        }
        "keyboard-focus" -> {
            val client = body["client"] as? String
            val focused = body["focused"] as? Boolean
            if (client == null || client.length !in 1..80 || focused == null) json(400, mapOf("error" to "invalid focus lease"))
            else { api.keyboardFocus(client, focused); json(200, mapOf("ok" to true)) }
        }
        "key" -> {
            val name = body["name"] as? String
            val phase = body["phase"] as? String
            when {
                name == null || phase == null -> json(400, mapOf("error" to "name and phase missing"))
                api.key(name, phase, body["everything"] == true) -> json(200, mapOf("ok" to true))
                else -> json(404, mapOf("error" to "$name does nothing here"))
            }
        }
        "altscreen" -> {
            val content = body["content"] as? String
            when {
                content == null -> json(400, mapOf("error" to "content missing"))
                api.altscreenContent(content) -> json(200, mapOf("ok" to true))
                else -> json(409, mapOf("error" to "cannot show $content on the AltScreen now"))
            }
        }
        "open" -> {
            val place = body["place"] as? String
            when {
                place == null -> json(400, mapOf("error" to "place missing"))
                api.open(place) -> json(200, mapOf("ok" to true))
                else -> json(404, mapOf("error" to "cannot open $place"))
            }
        }
        "altscreen-frame" ->
            if (api.showAltscreenFrame(body["view"] as? String)) json(200, mapOf("ok" to true))
            else json(404, mapOf("error" to "no AltScreen"))
        "forget" ->
            if (api.forget()) json(200, mapOf("ok" to true))
            else json(409, mapOf("error" to "Nothing to forget"))
        "demo" ->
            if (api.demo(body["kind"] as? String)) json(200, mapOf("ok" to true))
            else json(409, mapOf("error" to "No such demo, or the AltScreen is off"))
        "volume" -> {
            val level = (body["level"] as? Number)?.toInt()
            when {
                level == null -> json(400, mapOf("error" to "send the level as a number"))
                api.volume(level) -> json(200, mapOf("ok" to true))
                else -> json(404, mapOf("error" to "no volume"))
            }
        }
        "windows-location" -> json(200, api.windowsLocation())
        "gps-drive" -> {
            // Each pedal and the wheel as a number, a missing one at rest.
            val values = listOf("throttle", "brake", "steer").map { key -> body[key]?.let { it as? Number ?: return@map null }?.toDouble() ?: 0.0 }
            when {
                values.any { it == null } -> json(400, mapOf("error" to "throttle, brake and steer must be numbers"))
                api.gpsDrive(values[0]!!, values[1]!!, values[2]!!) -> json(200, mapOf("ok" to true))
                else -> json(409, mapOf("error" to "no GPS car"))
            }
        }
        "drive-mode" -> {
            val on = body["on"] as? Boolean
            when {
                on == null -> json(400, mapOf("error" to "on missing"))
                api.driveMode(on) -> json(200, mapOf("ok" to true))
                else -> json(409, mapOf("error" to "the GPS is off"))
            }
        }
        "screenshot" ->
            if (api.screenshot()) json(200, mapOf("ok" to true)) else json(409, mapOf("error" to "no screens"))
        "firewall-rule" ->
            if (api.addFirewallRule()) json(200, mapOf("ok" to true))
            else json(409, mapOf("error" to "The rule was not added"))
        "window-size" -> {
            val window = body["window"] as? String ?: "main"
            when {
                body["actual"] != true -> json(400, mapOf("error" to "actual missing"))
                api.actualSize(window) -> json(200, mapOf("ok" to true))
                else -> json(404, mapOf("error" to "no $window window"))
            }
        }
    }
    return answer
}
