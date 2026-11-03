package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test

class SpatialOutputStatusTest {
    @Test fun `output status follows audible AAC APAC AAC instead of the prefetched codec`() {
        for (channels in listOf(2, 6)) {
            val device = TestSpatialDevice(channels)
            val worker = SpatialWorker({ device }, { object : SpatialPacketDecoder {
                override fun decode(unit: ApapAccessUnit): SpatialDecoded {
                    val codec = if (unit.sequence == 1) SpatialCodec.APAC else SpatialCodec.AAC
                    return SpatialDecoded(codec, if (codec == SpatialCodec.APAC) 6 else 2, ShortArray(1024 * channels))
                }
                override fun discontinuity() {}
                override fun close() {}
            } }, { 1f }, { 1f }, {}, emptyMap(), startDelayNs = 0)
            try {
                worker.begin(null)
                repeat(3) { index -> worker.push(ApapAccessUnit(ApapMediaTime(index * 1024L, 48000), index,
                    ApapMetadata(cookie = aacCookie()), byteArrayOf(1))) }
                await { device.samples.size == 3 }
                assertNull(worker.multichannelOutput)
                device.position.set(512)
                await { worker.position == 512L }
                assertNull(worker.multichannelOutput)
                device.position.set(1536)
                await { worker.position == 1536L }
                assertEquals(if (channels == 6) "surround" else "downmix", worker.multichannelOutput)
                device.position.set(2560)
                await { worker.position == 2560L }
                assertNull(worker.multichannelOutput)
            } finally { worker.close() }
            assertNull(worker.multichannelOutput)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Worker did not reach expected state", condition())
    }
}
