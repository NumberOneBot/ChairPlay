package io.github.numberonebot.chairplay.input

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import io.github.numberonebot.chairplay.carplay.CarPlayLink
import io.github.numberonebot.chairplay.phone.PhoneLink
import java.util.concurrent.Executor

/** Bridges the opt-in experiment to the existing session event stream and ordered input sender. */
internal class CarPlayTextInput(
    private val active: () -> AirPlaySession?,
    private val sender: Executor,
    private val log: (String) -> Unit,
) {
    private val input = CharacterInput(true, log)
    // Accessed only by the ordered input sender. Keep characters and packet bytes out of logs.
    private var deliverySequence = 0L
    private var previousCandidate: ByteArray? = null
    private val keyboardState = KeyboardState()
    private var keyboardOwner: AirPlaySession? = null

    fun capturesKey(key: Int, mods: Int): Boolean = input.capturesKey(active(), key, mods)

    fun key(key: Int, action: Int, mods: Int): Boolean {
        val session = active() ?: return false
        if (!session.keyboardInputEnabled || !input.typing(session)) return false
        if (HostTextHid.enabled && HostTextHid.usesCharacterCallback(key, mods)) return true
        if (!keyboardState.accepts(key)) return false
        sender.execute {
            if (active() !== session || !input.typing(session)) return@execute
            if (keyboardOwner !== session) { keyboardState.release(); keyboardOwner = session }
            nativePackets(session, keyboardState.event(key, action, mods))
        }
        return true
    }

    fun release() { active()?.let(::releaseAll) }

    private fun releaseAll(session: AirPlaySession) {
        sender.execute {
            if (keyboardOwner === session) nativePackets(session, keyboardState.release())
        }
    }

    private fun nativePackets(session: AirPlaySession, packets: List<ByteArray>) {
        for (packet in packets) {
            if (active() !== session) return
            val wire = if (HostTextHid.enabled) byteArrayOf(1) + packet else packet
            val sent = session.sendCommand(mapOf("type" to "hidSendReport", "uuid" to KeyboardHid.UUID, "hidReport" to wire))
            log("keyboard input state #${++deliverySequence}: bytes=${wire.size} socketWrite=$sent (keys hidden)")
            if (!sent) { keyboardState.release(); return }
        }
    }

    fun character(codePoint: Int) {
        val session = active() ?: return
        if (session.keyboardInputEnabled) {
            if (HostTextHid.enabled && input.typing(session)) HostTextHid.report(codePoint)?.let { enqueue(session, it, keyboard = true) }
            return
        }
        val report = input.report(session, codePoint) ?: return
        enqueue(session, report, keyboard = false)
    }

    private fun enqueue(session: AirPlaySession, report: ByteArray, keyboard: Boolean) {
        sender.execute {
            if (active() !== session || !input.typing(session)) return@execute
            val sequence = ++deliverySequence
            val changed = previousCandidate?.let { (!it.contentEquals(report)).toString() } ?: "first"
            previousCandidate = report.copyOf()
            val write: (ByteArray) -> Boolean = { packet ->
                val name = if (keyboard && report.size != 8) "Windows text"
                    else if (keyboard) {
                        val offset = packet.size - 8
                        when {
                            packet.drop(offset + 2).any { it != 0.toByte() } -> "key down"
                            packet[offset] != 0.toByte() -> "modifiers only"
                            else -> "release all"
                        }
                    } else if (packet.contentEquals(report)) "candidate" else "clear"
                val written = active() === session && session.sendCommand(
                    mapOf("type" to "hidSendReport", "uuid" to if (keyboard) KeyboardHid.UUID else HandwritingHid.UUID, "hidReport" to packet),
                )
                log("character input #$sequence: $name bytes=${packet.size} changed=$changed socketWrite=$written (text hidden)")
                written
            }
            val sent = if (keyboard && report.size != 8) write(report)
                else if (keyboard) KeyboardHid.sendCharacter(report, write) else HandwritingHid.sendCharacter(report, write = write)
            if (!sent) log("character input #$sequence: delivery sequence incomplete")
        }
    }

    fun listener(delegate: AirPlaySessionListener): AirPlaySessionListener = object : AirPlaySessionListener by delegate {
        override fun onSessionActive(session: AirPlaySession) {
            if (session.keyboardInputEnabled) {
                val format = if (HandwritingHid.separateReports) "separate character report ID 2" else "combined touch and characters"
                val ending = if (HandwritingHid.clearAfterCharacter) "candidate + 35 ms + clear" else "candidate only"
                log("character input: experimental device advertised ($format; $ending); waiting for iPhone input mode")
                log("character input: physical HID keyboard advertised; text and editing use key down + key up")
                if (HostTextHid.enabled) log("character input: Windows Unicode reports use the keyboard sender; editing uses native report ID 1")
            } else log("keyboard input disabled for this session")
            delegate.onSessionActive(session)
        }

        override fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) {
            val wasTyping = input.typing(session)
            if (session.keyboardInputEnabled) input.command(session, type, params)
            if (wasTyping && !input.typing(session)) releaseAll(session)
            delegate.onCommand(session, type, params)
        }

        override fun onCommandResponse(session: AirPlaySession, type: String, params: Map<String, Any?>): Map<String, Any?>? =
            (if (session.keyboardInputEnabled) input.query(session, type, params) else null) ?: delegate.onCommandResponse(session, type, params)

        override fun onSessionEnded(session: AirPlaySession) {
            releaseAll(session)
            input.ended(session)
            delegate.onSessionEnded(session)
        }
    }

    companion object {
        /** Text input for the [active] link's session, while it is CarPlay's. */
        fun of(active: () -> PhoneLink?, sender: Executor, log: (String) -> Unit) =
            CarPlayTextInput({ (active() as? CarPlayLink)?.session }, sender, log)
    }
}
