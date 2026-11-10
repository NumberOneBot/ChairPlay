package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Byte layouts of the two 256-byte game bridge files. All values are little-endian. */
object BridgeLayout {
    const val SIZE = 256
    const val STATUS_MAGIC = 0x52425043
    const val TELEMETRY_MAGIC = 0x54435043
    const val VERSION = 1
    const val STATUS_FILE = "ChairPlay.Bridge.v1"
    const val TELEMETRY_FILE = "ChairPlay.Telemetry.v1"
    const val STATUS_SIZE = 136
    const val TELEMETRY_SIZE = 112

    fun textureName(generation: Int): String = "ChairPlay.Main.v1.$generation"

    /** A little-endian view of [bytes] from position 0, leaving the caller's buffer untouched. */
    internal fun view(bytes: ByteBuffer): ByteBuffer = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    internal fun readText(b: ByteBuffer, at: Int, max: Int): String {
        val raw = ByteArray(max) { b.get(at + it) }
        val end = raw.indexOf(0).let { if (it < 0) max else it }
        return String(raw, 0, end, Charsets.UTF_8)
    }

    internal fun writeText(b: ByteBuffer, at: Int, max: Int, text: String) {
        val raw = text.toByteArray(Charsets.UTF_8)
        // Keep room for the terminating zero and never cut a multi-byte character.
        var n = minOf(raw.size, max - 1)
        while (n > 0 && n < raw.size && (raw[n].toInt() and 0xC0) == 0x80) n--
        for (i in 0 until n) b.put(at + i, raw[i])
    }
}

data class BridgeStatus(
    val state: Int,
    val generation: Int,
    val width: Int,
    val height: Int,
    val safe: AreaRect,
    val dxgiFormat: Int,
    val adapterLuid: Long,
    val frameCounter: Long,
    val streamFps: Int,
    val producerPid: Int,
    val textureName: String,
) {
    companion object {
        private const val MAGIC = 0
        private const val VERSION = 4
        private const val STATE = 12
        private const val GENERATION = 16
        private const val WIDTH = 20
        private const val HEIGHT = 24
        private const val SAFE = 28
        private const val DXGI_FORMAT = 44
        private const val LUID_LOW = 48
        private const val LUID_HIGH = 52
        private const val FRAME_COUNTER = 56
        private const val STREAM_FPS = 64
        private const val PRODUCER_PID = 68
        private const val TEXTURE_NAME = 72
        private const val TEXTURE_NAME_MAX = 64

        /** Null when the magic or the version differ. */
        fun parse(bytes: ByteBuffer): BridgeStatus? {
            val b = BridgeLayout.view(bytes)
            if (b.getInt(MAGIC) != BridgeLayout.STATUS_MAGIC) return null
            if (b.getShort(VERSION).toInt() and 0xFFFF != BridgeLayout.VERSION) return null
            return BridgeStatus(
                state = b.getInt(STATE),
                generation = b.getInt(GENERATION),
                width = b.getInt(WIDTH),
                height = b.getInt(HEIGHT),
                safe = AreaRect(b.getInt(SAFE), b.getInt(SAFE + 4), b.getInt(SAFE + 8), b.getInt(SAFE + 12)),
                dxgiFormat = b.getInt(DXGI_FORMAT),
                adapterLuid = (b.getInt(LUID_HIGH).toLong() shl 32) or (b.getInt(LUID_LOW).toLong() and 0xFFFFFFFFL),
                frameCounter = b.getLong(FRAME_COUNTER),
                streamFps = b.getInt(STREAM_FPS),
                producerPid = b.getInt(PRODUCER_PID),
                textureName = BridgeLayout.readText(b, TEXTURE_NAME, TEXTURE_NAME_MAX),
            )
        }
    }
}

data class TelemetryRecord(
    val counter: Int,
    val paused: Boolean,
    val gear: Int?,
    val speed: Double,
    val headingDegrees: Double,
    val latitude: Double,
    val longitude: Double,
    val rpm: Double?,
    val source: String,
    /** Where the tachometer's red zone starts, in rpm; null when not given or not an engine's. */
    val redline: Double? = null,
    /**
     * The pedals pressed, 0 to 1, and the steering wheel's angle in degrees, below 0 to the left and as far round as the
     * game turns it; each null when not given.
     */
    val throttle: Double? = null,
    val brake: Double? = null,
    val steerDegrees: Double? = null,
    /** The car's top speed, m/s, for the speedometer's scale; null when not given or not above 0. */
    val topSpeed: Double? = null,
) {
    companion object {
        private const val MAGIC = 0
        private const val VERSION = 4
        private const val SIZE = 6
        private const val COUNTER = 12
        private const val FLAGS = 16
        private const val GEAR = 20
        private const val SPEED = 24
        private const val HEADING = 32
        private const val LATITUDE = 40
        private const val LONGITUDE = 48
        private const val RPM = 56
        private const val REDLINE = 60
        private const val SOURCE = 64
        private const val SOURCE_MAX = 32
        private const val THROTTLE = 96
        private const val BRAKE = 100
        private const val STEER = 104
        private const val TOP_SPEED = 108
        private const val FLAG_RPM = 1
        private const val FLAG_GEAR = 2
        private const val FLAG_PAUSED = 4
        private const val FLAG_REDLINE = 8
        private const val FLAG_THROTTLE = 16
        private const val FLAG_BRAKE = 32
        private const val FLAG_STEER = 64
        private const val FLAG_TOP_SPEED = 128
        /** A red line at or below 1000 rpm or above 20000 is no engine's (and would number a dial past reading). */
        private const val LOWEST_REDLINE = 1000.0
        private const val HIGHEST_REDLINE = 20000.0

        /** Null when the magic or version differ, or a required value is not finite or out of range. */
        fun parse(bytes: ByteBuffer): TelemetryRecord? {
            val b = BridgeLayout.view(bytes)
            if (b.getInt(MAGIC) != BridgeLayout.TELEMETRY_MAGIC) return null
            if (b.getShort(VERSION).toInt() and 0xFFFF != BridgeLayout.VERSION) return null
            val speed = b.getDouble(SPEED)
            val heading = b.getDouble(HEADING)
            val latitude = b.getDouble(LATITUDE)
            val longitude = b.getDouble(LONGITUDE)
            if (!speed.isFinite() || !heading.isFinite()) return null
            if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
            if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
            val flags = b.getInt(FLAGS)
            val rpm = b.getFloat(RPM).toDouble()
            val redline = b.getFloat(REDLINE).toDouble()
            // A pedal or the wheel given as a number, held to its range; not a number, as if not given.
            fun input(flag: Int, at: Int, range: ClosedFloatingPointRange<Double>) =
                b.getFloat(at).toDouble().takeIf { flags and flag != 0 && it.isFinite() }?.coerceIn(range)
            return TelemetryRecord(
                counter = b.getInt(COUNTER),
                paused = flags and FLAG_PAUSED != 0,
                gear = if (flags and FLAG_GEAR != 0) b.getInt(GEAR) else null,
                speed = speed,
                headingDegrees = ((heading % 360.0) + 360.0) % 360.0,
                latitude = latitude,
                longitude = longitude,
                rpm = if (flags and FLAG_RPM != 0 && rpm.isFinite()) rpm else null,
                source = BridgeLayout.readText(b, SOURCE, SOURCE_MAX),
                // NaN fails both comparisons, infinity the second.
                redline = if (flags and FLAG_REDLINE != 0 && redline > LOWEST_REDLINE && redline <= HIGHEST_REDLINE) redline else null,
                throttle = input(FLAG_THROTTLE, THROTTLE, 0.0..1.0),
                brake = input(FLAG_BRAKE, BRAKE, 0.0..1.0),
                steerDegrees = input(FLAG_STEER, STEER, -Double.MAX_VALUE..Double.MAX_VALUE),
                topSpeed = b.getFloat(TOP_SPEED).toDouble().takeIf { flags and FLAG_TOP_SPEED != 0 && it.isFinite() && it > 0 },
            )
        }

        /** Writes the record over the first bytes of [into], clearing the rest; for tests and tools. */
        fun write(record: TelemetryRecord, into: ByteBuffer) {
            val b = BridgeLayout.view(into)
            for (i in 0 until BridgeLayout.SIZE) b.put(i, 0)
            b.putInt(MAGIC, BridgeLayout.TELEMETRY_MAGIC)
            b.putShort(VERSION, BridgeLayout.VERSION.toShort())
            b.putShort(SIZE, BridgeLayout.TELEMETRY_SIZE.toShort())
            b.putInt(COUNTER, record.counter)
            var flags = 0
            if (record.rpm != null) flags = flags or FLAG_RPM
            if (record.gear != null) flags = flags or FLAG_GEAR
            if (record.paused) flags = flags or FLAG_PAUSED
            if (record.redline != null) flags = flags or FLAG_REDLINE
            if (record.throttle != null) flags = flags or FLAG_THROTTLE
            if (record.brake != null) flags = flags or FLAG_BRAKE
            if (record.steerDegrees != null) flags = flags or FLAG_STEER
            if (record.topSpeed != null) flags = flags or FLAG_TOP_SPEED
            b.putInt(FLAGS, flags)
            b.putInt(GEAR, record.gear ?: 0)
            b.putDouble(SPEED, record.speed)
            b.putDouble(HEADING, record.headingDegrees)
            b.putDouble(LATITUDE, record.latitude)
            b.putDouble(LONGITUDE, record.longitude)
            b.putFloat(RPM, (record.rpm ?: 0.0).toFloat())
            b.putFloat(REDLINE, (record.redline ?: 0.0).toFloat())
            BridgeLayout.writeText(b, SOURCE, SOURCE_MAX, record.source)
            b.putFloat(THROTTLE, (record.throttle ?: 0.0).toFloat())
            b.putFloat(BRAKE, (record.brake ?: 0.0).toFloat())
            b.putFloat(STEER, (record.steerDegrees ?: 0.0).toFloat())
            b.putFloat(TOP_SPEED, (record.topSpeed ?: 0.0).toFloat())
        }
    }
}
