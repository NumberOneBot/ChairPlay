package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.CarDisplay
import io.github.numberonebot.chairplay.phone.Dock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TelemetryRecordTest {
    private fun buffer(): ByteBuffer = ByteBuffer.allocate(BridgeLayout.SIZE).order(ByteOrder.LITTLE_ENDIAN)

    private fun text(b: ByteBuffer, at: Int, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) b.put(at + i, bytes[i])
    }

    /** A record with a position (flag bit 8) unless [position] is false, and the other [flags]. */
    private fun telemetry(flags: Int, magic: Int = BridgeLayout.TELEMETRY_MAGIC, position: Boolean = true): ByteBuffer {
        val b = buffer()
        b.putInt(0, magic)
        b.putShort(4, 1)
        b.putShort(6, 96)
        b.putInt(12, 5)
        b.putInt(16, if (position) flags or POSITION else flags)
        b.putInt(20, 3)
        b.putDouble(24, 13.9)
        b.putDouble(32, 270.0)
        b.putDouble(40, 50.45)
        b.putDouble(48, 30.52)
        b.putFloat(56, 2500f)
        b.putFloat(60, 7500f)
        text(b, 64, "SimHub")
        return b
    }

    @Test
    fun `telemetry parses required and optional values`() {
        val t = TelemetryRecord.parse(telemetry(0b011))!!
        assertEquals(5, t.counter)
        assertEquals(false, t.paused)
        assertEquals(3, t.gear)
        assertEquals(13.9, t.speed, 0.0)
        assertEquals(270.0, t.headingDegrees, 0.0)
        assertEquals(50.45, t.latitude, 0.0)
        assertEquals(30.52, t.longitude, 0.0)
        assertEquals(2500.0, t.rpm!!, 0.0)
        assertEquals("SimHub", t.source)
        assertEquals(true, TelemetryRecord.parse(telemetry(0b100))!!.paused)
    }

    @Test
    fun `telemetry without optional flags has no rpm or gear`() {
        val t = TelemetryRecord.parse(telemetry(0))!!
        assertNull(t.rpm)
        assertNull(t.gear)
        assertNull(t.redline)
    }

    @Test
    fun `telemetry gives the redline at 60 with flag bit 3`() {
        assertEquals(7500.0, TelemetryRecord.parse(telemetry(0b1000))!!.redline!!, 0.0)
        assertNull(TelemetryRecord.parse(telemetry(0b0011))!!.redline)
    }

    // A redline no engine has is not given: the record still drives, only the gauges keep the GPS car's.
    @Test
    fun `telemetry ignores a redline no engine has`() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, -7000f, 0f, 500f, 1000f, 20001f)) {
            val t = TelemetryRecord.parse(telemetry(0b1000).also { it.putFloat(60, bad) })
            assertNotNull("$bad", t)
            assertNull("$bad", t!!.redline)
        }
        assertEquals(1001.0, TelemetryRecord.parse(telemetry(0b1000).also { it.putFloat(60, 1001f) })!!.redline!!, 0.0)
        assertEquals(20000.0, TelemetryRecord.parse(telemetry(0b1000).also { it.putFloat(60, 20000f) })!!.redline!!, 0.0)
    }

    // The pedals and the wheel, appended at 96, 100 and 104, each with its own flag (bits 4, 5 and 6); the wheel as the
    // steering wheel's angle in degrees, as far round as the game turns it.
    @Test
    fun `telemetry gives the pedals and the wheel with their flags`() {
        fun inputs(flags: Int, throttle: Float, brake: Float, steer: Float) =
            telemetry(flags).also { it.putFloat(96, throttle); it.putFloat(100, brake); it.putFloat(104, steer) }
        val t = TelemetryRecord.parse(inputs(0b111_0000, 0.75f, 0.25f, -450f))!!
        assertEquals(0.75, t.throttle!!, 0.0)
        assertEquals(0.25, t.brake!!, 0.0)
        assertEquals(-450.0, t.steerDegrees!!, 0.0)
        val none = TelemetryRecord.parse(inputs(0, 0.75f, 0.25f, -450f))!!
        assertNull(none.throttle)
        assertNull(none.brake)
        assertNull(none.steerDegrees)
        // Pedals past their range stop at its ends, the wheel goes round past a turn; not a number, none is given, and
        // the record still drives.
        val wild = TelemetryRecord.parse(inputs(0b111_0000, 1.5f, -0.2f, 1080f))!!
        assertEquals(listOf(1.0, 0.0, 1080.0), listOf(wild.throttle, wild.brake, wild.steerDegrees))
        val nan = TelemetryRecord.parse(inputs(0b111_0000, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN))
        assertNotNull(nan)
        assertEquals(listOf(null, null, null), listOf(nan!!.throttle, nan.brake, nan.steerDegrees))
    }

    // The car's top speed, m/s, appended at 108 with flag bit 7, for the speedometer's scale; not a number above 0, none.
    @Test
    fun `telemetry gives the top speed with flag bit 7`() {
        fun top(flags: Int, value: Float) = telemetry(flags).also { it.putFloat(108, value) }
        assertEquals(97.5, TelemetryRecord.parse(top(0b1000_0000, 97.5f))!!.topSpeed!!, 0.0)
        assertNull(TelemetryRecord.parse(top(0, 97.5f))!!.topSpeed)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -10f)) {
            val t = TelemetryRecord.parse(top(0b1000_0000, bad))
            assertNotNull("$bad", t)
            assertNull("$bad", t!!.topSpeed)
        }
    }

    @Test
    fun `telemetry with another magic is not read`() {
        assertNull(TelemetryRecord.parse(telemetry(0, magic = 0)))
    }

    @Test
    fun `telemetry refuses values a GPS cannot be`() {
        val nanSpeed = telemetry(0).also { it.putDouble(24, Double.NaN) }
        val lat = telemetry(0).also { it.putDouble(40, 91.0) }
        val lon = telemetry(0).also { it.putDouble(48, -181.0) }
        val heading = telemetry(0).also { it.putDouble(32, Double.POSITIVE_INFINITY) }
        for (b in listOf(nanSpeed, lat, lon, heading)) assertNull(TelemetryRecord.parse(b))
        assertEquals(0.0, TelemetryRecord.parse(telemetry(0).also { it.putDouble(32, 360.0) })!!.headingDegrees, 0.0)
        assertEquals(270.0, TelemetryRecord.parse(telemetry(0).also { it.putDouble(32, -90.0) })!!.headingDegrees, 0.0)
    }

    // A record without bit 8 carries no position: its speed, heading, latitude and longitude are not read, so what
    // stands there cannot refuse it (owner, 2026-11-24: games in the bridge without GPS coordinates).
    @Test
    fun `telemetry without the position bit is read without a position`() {
        val t = TelemetryRecord.parse(telemetry(0, position = false).also { it.putDouble(40, Double.NaN) })!!
        assertFalse(t.position)
        assertEquals(5, t.counter)
        assertTrue(TelemetryRecord.parse(telemetry(0))!!.position)
    }

    // The controls (bit 9): the buttons held at 112, the knob's, the zoom's and the volume's steps at 116, 120 and 124.
    @Test
    fun `telemetry gives the controls with flag bit 9`() {
        fun controls(flags: Int) = telemetry(flags, position = false).also {
            it.putInt(112, 0x2010); it.putInt(116, 3); it.putInt(120, -1); it.putInt(124, 2)
        }
        assertEquals(GameControls(0x2010, 3, -1, 2), TelemetryRecord.parse(controls(CONTROLS))!!.controls)
        assertNull(TelemetryRecord.parse(controls(0))!!.controls)
    }

    // The car's display (bit 10): its picture's size at 128 and 132, its width in millimetres at 136; a size no setting
    // could hold (odd, or not above 0) gives none.
    @Test
    fun `telemetry gives the car's display with flag bit 10`() {
        fun display(flags: Int, width: Int = 800, height: Int = 480, mm: Int = 180) = telemetry(flags).also {
            it.putInt(128, width); it.putInt(132, height); it.putInt(136, mm)
        }
        assertEquals(CarDisplay(800, 480, 180), TelemetryRecord.parse(display(DISPLAY))!!.display)
        assertNull(TelemetryRecord.parse(display(0))!!.display)
        for (bad in listOf(display(DISPLAY, width = 801), display(DISPLAY, height = 0), display(DISPLAY, mm = -5))) {
            assertNull(TelemetryRecord.parse(bad)!!.display)
        }
    }

    // Its options at 140, read only in a record of 144 bytes or more: bit 0 right-hand drive, bits 1-2 the dock (0 auto,
    // 1 driver, 2 bottom); dock 3 gives no display. A record of 140 bytes leaves both to ChairPlay's settings.
    @Test
    fun `telemetry gives the car's driving side and dock in a record of 144 bytes`() {
        fun display(size: Int, options: Int) = telemetry(DISPLAY).also {
            it.putShort(6, size.toShort()); it.putInt(128, 800); it.putInt(132, 480); it.putInt(136, 180); it.putInt(140, options)
        }
        fun parsed(size: Int, options: Int) = TelemetryRecord.parse(display(size, options))!!.display
        assertEquals(CarDisplay(800, 480, 180, rightHandDrive = true, dock = Dock.BOTTOM), parsed(144, 0b101))
        assertEquals(CarDisplay(800, 480, 180, rightHandDrive = false, dock = Dock.DRIVER), parsed(144, 0b010))
        assertEquals(CarDisplay(800, 480, 180, rightHandDrive = false, dock = Dock.AUTO), parsed(144, 0))
        assertEquals(CarDisplay(800, 480, 180), parsed(140, 0b101))
        assertNull(parsed(144, 0b110))
    }

    // The car's look (bit 11): dark with bit 12, light without; bit 12 alone gives none.
    @Test
    fun `telemetry gives the car's theme with flag bit 11`() {
        fun dark(flags: Int) = TelemetryRecord.parse(telemetry(flags))!!.dark
        assertEquals(true, dark(THEME or DARK))
        assertEquals(false, dark(THEME))
        assertNull(dark(DARK))
        assertNull(dark(0))
    }

    // A touch (bit 13): x and y at 144 and 148 in fractions of the picture, the finger down at 152; read only in a record
    // of 156 bytes or more. A place off the picture or not a number gives no touch, the rest of the record standing.
    @Test
    fun `telemetry gives a touch with flag bit 13 in a record of 156 bytes`() {
        fun touch(size: Int = 156, x: Float = 0.25f, y: Float = 0.75f, down: Int = 1, flags: Int = TOUCH) =
            TelemetryRecord.parse(telemetry(flags or CONTROLS).also {
                it.putShort(6, size.toShort()); it.putInt(112, 16)
                it.putFloat(144, x); it.putFloat(148, y); it.putInt(152, down)
            })!!
        assertEquals(GameTouch(0.25, 0.75, true), touch().touch)
        assertEquals(GameTouch(0.25, 0.75, false), touch(down = 0).touch)
        assertNull(touch(size = 144).touch)
        assertNull(touch(flags = 0).touch)
        for (bad in listOf(touch(x = 1.5f), touch(y = -0.1f), touch(x = Float.NaN), touch(y = Float.POSITIVE_INFINITY))) {
            assertNull(bad.touch)
            assertEquals(16, bad.controls!!.buttons)
        }
    }

    @Test
    fun `write then parse gives a record of controls alone back`() {
        val r = TelemetryRecord(
            4, false, null, 0.0, 0.0, 0.0, 0.0, null, "Bridge test", position = false, controls = GameControls(1, -7, 2, 0),
            display = CarDisplay(1280, 720, 300, rightHandDrive = true, dock = Dock.DRIVER), dark = true,
            touch = GameTouch(0.5, 0.25, true),
        )
        val b = buffer()
        TelemetryRecord.write(r, b)
        assertEquals(r, TelemetryRecord.parse(b))
        assertEquals(CONTROLS or DISPLAY or THEME or DARK or TOUCH, b.order(ByteOrder.LITTLE_ENDIAN).getInt(16))
        TelemetryRecord.write(r.copy(dark = false), b)
        assertEquals(false, TelemetryRecord.parse(b)!!.dark)
    }

    @Test
    fun `write then parse gives the record back`() {
        val r = TelemetryRecord(
            9, true, -1, -2.5, 90.0, -33.9, 151.2, 800.0, "Assetto Corsa", redline = 8250.0,
            throttle = 0.5, brake = 0.25, steerDegrees = -270.0, topSpeed = 83.25,
        )
        val b = buffer()
        TelemetryRecord.write(r, b)
        assertNotNull(TelemetryRecord.parse(b))
        assertEquals(r, TelemetryRecord.parse(b))
        assertEquals(8, b.order(ByteOrder.LITTLE_ENDIAN).getInt(16) and 8)
        assertEquals(8250f, b.getFloat(60), 0f)
        assertEquals(156, b.getShort(6).toInt())
        val bare = r.copy(gear = null, rpm = null, paused = false, redline = null, throttle = null, brake = null, steerDegrees = null, topSpeed = null)
        TelemetryRecord.write(bare, b)
        assertEquals(bare, TelemetryRecord.parse(b))
    }

    private companion object {
        const val POSITION = 256
        const val CONTROLS = 512
        const val DISPLAY = 1024
        const val THEME = 2048
        const val DARK = 4096
        const val TOUCH = 8192
    }
}
