package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BridgeLayoutTest {
    private fun buffer(): ByteBuffer = ByteBuffer.allocate(BridgeLayout.SIZE).order(ByteOrder.LITTLE_ENDIAN)

    private fun text(b: ByteBuffer, at: Int, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) b.put(at + i, bytes[i])
    }

    private fun status(magic: Int = BridgeLayout.STATUS_MAGIC, version: Int = 1): ByteBuffer {
        val b = buffer()
        b.putInt(0, magic)
        b.putShort(4, version.toShort())
        b.putShort(6, 136)
        b.putInt(12, 2)
        b.putInt(16, 7)
        b.putInt(20, 800)
        b.putInt(24, 480)
        b.putInt(28, 0); b.putInt(32, 0); b.putInt(36, 800); b.putInt(40, 480)
        b.putInt(44, 87)
        b.putInt(48, 0x1234)
        b.putInt(52, 1)
        b.putLong(56, 99)
        b.putInt(64, 30)
        b.putInt(68, 4242)
        text(b, 72, "ChairPlay.Main.v1.7")
        return b
    }

    private fun telemetry(flags: Int, magic: Int = BridgeLayout.TELEMETRY_MAGIC): ByteBuffer {
        val b = buffer()
        b.putInt(0, magic)
        b.putShort(4, 1)
        b.putShort(6, 96)
        b.putInt(12, 5)
        b.putInt(16, flags)
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
    fun `status parses every field at its offset`() {
        val s = BridgeStatus.parse(status())!!
        assertEquals(2, s.state)
        assertEquals(7, s.generation)
        assertEquals(800, s.width)
        assertEquals(480, s.height)
        assertEquals(AreaRect(0, 0, 800, 480), s.safe)
        assertEquals(87, s.dxgiFormat)
        assertEquals((1L shl 32) or 0x1234L, s.adapterLuid)
        assertEquals(99L, s.frameCounter)
        assertEquals(30, s.streamFps)
        assertEquals(4242, s.producerPid)
        assertEquals("ChairPlay.Main.v1.7", s.textureName)
    }

    @Test
    fun `status with another magic or version is not read`() {
        assertNull(BridgeStatus.parse(status(magic = 0)))
        assertNull(BridgeStatus.parse(status(version = 2)))
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

    @Test
    fun `write then parse gives the record back`() {
        val r = TelemetryRecord(9, true, -1, -2.5, 90.0, -33.9, 151.2, 800.0, "Assetto Corsa", redline = 8250.0)
        val b = buffer()
        TelemetryRecord.write(r, b)
        assertNotNull(TelemetryRecord.parse(b))
        assertEquals(r, TelemetryRecord.parse(b))
        assertEquals(8, b.order(ByteOrder.LITTLE_ENDIAN).getInt(16) and 8)
        assertEquals(8250f, b.getFloat(60), 0f)
        val bare = r.copy(gear = null, rpm = null, paused = false, redline = null)
        TelemetryRecord.write(bare, b)
        assertEquals(bare, TelemetryRecord.parse(b))
    }

    @Test
    fun `texture name follows the generation`() {
        assertEquals("ChairPlay.Main.v1.12", BridgeLayout.textureName(12))
    }
}
