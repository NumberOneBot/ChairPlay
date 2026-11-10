package io.github.numberonebot.chairplay.gps.usb

import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperConnectionTest {
    private fun started(helper: FakeHelper): HelperConnection =
        HelperConnection(helper, "test").also { assertEquals(UsbResult.Ok(Unit), it.start(2_000)) }

    @Test
    fun `the client answers the helper's hello with its own`() {
        val helper = FakeHelper()
        started(helper)
        assertEquals(listOf("hello"), helper.ops())
        assertEquals(2L, helper.requests[0]["protocol"])
    }

    @Test
    fun `a helper of another protocol is refused`() {
        val helper = FakeHelper(hello = false)
        helper.say(mapOf("type" to "hello", "protocol" to 1))
        val result = HelperConnection(helper, "test").start(2_000)
        assertEquals(UsbErrorCode.PROTOCOL, (result as UsbResult.Failed).code)
        assertTrue(helper.killed || helper.inputClosed())
    }

    @Test
    fun `a helper that never says hello fails in bounded time`() {
        val helper = FakeHelper(hello = false)
        val started = System.nanoTime()
        val result = HelperConnection(helper, "test").start(200)
        assertEquals(UsbErrorCode.HELPER_FAILED, (result as UsbResult.Failed).code)
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2))
    }

    @Test
    fun `replies are matched by id, so a slow request does not hold a quick one`() {
        val slow = CountDownLatch(1)
        lateinit var slowRequest: Map<String, Any?>
        val helper = FakeHelper { request ->
            when (request["op"]) {
                "prepare" -> {
                    slowRequest = request
                    slow.countDown()
                }
                "discover" -> reply(request, mapOf("devices" to emptyList<Any>()))
                else -> standard(request)
            }
        }
        val connection = started(helper)
        val prepare = connection.send("prepare", mapOf("udid" to "U"))
        assertTrue(slow.await(2, TimeUnit.SECONDS))
        val discover = connection.call("discover", emptyMap(), 1_000)
        assertTrue(discover is UsbResult.Ok)
        helper.reply(slowRequest, mapOf("packageId" to null))
        assertTrue(prepare.await(1_000) is UsbResult.Ok)
    }

    @Test
    fun `a request without a reply times out and its late reply is ignored`() {
        lateinit var held: Map<String, Any?>
        val helper = FakeHelper { request -> if (!standard(request)) held = request }
        val connection = started(helper)
        val result = connection.call("probe", mapOf("udid" to "U"), 100)
        assertEquals(UsbErrorCode.TIMEOUT, (result as UsbResult.Failed).code)
        helper.reply(held)
        assertTrue(connection.call("ping", emptyMap(), 1_000) is UsbResult.Ok)
    }

    @Test
    fun `an error reply carries its code`() {
        val helper = FakeHelper { request -> if (!standard(request)) fail(request, "several-devices") }
        val result = started(helper).call("discover", emptyMap(), 1_000)
        assertEquals(UsbErrorCode.SEVERAL_DEVICES, (result as UsbResult.Failed).code)
    }

    @Test
    fun `an unknown error code is a helper failure`() {
        val helper = FakeHelper { request -> if (!standard(request)) fail(request, "made-up") }
        assertEquals(UsbErrorCode.HELPER_FAILED, (started(helper).call("discover", emptyMap(), 1_000) as UsbResult.Failed).code)
    }

    @Test
    fun `a helper that ends fails what waits and tells once`() {
        val ends = AtomicInteger()
        val helper = FakeHelper { request -> if (!standard(request)) exit() }
        val connection = started(helper)
        connection.onEnd = { ends.incrementAndGet() }
        val result = connection.call("probe", mapOf("udid" to "U"), 2_000)
        assertEquals(UsbErrorCode.HELPER_FAILED, (result as UsbResult.Failed).code)
        eventually { ends.get() == 1 }
        assertEquals(UsbErrorCode.HELPER_FAILED, (connection.call("ping", emptyMap(), 500) as UsbResult.Failed).code)
        Thread.sleep(50)
        assertEquals(1, ends.get())
    }

    @Test
    fun `a line that is not the protocol ends the helper`() {
        val helper = FakeHelper { request -> if (!standard(request)) sayRaw("Traceback (most recent call last):") }
        val connection = started(helper)
        val result = connection.call("probe", mapOf("udid" to "U"), 2_000)
        assertEquals(UsbErrorCode.PROTOCOL, (result as UsbResult.Failed).code)
        eventually { helper.killed }
    }

    @Test
    fun `progress events reach their request and other events the listener`() {
        val helper = FakeHelper { request ->
            if (!standard(request)) {
                event("progress", mapOf("id" to request["id"], "stage" to "download", "receivedBytes" to 5))
                event("lost", mapOf("session" to "s1", "generation" to 1, "code" to "link-lost"))
                reply(request)
            }
        }
        val connection = started(helper)
        val others = CopyOnWriteArrayList<Map<String, Any?>>()
        connection.onEvent = { others += it }
        val progress = CopyOnWriteArrayList<Map<String, Any?>>()
        assertTrue(connection.send("prepare", mapOf("udid" to "U")) { progress += it }.await(1_000) is UsbResult.Ok)
        assertEquals(listOf(5L), progress.map { it["receivedBytes"] })
        eventually { others.size == 1 }
        assertEquals("lost", others[0]["event"])
    }

    @Test
    fun `close ends the input and waits for the helper, killing it when it stays`() {
        val polite = FakeHelper()
        started(polite).close(1_000)
        assertTrue(polite.exited)
        assertTrue(!polite.killed)
        val stubborn = FakeHelper()
        stubborn.onInputEnd = {}
        val started = System.nanoTime()
        started(stubborn).close(100)
        assertTrue(stubborn.killed)
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2))
    }
}
