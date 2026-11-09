package io.github.numberonebot.chairplay.media.apac

import io.github.numberonebot.chairplay.media.matchDeviceOrDefault
import io.github.numberonebot.chairplay.platform.NativeLibraries
import io.github.numberonebot.chairplay.platform.OutputDevice
import io.github.numberonebot.chairplay.platform.Platforms
import io.github.numberonebot.chairplay.platform.SpatialOutputs
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

    @Synchronized fun capability(dir: Path, output: String?, fresh: Boolean = false,
        outputs: SpatialOutputs = Platforms.current.audio.spatial): SpatialCapability {
        val path = dir.resolve("apac").resolve(NativeLibraries.fileName("chairplay_apac")).toAbsolutePath().normalize()
        val key = "$path|$output"
        val now = System.nanoTime()
        cache[key]?.takeIf { !fresh && now - it.first < 3_000_000_000L }?.let { return it.second }
        val result = try {
            if (!Files.isRegularFile(path)) throw IOException("Spatial audio decoder is unavailable")
            val library = libraries.getOrPut(path) { ApacNativeLibrary.load(path) }
            val device = spatialDevice(outputs, output)
            val channels = outputs.open(device.id, 0).use { it.channels }
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

/**
 * Whether a connection offers APAC: Buffered playback, the decoder loaded, the output device opened. Only a
 * connection's configuration asks (DesktopConfig.now()): at the start, before any window, it took most of a second.
 */
internal fun spatialConfigured(dir: Path, settings: Properties): Boolean {
    val mode = settings.getProperty("audio.music.mode", "buffered").trim().lowercase().ifEmpty { "buffered" }
    return mode == "buffered" &&
        SpatialRuntime.capability(dir, settings.getProperty("audio.output")).available
}

/** The output [setting] (audio.output) picks; a device that is gone or named by several gives way to the default. */
internal fun spatialDevice(outputs: SpatialOutputs, setting: String?): OutputDevice {
    val all = outputs.devices()
    // As the rest of the sound: a device that is gone gives way to the Windows default (AppAudio warns once).
    val name = matchDeviceOrDefault("audio.output", setting, all.map { it.name }) {}
    return if (name == null) outputs.defaultDevice() else all.single { it.name == name }
}
