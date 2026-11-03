package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class SpatialWorkerTest {
    @Test fun `seek anchor supersedes the paused device position before the next packet arrives`() {
        val worker = SpatialWorker({ TestSpatialDevice() }, { SpatialDecoder(null, it) }, { 1f }, { 1f }, {}, emptyMap())
        ApapStreamSource(ByteArray(32), worker).use { source ->
            source.retainFrom(1024)
            source.begin()
            source.pause()
            source.retainFrom(8192)
            assertEquals(8192L, source.position)
        }
    }
    @Test fun `pause replay keeps decoded tail and decoder state while flush cancels old output`() {
        val devices = CopyOnWriteArrayList<TestSpatialDevice>()
        var decodes = 0
        val worker = SpatialWorker({ TestSpatialDevice().also(devices::add) },
            { object : SpatialPacketDecoder {
                override fun decode(unit: ApapAccessUnit): SpatialDecoded {
                    decodes++
                    return SpatialDecoded(SpatialCodec.APAC, 6, ShortArray(1024 * 2) { unit.sequence.toShort() })
                }
                override fun discontinuity() {}
                override fun close() {}
            } }, { 1f }, { 1f }, {}, emptyMap(), startDelayNs = 0)
        val first = unit(1, 0)
        val second = unit(2, 1024)
        try {
            worker.begin(null)
            worker.push(first)
            worker.push(second)
            await { devices.firstOrNull()?.samples?.size == 2 }
            assertEquals(2, decodes)
            devices[0].position.set(512)
            await { worker.position == 512L }
            worker.pause()
            assertTrue(devices[0].closed)
            worker.begin(512)
            worker.push(first)
            worker.push(second)
            await { devices.size == 2 && devices[1].samples.size == 2 }
            assertEquals(2, decodes)
            assertEquals(512 * 2, devices[1].samples[0].size)
            assertEquals(1024 * 2, devices[1].samples[1].size)
            worker.pause()
            worker.begin(2048)
            worker.push(unit(3, 2048))
            await { devices.size == 3 && devices[2].samples.size == 1 }
            assertTrue(devices[2].samples[0].all { it == 3.toShort() })
        } finally { worker.close() }
        assertTrue(devices.all { it.closed })
        assertTrue(worker.cleanupComplete)
    }

    private fun unit(seq: Int, time: Long) = ApapAccessUnit(ApapMediaTime(time, 48000), seq,
        ApapMetadata(cookie = aacCookie()), byteArrayOf(1))
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Worker did not reach expected state", condition())
    }
}

internal class TestSpatialDevice(override val channels: Int = 2) : SpatialDevice {
    val samples = CopyOnWriteArrayList<ShortArray>()
    val position = AtomicLong()
    @Volatile var closed = false
    override fun write(samples: ShortArray) { check(!closed); this.samples.add(samples.copyOf()) }
    override fun playedFrames() = position.get()
    override fun cancel() {}
    override fun close() { closed = true }
}
