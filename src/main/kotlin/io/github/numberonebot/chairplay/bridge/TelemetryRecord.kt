package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.CarDisplay
import io.github.numberonebot.chairplay.phone.Dock
import java.nio.ByteBuffer

/** The telemetry file's record ([BridgeLayout.TELEMETRY_FILE]), as a game's source writes it. */
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
    /**
     * Whether the record gives the car's place (flag bit 8): speed, heading, latitude and longitude, all 0 when it does
     * not, for a game that sends its controls alone.
     */
    val position: Boolean = true,
    /** The game's buttons and steps (flag bit 9); null when not given. */
    val controls: GameControls? = null,
    /** The car's display (flag bit 10), for CarPlay's picture; null when not given, or a size or dock no setting could hold. */
    val display: CarDisplay? = null,
    /** The car's look (flag bit 11): dark (bit 12) or light; null when not given. */
    val dark: Boolean? = null,
    /** The game's finger on CarPlay's picture (flag bit 13); null when not given or off the picture. */
    val touch: GameTouch? = null,
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
        private const val BUTTONS = 112
        private const val KNOB_STEPS = 116
        private const val ZOOM_STEPS = 120
        private const val VOLUME_STEPS = 124
        private const val DISPLAY_WIDTH = 128
        private const val DISPLAY_HEIGHT = 132
        private const val DISPLAY_WIDTH_MM = 136
        private const val DISPLAY_OPTIONS = 140
        /** The record's size from which [DISPLAY_OPTIONS] is given. */
        private const val SIZE_WITH_OPTIONS = 144
        private const val OPTION_RIGHT_HAND_DRIVE = 1
        /** Bits 1-2 of the options: the dock, by its place here; 3 is none. */
        private const val DOCK_SHIFT = 1
        private const val DOCK_MASK = 3
        private val DOCKS = listOf(Dock.AUTO, Dock.DRIVER, Dock.BOTTOM)
        private const val TOUCH_X = 144
        private const val TOUCH_Y = 148
        private const val TOUCH_DOWN = 152
        /** The record's size from which the touch is given. */
        private const val SIZE_WITH_TOUCH = 156
        private const val FLAG_RPM = 1
        private const val FLAG_GEAR = 2
        private const val FLAG_PAUSED = 4
        private const val FLAG_REDLINE = 8
        private const val FLAG_THROTTLE = 16
        private const val FLAG_BRAKE = 32
        private const val FLAG_STEER = 64
        private const val FLAG_TOP_SPEED = 128
        private const val FLAG_POSITION = 256
        private const val FLAG_CONTROLS = 512
        private const val FLAG_DISPLAY = 1024
        private const val FLAG_THEME = 2048
        private const val FLAG_DARK = 4096
        private const val FLAG_TOUCH = 8192
        /** A red line at or below 1000 rpm or above 20000 is no engine's (and would number a dial past reading). */
        private const val LOWEST_REDLINE = 1000.0
        private const val HIGHEST_REDLINE = 20000.0

        /**
         * Null when the magic or version differ, or a position given (flag bit 8) is not finite or out of range; without
         * one, its fields are not read.
         */
        fun parse(bytes: ByteBuffer): TelemetryRecord? {
            val b = BridgeLayout.view(bytes)
            if (b.getInt(MAGIC) != BridgeLayout.TELEMETRY_MAGIC) return null
            if (b.getShort(VERSION).toInt() and 0xFFFF != BridgeLayout.VERSION) return null
            val flags = b.getInt(FLAGS)
            val position = flags and FLAG_POSITION != 0
            fun place(at: Int) = if (position) b.getDouble(at) else 0.0
            val speed = place(SPEED)
            val heading = place(HEADING)
            val latitude = place(LATITUDE)
            val longitude = place(LONGITUDE)
            if (!speed.isFinite() || !heading.isFinite()) return null
            if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
            if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
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
                position = position,
                controls = if (flags and FLAG_CONTROLS == 0) null else
                    GameControls(b.getInt(BUTTONS), b.getInt(KNOB_STEPS), b.getInt(ZOOM_STEPS), b.getInt(VOLUME_STEPS)),
                display = if (flags and FLAG_DISPLAY == 0) null else display(b),
                dark = if (flags and FLAG_THEME == 0) null else flags and FLAG_DARK != 0,
                touch = if (flags and FLAG_TOUCH == 0) null else touch(b),
            )
        }

        /** On the picture (x and y finite, in 0..1) and in a record of [SIZE_WITH_TOUCH] bytes or more; else none. */
        private fun touch(b: ByteBuffer): GameTouch? {
            if (b.getShort(SIZE).toInt() and 0xFFFF < SIZE_WITH_TOUCH) return null
            val touch = GameTouch(b.getFloat(TOUCH_X).toDouble(), b.getFloat(TOUCH_Y).toDouble(), b.getInt(TOUCH_DOWN) != 0)
            return touch.takeIf { it.x in 0.0..1.0 && it.y in 0.0..1.0 }  // NaN is in no range
        }

        /**
         * As display.width and display.height take them: even pixels above 0; millimetres above 0; a known dock. A
         * record shorter than [SIZE_WITH_OPTIONS] (an older writer) leaves the driving side and the dock to the settings.
         */
        private fun display(b: ByteBuffer): CarDisplay? {
            val size = CarDisplay(b.getInt(DISPLAY_WIDTH), b.getInt(DISPLAY_HEIGHT), b.getInt(DISPLAY_WIDTH_MM))
            if (size.width <= 0 || size.width % 2 != 0 || size.height <= 0 || size.height % 2 != 0 || size.widthMm <= 0) return null
            if (b.getShort(SIZE).toInt() and 0xFFFF < SIZE_WITH_OPTIONS) return size
            val options = b.getInt(DISPLAY_OPTIONS)
            val dock = DOCKS.getOrNull((options shr DOCK_SHIFT) and DOCK_MASK) ?: return null
            return size.copy(rightHandDrive = options and OPTION_RIGHT_HAND_DRIVE != 0, dock = dock)
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
            if (record.position) flags = flags or FLAG_POSITION
            if (record.controls != null) flags = flags or FLAG_CONTROLS
            if (record.display != null) flags = flags or FLAG_DISPLAY
            if (record.dark != null) flags = flags or FLAG_THEME
            if (record.dark == true) flags = flags or FLAG_DARK
            if (record.touch != null) flags = flags or FLAG_TOUCH
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
            record.controls?.let {
                b.putInt(BUTTONS, it.buttons)
                b.putInt(KNOB_STEPS, it.knobSteps)
                b.putInt(ZOOM_STEPS, it.zoomSteps)
                b.putInt(VOLUME_STEPS, it.volumeSteps)
            }
            record.display?.let {
                b.putInt(DISPLAY_WIDTH, it.width)
                b.putInt(DISPLAY_HEIGHT, it.height)
                b.putInt(DISPLAY_WIDTH_MM, it.widthMm)
                val side = if (it.rightHandDrive == true) OPTION_RIGHT_HAND_DRIVE else 0
                b.putInt(DISPLAY_OPTIONS, side or (DOCKS.indexOf(it.dock ?: Dock.AUTO) shl DOCK_SHIFT))
            }
            record.touch?.let {
                b.putFloat(TOUCH_X, it.x.toFloat())
                b.putFloat(TOUCH_Y, it.y.toFloat())
                b.putInt(TOUCH_DOWN, if (it.down) 1 else 0)
            }
        }
    }
}
