package io.github.numberonebot.chairplay.bridge

import io.github.numberonebot.chairplay.config.AreaRect
import io.github.numberonebot.chairplay.config.WindowSize
import io.github.numberonebot.chairplay.platform.FakeGameBridge
import io.github.numberonebot.chairplay.platform.FakePlatform
import io.github.numberonebot.chairplay.phone.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** A [FrameOutput] fed by the platform's game bridge. */
class FrameOutputBridgeTest {
    @Test fun `a bridge without its library says why and publishes nothing`() {
        val sink = FakeFrameSink(openError = "chairplay_bridge.dll not found")
        val platform = FakePlatform(bridge = FakeGameBridge(frames = sink))
        val logs = mutableListOf<String>()
        val output = FrameOutput(platform.bridge.frames, { WindowSize(64, 64) }, { AreaRect(0, 0, 64, 64) }, logs::add, { 0L },
            worker = { Thread {} })
        output.enabled = true
        repeat(3) {
            output.frame(ScreenKind.MAIN, testFrame())
            output.pass(0)
        }
        assertEquals(1, logs.count { "chairplay_bridge.dll not found" in it })
        assertEquals(0, sink.count("publish"))
    }
}
