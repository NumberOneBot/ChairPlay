package io.github.numberonebot.chairplay.media.apac

import org.junit.Assume.assumeNotNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

internal data class ApacTestFixture(val cookie: ByteArray, val packets: List<ByteArray>, val pcm: FloatArray)

internal fun apacTestBackend(): ApacNativeLibrary {
    val path = System.getProperty("chairplay.apac.testLibrary")
    assumeNotNull(path)
    return ApacNativeLibrary.load(Path.of(path))
}

internal fun apacTestFixture(): ApacTestFixture {
    val path = System.getProperty("chairplay.apac.testFixture")
    assumeNotNull(path)
    val buffer = ByteBuffer.wrap(Files.readAllBytes(Path.of(path))).order(ByteOrder.LITTLE_ENDIAN)
    check(String(ByteArray(8).also(buffer::get), Charsets.US_ASCII) == "APACFRM1")
    val count = buffer.int
    val channels = buffer.int
    val frames = buffer.int
    val cookie = ByteArray(buffer.int).also(buffer::get)
    val lengths = IntArray(count) { buffer.int }
    val packets = lengths.map { length -> ByteArray(length).also(buffer::get) }
    val pcm = FloatArray(count * channels * frames) { buffer.float }
    check(!buffer.hasRemaining())
    return ApacTestFixture(cookie, packets, pcm)
}
