package io.github.numberonebot.chairplay.carplay

/**
 * Whether Siri or a call is on: any of their streams, sound or microphone, by its audio type. [changed] hears each
 * turn, on the thread that started or stopped the stream.
 */
internal class VoiceStreams(private val changed: (Boolean) -> Unit) {
    private val open = HashSet<Any>()

    @Synchronized fun started(stream: Any, audioType: String) {
        if (audioType.lowercase() in TYPES) turn { open += stream } else stopped(stream)
    }

    @Synchronized fun stopped(stream: Any) = turn { open -= stream }

    private fun turn(change: () -> Unit) {
        val was = open.isNotEmpty()
        change()
        if (open.isNotEmpty() != was) changed(!was)
    }

    private companion object {
        /** The engine's audio types of a call and of Siri, as AudioChannelMapping compares them. */
        val TYPES = setOf("telephony", "speechrecognition")
    }
}
