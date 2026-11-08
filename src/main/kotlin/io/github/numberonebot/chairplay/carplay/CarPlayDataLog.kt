package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.iap2.wire.Iap2Parameter
import com.shilapi.xcertplay.transport.Iap2ArtworkTransfer
import java.io.BufferedWriter
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.HexFormat

/** The iAP2 link a frame came over: the Bluetooth bootstrap, or the tunnel in the Wi-Fi session. */
enum class DataLink(val label: String) { BLUETOOTH("bluetooth"), WIFI("wifi") }

/**
 * log.carplay.data: every data message the iPhone sends (see [CarPlayDataMessages.isData]) as one JSON object
 * per line, in `carplay-data-<yyyy-MM-dd-HHmmss>.jsonl` in [directory], made at the first message. A line holds
 * the time, the link, the message id and name, and the decoded fields, with any parameter the decoder does not
 * know as `"<id>:<hex>"`; a message it cannot decode is written as hex. Other messages are never written, so
 * the Wi-Fi passphrase and the MFi exchange stay out. A failure to write stops the log, never the session.
 */
class CarPlayDataLog(
    private val directory: Path,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val log: (String) -> Unit = {},
) : Closeable {
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var stopped = false

    /** The file, once the first line made it. */
    @Volatile var file: Path? = null
        private set

    fun frame(link: DataLink, frame: Iap2Frame) {
        val id = frame.messageId
        if (!CarPlayDataMessages.isData(id)) return
        val line = StringBuilder()
        line.head(link).append(",\"id\":").string("0x" + id.toString(16).padStart(4, '0'))
        line.append(",\"name\":").string(CarPlayDataMessages.name(id))
        val fields = CarPlayDataMessages.decode(frame)
        if (fields != null) {
            line.append(",\"fields\":")
            line.fields(fields, nestUnknown = false)
            if (fields.unknown.isNotEmpty()) line.append(",\"unknown\":").unknown(fields.unknown)
        }
        if (fields == null || CarPlayDataMessages.keepsRawBytes(id)) line.append(",\"hex\":").string(HEX.formatHex(frame.payload))
        write(line.append('}'))
    }

    fun artwork(link: DataLink, transfer: Iap2ArtworkTransfer) {
        val line = StringBuilder()
        line.head(link).append(",\"name\":").string("NowPlayingArtwork")
        line.append(",\"fields\":{\"transferId\":").append(transfer.id).append(",\"bytes\":").append(transfer.bytes.size).append("}}")
        write(line)
    }

    override fun close() {
        synchronized(lock) {
            stopped = true
            runCatching { writer?.close() }
            writer = null
        }
    }

    private fun StringBuilder.head(link: DataLink): StringBuilder {
        val time = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
        append("{\"time\":").string(TIME.format(time))
        return append(",\"link\":").string(link.label)
    }

    private fun write(line: CharSequence) {
        synchronized(lock) {
            if (stopped) return
            try {
                val out = writer ?: open().also { writer = it }
                out.append(line).append('\n')
                // Flushed per line, so a crash or a hard stop keeps what came before it.
                out.flush()
            } catch (error: Exception) {
                stopped = true
                runCatching { writer?.close() }
                writer = null
                log("CarPlay data log stopped: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun open(): BufferedWriter {
        Files.createDirectories(directory)
        val path = directory.resolve("carplay-data-" + FILE_TIME.format(OffsetDateTime.now(clock)) + ".jsonl")
        return Files.newBufferedWriter(path, Charsets.UTF_8).also {
            file = path
            log("CarPlay data log: $path")
        }
    }

    private companion object {
        val HEX: HexFormat = HexFormat.of()
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        /** A group's own unknown parameters go inside it; the message's beside its fields. */
        fun StringBuilder.fields(fields: DecodedFields, nestUnknown: Boolean): StringBuilder {
            append('{')
            var first = true
            for ((name, value) in fields.values) {
                if (!first) append(',')
                first = false
                string(name).append(':').value(value)
            }
            if (nestUnknown && fields.unknown.isNotEmpty()) {
                if (!first) append(',')
                append("\"unknown\":").unknown(fields.unknown)
            }
            return append('}')
        }

        fun StringBuilder.value(value: Any?): StringBuilder = when (value) {
            null -> append("null")
            is String -> string(value)
            is Boolean, is Int, is Long -> append(value)
            is CodedValue -> if (value.name != null) string(value.name) else append(value.code)
            is DecodedFields -> fields(value, nestUnknown = true)
            is List<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    value(item)
                }
                append(']')
            }
            else -> string(value.toString())
        }

        fun StringBuilder.unknown(parameters: List<Iap2Parameter>): StringBuilder =
            value(parameters.map { "${it.id}:${HEX.formatHex(it.payload)}" })

        fun StringBuilder.string(text: String): StringBuilder {
            append('"')
            for (char in text) {
                when {
                    char == '"' -> append("\\\"")
                    char == '\\' -> append("\\\\")
                    char == '\n' -> append("\\n")
                    char == '\r' -> append("\\r")
                    char == '\t' -> append("\\t")
                    char < ' ' || char == ' ' || char == ' ' -> append("\\u").append(String.format("%04x", char.code))
                    else -> append(char)
                }
            }
            return append('"')
        }
    }
}
