package io.github.numberonebot.chairplay.media.apac

import io.github.numberonebot.chairplay.media.matchDeviceOrDefault
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal data class SpatialPlan(val library: ApacNativeLibrary, val device: String, val channels: Int)
internal data class SpatialCapability(val available: Boolean, val reason: String? = null, val plan: SpatialPlan? = null)

/** Advertise APAC only when its decoder and the selected Windows output are available. */
internal object SpatialRuntime {
    private val libraries = HashMap<Path, ApacNativeLibrary>()
    private val cache = HashMap<String, Pair<Long, SpatialCapability>>()

    @Synchronized fun capability(dir: Path, output: String?, fresh: Boolean = false): SpatialCapability {
        val path = dir.resolve("apac/chairplay_apac.dll").toAbsolutePath().normalize()
        val key = "$path|$output"
        val now = System.nanoTime()
        cache[key]?.takeIf { !fresh && now - it.first < 3_000_000_000L }?.let { return it.second }
        val result = try {
            if (!Files.isRegularFile(path)) throw IOException("Spatial audio decoder is unavailable")
            val library = libraries.getOrPut(path) { ApacNativeLibrary.load(path) }
            val all = WasapiOutput.devices()
            // As the rest of the sound: a device that is gone gives way to the Windows default (AppAudio warns once).
            val name = matchDeviceOrDefault("audio.output", output, all.map { it.name }) {}
            val device = if (name == null) WasapiOutput.defaultDevice() else all.single { it.name == name }
            val channels = WasapiOutput.open(device.id, 0).use { it.channels }
            SpatialCapability(true, plan = SpatialPlan(library, device.id, channels))
        } catch (error: Exception) {
            SpatialCapability(false, error.message ?: "Spatial audio is unavailable")
        } catch (_: LinkageError) { SpatialCapability(false, "Windows spatial audio output is unavailable") }
        cache[key] = now to result
        return result
    }

    fun requirePlan(dir: Path, output: String?): SpatialPlan =
        capability(dir, output, fresh = true).let { it.plan ?: throw IOException(it.reason) }
}

internal fun spatialConfigured(dir: Path, settings: Properties): Boolean {
    // "spatial" is the old saved name for buffered playback with APAC enabled.
    val mode = settings.getProperty("audio.music.mode", "buffered").trim().lowercase().ifEmpty { "buffered" }
    return mode in listOf("buffered", "spatial") &&
        SpatialRuntime.capability(dir, settings.getProperty("audio.output")).available
}
