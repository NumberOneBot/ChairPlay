package io.github.numberonebot.chairplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Track playback's command over HTTP: the action reaches the app's track commands, answered as drive mode's. */
class AppUiTrackTest : UiControllerFixture() {
    private val asked = mutableListOf<String>()
    private var answer = true

    private fun post(body: Map<String, Any?>) =
        postRoute(AppUiApi(controller(), track = { action -> answer.also { asked += action } }) { null }, "track", body)

    @Test
    fun `an action reaches the track commands and answers 200`() {
        assertEquals(200, post(mapOf("action" to "play")).first)
        assertEquals(200, post(mapOf("action" to "open")).first)
        assertEquals(listOf("play", "open"), asked)
    }

    @Test
    fun `an action refused answers 409`() {
        answer = false
        assertEquals(409, post(mapOf("action" to "pause")).first)
    }

    @Test
    fun `a post without an action answers 400 and asks nothing`() {
        assertEquals(400, post(emptyMap()).first)
        assertEquals(400, post(mapOf("action" to 3)).first)
        assertEquals(emptyList<String>(), asked)
    }
}
