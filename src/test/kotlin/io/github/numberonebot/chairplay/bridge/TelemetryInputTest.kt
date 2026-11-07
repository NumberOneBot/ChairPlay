package io.github.numberonebot.chairplay.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class TelemetryInputTest {
    private var now = 0L
    private val logs = mutableListOf<String>()
    private val source = FakeSource()
    private val input = TelemetryInput(source, { logs += it }, { now })

    private class FakeSource : TelemetrySource {
        var record: TelemetryRecord? = null
        var readable = true
        var opens = 0
        var closes = 0

        override fun open(): Boolean { opens++; return true }

        override fun read(into: ByteBuffer): Boolean {
            if (!readable) return false
            TelemetryRecord.write(record ?: return false, into)
            return true
        }

        override fun close() { closes++ }
    }

    private fun record(counter: Int, paused: Boolean = false, name: String = "AC") = TelemetryRecord(
        counter = counter, paused = paused, gear = null, speed = 10.0, headingDegrees = 90.0,
        latitude = 48.0, longitude = 2.0, rpm = null, source = name,
    )

    private fun at(seconds: Double) { now = (seconds * 1e9).toLong() }

    @Test fun `nothing written is absent`() {
        assertEquals(External.Absent, input.poll())
        at(10.0)
        assertEquals(External.Absent, input.poll())
    }

    @Test fun `a fresh update drives`() {
        source.record = record(1)
        assertEquals(External.Drives(record(1)), input.poll())
    }

    @Test fun `the same counter half a second later halts`() {
        source.record = record(1)
        input.poll()
        at(0.4)
        assertTrue(input.poll() is External.Drives)
        at(0.5)
        assertEquals(External.Halted, input.poll())
    }

    @Test fun `five seconds without change is absent`() {
        source.record = record(1)
        input.poll()
        at(4.9)
        assertEquals(External.Halted, input.poll())
        at(5.0)
        assertEquals(External.Absent, input.poll())
        source.record = record(2)
        assertTrue(input.poll() is External.Drives)
    }

    @Test fun `paused halts at once`() {
        source.record = record(1, paused = true)
        assertEquals(External.Halted, input.poll())
    }

    @Test fun `a torn record counts as no update`() {
        source.record = record(1)
        input.poll()
        source.readable = false
        at(0.3)
        assertTrue(input.poll() is External.Drives)
        at(0.6)
        assertEquals(External.Halted, input.poll())
        at(5.0)
        assertEquals(External.Absent, input.poll())
    }

    @Test fun `a record that does not parse counts as no update`() {
        source.record = record(1).copy(latitude = 200.0)
        assertEquals(External.Absent, input.poll())
    }

    @Test fun `a new source is logged once`() {
        source.record = record(1, name = "AC")
        input.poll()
        at(0.1)
        source.record = record(2, name = "AC")
        input.poll()
        at(0.2)
        source.record = record(3, name = "ETS2")
        input.poll()
        assertEquals(listOf("Telemetry from AC", "Telemetry from ETS2"), logs)
    }

    @Test fun `the source opens on the first poll and closes with the input`() {
        assertEquals(0, source.opens)
        input.poll()
        input.poll()
        assertEquals(1, source.opens)
        input.close()
        assertEquals(1, source.closes)
    }
}
