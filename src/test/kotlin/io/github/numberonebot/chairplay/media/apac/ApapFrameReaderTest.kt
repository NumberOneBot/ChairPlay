package io.github.numberonebot.chairplay.media.apac

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Properties

class ApapFrameReaderTest {
    private val vectors = Properties().apply {
        ApapFrameReaderTest::class.java.getResourceAsStream("/apap/original-chacha.properties")!!.use(::load)
    }
    private fun bytes(name: String) = vectors.getProperty(name).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun `both AAD variants match independent original ChaCha vectors across fragmented TCP reads`() {
        val input = object : ByteArrayInputStream(bytes("audio") + bytes("extension")) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 3))
        }
        ApapFrameReader(input, bytes("key")).use { reader ->
            for (extension in listOf(false, true)) {
                val packet = reader.read()!!
                assertArrayEquals(bytes("payload"), packet.plaintext)
                assertEquals(extension, packet.extension)
                assertEquals(0x0102030405060708L, packet.time.value)
                assertEquals(48000, packet.time.timescale)
                assertEquals(0xfffffe, packet.sequence)
            }
            assertNull(reader.read())
        }
    }

    @Test fun `modified ciphertext tag nonce and authenticated time never reach the caller`() {
        val good = bytes("extension")
        for (offset in listOf(4, 12, 19, good.size - 9, good.lastIndex)) {
            val bad = good.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            ApapFrameReader(ByteArrayInputStream(bad + good), bytes("key")).use { reader ->
                assertThrows(ApapAuthenticationException::class.java) { reader.read() }
                assertArrayEquals(bytes("payload"), reader.read()!!.plaintext)
            }
        }
    }

    @Test fun `oversized unsigned lengths are rejected before reading or allocating a body`() {
        for (length in listOf(0, 42, 1_048_577, -1, Int.MIN_VALUE)) {
            val prefix = ByteArrayInputStream(ByteBuffer.allocate(4).putInt(length).array())
            val input = object : InputStream() {
                override fun read(): Int = prefix.read().also { check(it != -1) { "Read beyond length prefix" } }
            }
            ApapFrameReader(input, bytes("key")).use { reader ->
                assertThrows(IOException::class.java) { reader.read() }
            }
        }
    }

    @Test fun `clean EOF differs from truncated prefix and truncated packet`() {
        ApapFrameReader(ByteArrayInputStream(byteArrayOf()), bytes("key")).use { assertNull(it.read()) }
        for (size in listOf(1, 2, 3, 4, bytes("audio").size - 1)) {
            ApapFrameReader(ByteArrayInputStream(bytes("audio").copyOf(size)), bytes("key")).use { reader ->
                assertThrows(EOFException::class.java) { reader.read() }
            }
        }
    }

    @Test fun `reader owns an independent copy of the session key`() {
        val key = bytes("key")
        ApapFrameReader(ByteArrayInputStream(bytes("audio")), key).use { reader ->
            key.fill(0)
            assertArrayEquals(bytes("payload"), reader.read()!!.plaintext)
        }
    }

    @Test fun `invalid key and impossible frame budget are rejected before reading`() {
        val input = ByteArrayInputStream(bytes("audio"))
        assertThrows(IllegalArgumentException::class.java) { ApapFrameReader(input, ByteArray(31)) }
        assertThrows(IllegalArgumentException::class.java) { ApapFrameReader(input, bytes("key"), 42) }
        assertThrows(IllegalArgumentException::class.java) { ApapFrameReader(input, bytes("key"), Int.MAX_VALUE) }
        assertEquals(bytes("audio").size, input.available())
    }
}
