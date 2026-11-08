package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.bridge.FrameTap
import io.github.numberonebot.chairplay.phone.ScreenKind
import io.github.numberonebot.chairplay.phone.VideoCodecKind
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows a phone's screens, each in its window of [windows]: the main screen, and the car's instrument cluster when there
 * is a window for it (a screen without one is ignored). Each screen stream's thread hands its Annex B data here; it is
 * decoded on that thread and each picture goes to [frameTap] and then to its window, which draws it on the GL thread.
 */
class ScreenDecoders(
    windows: Map<ScreenKind, VideoWindow>,
    /** display.share: sees each picture before its window takes it, to publish the main screen for games. */
    private val frameTap: FrameTap = FrameTap.NONE,
    private val log: (String) -> Unit = {},
    private val hardwareDecoding: () -> Boolean = { true },
) : Closeable {
    private val diagnosticHandlers = ConcurrentHashMap<ScreenKind, (String) -> Unit>()
    private val recoveryHandlers = ConcurrentHashMap<ScreenKind, () -> Unit>()
    // The handler sends a command, and a failed send closes the session, which stops this stream and
    // closes the decoder. Run inside decode() that would free the decoder under its own feet, so the
    // request is posted away from the decoder's call path (as AndroidMediaSink does).
    private val recoveryExecutor = Executors.newSingleThreadExecutor { Thread(it, "video-recovery").apply { isDaemon = true } }
    /** Runs on the cluster window's thread when the first picture of a cluster stream newly set up is on screen. */
    @Volatile
    var onClusterStream: () -> Unit = {}
    private val clusterSetUp = AtomicBoolean()

    private val screens: Map<ScreenKind, Screen> = windows.mapValues { (kind, window) -> Screen(kind, window) }

    /** One screen stream's decoder and window. */
    private inner class Screen(private val kind: ScreenKind, val window: VideoWindow) {
        private val name = kind.name.lowercase()
        private val lock = Any()
        private var decoder: VideoDecoder? = null
        private var codec = VideoCodecKind.H264
        /** The running decoder's configuration; a new codec or a stop closes the decoder, so it counts only with one. */
        private var configured: ByteArray? = null
        private var lastRecoveryNanos = 0L
        private val firstFramePending = AtomicBoolean()
        // Whether the tap has failed already: logged once per screen.
        private var tapFailed = false

        init {
            window.onFrameShown = {
                if (firstFramePending.compareAndSet(true, false)) {
                    diagnosticHandlers[kind]?.invoke(FIRST_FRAME)
                }
                // Once per stream set up, not per decoder configuration: the phone may configure one again as asked
                // for a view, and the view asked for again would loop.
                if (kind == ScreenKind.CLUSTER && clusterSetUp.compareAndSet(true, false)) {
                    onClusterStream()
                }
            }
        }

        fun selectCodec(value: VideoCodecKind) = synchronized(lock) {
            window.titles.codec(value)
            if (codec != value) {
                decoder?.close()
                decoder = null
                codec = value
            }
            log("video codec $value of screen $name")
        }

        /**
         * A configuration starts a stream, so it starts a fresh decoder and awaits that stream's first picture. The same
         * one again keeps the decoder: at 1920x1080 the iPhone can repeat it right after the keyframe and send no new
         * keyframe, which a fresh decoder would wait for (AndroidMediaSink keeps its decoder too).
         */
        fun configure(parameterSets: ByteArray) = synchronized(lock) {
            if (decoder != null && configured.contentEquals(parameterSets)) {
                log("video screen $name: configuration unchanged; keeping the decoder")
                return
            }
            configured = parameterSets
            decoder?.close()
            // The window releases the picture once drawn, so the tap reads it first.
            decoder = VideoDecoder({ frame -> tap(frame); window.show(frame) }, ::recover, codec, { log("video screen $name: $it") }, hardwareDecoding())
                .also { it.configure(parameterSets) }
            firstFramePending.set(true)
        }

        /** Hands [frame] to the tap; whatever it throws, an Error too, the window still gets the picture. */
        private fun tap(frame: DecodedFrame) {
            try {
                frameTap.frame(kind, frame)
            } catch (e: Throwable) {
                if (!tapFailed) log("video screen $name: the frame tap failed: $e")
                tapFailed = true
            }
        }

        fun decode(annexB: ByteArray) = synchronized(lock) { decoder?.decode(annexB) }

        fun stop() = synchronized(lock) {
            decoder?.close()
            decoder = null
            window.titles.codec(null)
        }

        /** The decoder rejected a packet: ask the phone for a keyframe of this screen, at most once a second. */
        private fun recover(reason: String) {
            log("video decoder ($name): $reason")
            requestKeyframe()
        }

        /** Asks the phone for a keyframe of this screen through the recovery handler, at most once a second. */
        fun requestKeyframe() {
            if (recoveryExecutor.isShutdown) return
            val now = System.nanoTime()
            if (now - lastRecoveryNanos < RECOVERY_INTERVAL_NANOS && lastRecoveryNanos != 0L) return
            lastRecoveryNanos = now
            recoveryExecutor.execute { recoveryHandlers[kind]?.invoke() }
        }
    }

    init {
        frameTap.attach { requestKeyframe(ScreenKind.MAIN) }
    }

    /** Asks the phone for a keyframe of [screen] through its recovery handler, at most once a second. */
    fun requestKeyframe(screen: ScreenKind) {
        screens[screen]?.requestKeyframe()
    }

    /** The codec of [screen]'s stream; a new one closes the decoder until the next configuration. */
    fun selectCodec(screen: ScreenKind, codec: VideoCodecKind) {
        screens[screen]?.selectCodec(codec)
    }

    /** Starts [screen]'s stream with its Annex B parameter sets (empty when it brings its own). */
    fun configure(screen: ScreenKind, annexBParameterSets: ByteArray) {
        screens[screen]?.configure(annexBParameterSets)
    }

    fun decode(screen: ScreenKind, annexB: ByteArray) {
        screens[screen]?.decode(annexB)
    }

    /** Hears "first frame rendered" once the first picture of each configuration is on screen. */
    fun setDiagnosticHandler(screen: ScreenKind, handler: (String) -> Unit) {
        diagnosticHandlers[screen] = handler
    }

    /** Asks the phone for a keyframe of [screen]; called away from the decoder, at most once a second. */
    fun setRecoveryHandler(screen: ScreenKind, handler: () -> Unit) {
        recoveryHandlers[screen] = handler
    }

    /** [screen]'s stream started or stopped; a stop closes its decoder and forgets its handlers. */
    fun active(screen: ScreenKind, on: Boolean) {
        if (on && screen == ScreenKind.CLUSTER) clusterSetUp.set(true)
        if (on) return frameTap.active(screen, true)
        val decoding = screens[screen] ?: return
        diagnosticHandlers.remove(screen)
        recoveryHandlers.remove(screen)
        decoding.stop()
        // After the decoder closed, so no picture of this stream follows the stop.
        frameTap.active(screen, false)
    }

    /** Stops the decoders, then closes the tap after them, and the windows. */
    override fun close() {
        screens.values.forEach { it.stop() }
        (frameTap as? Closeable)?.close()
        recoveryExecutor.shutdownNow()
        screens.values.forEach { it.window.close() }
    }

    private companion object {
        const val FIRST_FRAME = "first frame rendered"
        const val RECOVERY_INTERVAL_NANOS = 1_000_000_000L
    }
}
