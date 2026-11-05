package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.PairingStore
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.HexFormat
import java.util.logging.Logger
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

private val PAIRING_HEX = HexFormat.of()
private val pairingLog = Logger.getLogger("chairplay.pairings")

/**
 * Desktop file persistence: upstream's PairingStore only holds keys in memory.
 * Replace this adapter if upstream adds portable, crash-safe file persistence and damaged-record recovery.
 * Filenames encode identifiers as hex because Windows forbids some identifier characters.
 */
internal fun loadPairings(dir: Path): PairingStore {
    val pairingDir = Files.createDirectories(dir.resolve("pairings"))
    var loaded = false
    val store = PairingStore { identifier, key ->
        if (loaded) savePairing(pairingDir, identifier, key)
    }
    for (file in pairingDir.listDirectoryEntries()) {
        try {
            val identifier = String(PAIRING_HEX.parseHex(file.name))
            val key = PAIRING_HEX.parseHex(file.readText().trim())
            require(identifier.isNotEmpty() && key.size == 32)
            store.save(identifier, key)
        } catch (_: IllegalArgumentException) {
            preserveDamagedPairing(dir, file)
        } catch (_: IOException) {
            preserveDamagedPairing(dir, file)
        }
    }
    loaded = true
    return store
}

/** Keep damaged data outside pairings/ so it neither blocks startup nor marks an iPhone as known. */
private fun preserveDamagedPairing(dir: Path, file: Path) {
    try {
        val destination = Files.createDirectories(dir.resolve("logs/damaged-pairings"))
        val backup = Files.createTempFile(destination, "pairing-", ".damaged")
        try {
            Files.move(file, backup, REPLACE_EXISTING)
        } catch (error: IOException) {
            Files.deleteIfExists(backup)
            throw error
        }
        pairingLog.warning("A damaged iPhone pairing was moved to $backup. Pair the iPhone again.")
    } catch (_: IOException) {
        pairingLog.warning("An iPhone pairing could not be read or moved out of $file. Check file permissions and pair the iPhone again.")
    }
}

/** Flush a complete replacement before renaming it, leaving the previous key intact if writing fails. */
private fun savePairing(dir: Path, identifier: String, key: ByteArray) {
    val file = dir.resolve(PAIRING_HEX.formatHex(identifier.toByteArray()))
    val temporary = Files.createTempFile(dir, "pairing-", ".saving")
    try {
        FileChannel.open(temporary, WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(PAIRING_HEX.formatHex(key).toByteArray())
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        Files.move(temporary, file, REPLACE_EXISTING, ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
