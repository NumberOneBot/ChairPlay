package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.RfcommLink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MacBluetoothTest {

    @Test
    fun normalizeAddressStandardizesSeparatorsAndCasing() {
        assertEquals("AA:BB:CC:DD:EE:FF", MacBluetooth.normalizeAddress("aa-bb-cc-dd-ee-ff"))
        assertEquals("01:23:45:67:89:AB", MacBluetooth.normalizeAddress("1:23:45:67:89:ab"))
        assertEquals("AA:BB:CC:DD:EE:FF", MacBluetooth.normalizeAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun rfcommStreamReadsEofCleanlyAndChunksWrites() {
        var closedCalled = false
        val writtenBytes = ByteArrayOutputStream()
        val readPayload = byteArrayOf(1, 2, 3, 4, 5)
        var readOffset = 0

        val fakeTransport = object : MacBluetooth.NativeTransport {
            override fun read(handle: Long, buf: ByteArray, len: Int): Int {
                if (readOffset >= readPayload.size) {
                    return 0 // EOF / closed by peer
                }
                val available = readPayload.size - readOffset
                val toCopy = minOf(len, available)
                System.arraycopy(readPayload, readOffset, buf, 0, toCopy)
                readOffset += toCopy
                return toCopy
            }

            override fun write(handle: Long, buf: ByteArray, len: Int): Int {
                assertTrue("Write chunk size must not exceed MTU (1024 bytes)", len <= 1024)
                writtenBytes.write(buf, 0, len)
                return len
            }

            override fun close(handle: Long) {
                closedCalled = true
            }
        }

        val link: RfcommLink = MacBluetooth.createRfcommLink(42L, fakeTransport, mtu = 1024)

        // Read test
        val inBuf = ByteArray(10)
        val n1 = link.input.read(inBuf, 0, 5)
        assertEquals(5, n1)
        assertArrayEquals(readPayload, inBuf.copyOf(5))

        val n2 = link.input.read(inBuf, 0, 5)
        assertEquals(-1, n2.toLong())

        // Write chunk test (> 1024 bytes)
        val largePayload = ByteArray(2500) { (it % 127).toByte() }
        link.output.write(largePayload)
        assertArrayEquals(largePayload, writtenBytes.toByteArray())

        // Close idempotency
        assertFalse(closedCalled)
        link.close()
        assertTrue(closedCalled)
        closedCalled = false
        link.close()
        assertFalse(closedCalled)
    }

    @Test
    fun liveSmokeTestLoadsNativeLibrary() {
        val os = System.getProperty("os.name").orEmpty()
        assumeTrue("Mac only", os.startsWith("Mac"))

        val errBuf = ByteArray(256)
        val addrBuf = ByteArray(32)
        val res = MacNative.INSTANCE.cp_bt_radio_address(addrBuf, addrBuf.size, errBuf, errBuf.size)
        // If Bluetooth hardware is missing or disabled, it may return -1, but it MUST NOT throw UnsatisfiedLinkError
        assertTrue(res == 0 || res == -1)
    }
}
