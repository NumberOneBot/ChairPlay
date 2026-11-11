package io.github.numberonebot.chairplay.gps.usb

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Launches [FakeHelper]s: [control] and [session] answer each role's requests; every launch is kept. */
class FakeLauncher(
    var control: FakeHelper.(Map<String, Any?>) -> Unit = { standard(it) },
    var session: FakeHelper.(Map<String, Any?>) -> Unit = { sessionStandard(it) },
) : HelperLauncher {
    val launched = CopyOnWriteArrayList<Pair<HelperRole, FakeHelper>>()
    @Volatile var failing = false
    @Volatile var helloDelayMillis = 0L

    override fun launch(role: HelperRole): HelperPipes {
        if (failing) throw IOException("no helper here")
        val helper = FakeHelper(hello = helloDelayMillis == 0L) {
            (if (role == HelperRole.CONTROL) control else session).invoke(this, it)
        }
        if (helloDelayMillis > 0) {
            val delay = helloDelayMillis
            Thread {
                Thread.sleep(delay)
                helper.say(mapOf("type" to "hello", "protocol" to 2, "role" to role.key))
            }.apply { isDaemon = true }.start()
        }
        launched += role to helper
        return helper
    }

    fun of(role: HelperRole) = launched.filter { it.first == role }.map { it.second }
}

/** A session helper that opens, applies every set at once, and answers close. */
fun FakeHelper.sessionStandard(request: Map<String, Any?>) {
    if (standard(request)) return
    when (request["op"]) {
        "open" -> reply(request, mapOf("session" to request["session"]))
        "set" -> reply(request, mapOf("applied" to true, "acknowledged" to requests.count { it["op"] == "set" }.toLong()))
        "close" -> {
            reply(request)
            exit()
        }
    }
}
