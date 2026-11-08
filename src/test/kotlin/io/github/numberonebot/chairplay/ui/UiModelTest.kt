package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.phone.LinkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class UiModelTest {
    private fun model(wifi: Boolean = true, autoconnect: Boolean = true, autoshow: Boolean = true, setupDone: Boolean = true) =
        UiModel(wifi, autoconnect, autoshow, setupDone) { LocalTime.of(14, 3, 5) }

    @Test
    fun `the start screen opens first and says whether the app is connecting`() {
        assertEquals(UiState(Phase.STARTING, Screen.START, autoshow = true, waiting = true), model().state)
        assertEquals(Phase.IDLE, model(autoconnect = false).state.phase)
        assertEquals(Phase.SETUP, model(wifi = false).state.phase)
        assertTrue(model().state.showsUi)
    }

    // Connecting on its own, the app shows a black screen that waits for CarPlay; the settings come only when there is
    // something to do: Get started, no network, no autoconnect, or a failed attempt.
    @Test
    fun `the start waits for the connection the app makes on its own`() {
        assertTrue(model().state.waiting)
        assertFalse(model(autoconnect = false).state.waiting)
        assertFalse(model(wifi = false).state.waiting)
        assertFalse(model(setupDone = false).state.waiting)

        val failing = model()
        failing.bringUp(LinkStatus.CallingPhone)
        assertTrue(failing.state.waiting)
        failing.bringUp(LinkStatus.Failed("Bluetooth control ended TIMED_OUT", 5_000))
        assertFalse(failing.state.waiting)
        failing.bringUp(LinkStatus.Starting)
        assertFalse(failing.state.waiting)
    }

    // CarPlay covers the waiting screen without the settings showing between; a session lost from CarPlay waits again.
    @Test
    fun `the waiting screen gives way to CarPlay and comes back when the session ends`() {
        val model = model()

        model.sessionActive()
        assertEquals(Screen.CARPLAY, model.state.screen)
        model.sessionEnded()
        assertEquals(Screen.START, model.state.screen)
        assertTrue(model.state.waiting)

        model.show(Screen.SETTINGS)
        assertFalse(model.state.waiting)
        model.sessionActive()
        model.sessionEnded()
        assertFalse(model.state.waiting)
    }

    @Test
    fun `the settings end the wait when asked for, and so does Disconnect`() {
        val shown = model().apply { show(Screen.SETTINGS) }
        assertFalse(shown.state.waiting)
        val stopped = model().apply { disconnected() }
        assertFalse(stopped.state.waiting)
        // Without autoshow the session leaves the start screen to the settings, with Show CarPlay.
        val manual = model(autoshow = false).apply { sessionActive() }
        assertFalse(manual.state.waiting)
    }

    @Test
    fun `each bring-up step shows on the start screen`() {
        val model = model()

        model.bringUp(LinkStatus.CallingPhone)
        assertEquals(Phase.CALLING, model.state.phase)
        model.bringUp(LinkStatus.Joining(knownPhone = true))
        assertEquals(Phase.JOINING, model.state.phase)
        assertTrue(model.state.knownIphone)
        model.bringUp(LinkStatus.Starting)
        assertEquals(Phase.STARTING, model.state.phase)
    }

    // The reason stays on screen while the app tries again, until the iPhone is there.
    @Test
    fun `the last error stays until a session is active`() {
        val model = model()

        model.bringUp(LinkStatus.Failed("Offline MFi directory is missing or invalid", 5_000))
        assertEquals(Phase.RETRYING, model.state.phase)
        assertEquals(UiError("Offline MFi directory is missing or invalid", LocalTime.of(14, 3, 5)), model.state.error)
        model.bringUp(LinkStatus.Starting)
        model.bringUp(LinkStatus.CallingPhone)
        assertEquals("Offline MFi directory is missing or invalid", model.state.error?.message)

        model.sessionActive()
        assertEquals(Phase.ACTIVE, model.state.phase)
        assertNull(model.state.error)
    }

    @Test
    fun `with autoshow CarPlay covers the start screen as the session starts`() {
        val model = model()

        model.sessionActive()

        assertEquals(Screen.CARPLAY, model.state.screen)
        assertFalse(model.state.showsUi)
    }

    // Someone changing settings is not interrupted.
    @Test
    fun `with autoshow the settings screen stays open as the session starts`() {
        val model = model()
        model.show(Screen.SETTINGS)

        model.sessionActive()

        assertEquals(Screen.SETTINGS, model.state.screen)
    }

    @Test
    fun `without autoshow the start screen stays until Show CarPlay`() {
        val model = model(autoshow = false)

        model.sessionActive()
        assertEquals(Screen.START, model.state.screen)
        assertTrue(model.state.canShowCarPlay)

        assertTrue(model.show(Screen.CARPLAY))
        assertEquals(Screen.CARPLAY, model.state.screen)
    }

    @Test
    fun `CarPlay cannot be shown without a session`() {
        val model = model()

        assertFalse(model.show(Screen.CARPLAY))
        assertEquals(Screen.START, model.state.screen)
        assertFalse(model.state.canShowCarPlay)
    }

    @Test
    fun `the start screen returns when the session ends`() {
        val model = model()
        model.sessionActive()

        model.sessionEnded()

        assertEquals(Screen.START, model.state.screen)
        assertEquals(Phase.STARTING, model.state.phase)
    }

    @Test
    fun `the settings screen stays open when the session ends`() {
        val model = model()
        model.sessionActive()
        model.carButton()

        model.sessionEnded()

        assertEquals(Screen.SETTINGS, model.state.screen)
    }

    // The car button on the CarPlay home screen opens the settings over CarPlay, which the back button returns to.
    @Test
    fun `the car button opens the settings over CarPlay`() {
        val model = model()
        model.sessionActive()

        model.carButton()
        assertEquals(Screen.SETTINGS, model.state.screen)
        assertTrue(model.state.showsUi)

        assertTrue(model.show(Screen.CARPLAY))
        assertEquals(Screen.CARPLAY, model.state.screen)
    }

    @Test
    fun `a Wi-Fi network entered on the start screen ends the setup`() {
        val model = model(wifi = false, autoconnect = false)

        assertTrue(model.wifiSaved())

        assertEquals(Phase.IDLE, model.state.phase)
        assertFalse(model.wifiSaved())
    }

    // Disconnect: the session ends and the app waits for Connect; CarPlay's picture gives way to the screens.
    @Test
    fun `Disconnect leaves the app idle on its screens`() {
        val model = model(autoconnect = true)
        model.sessionActive()
        model.show(Screen.CARPLAY)

        model.disconnected()

        assertEquals(Phase.IDLE, model.state.phase)
        assertEquals(Screen.START, model.state.screen)
        model.connecting()
        assertEquals(Phase.STARTING, model.state.phase)
    }

    @Test
    fun `Connect starts a bring-up from idle`() {
        val model = model(autoconnect = false)

        model.connecting()

        assertEquals(Phase.STARTING, model.state.phase)
    }

    @Test
    fun `autoshow changed on the settings screen applies to the next session`() {
        val model = model(autoshow = true)
        model.setAutoshow(false)

        model.sessionActive()

        assertEquals(Screen.START, model.state.screen)
        assertFalse(model.state.autoshow)
    }

    // Each bring-up reads the Wi-Fi anew, so a saved network is pending only until the next one starts.
    @Test
    fun `changes waiting for a reconnect are clear once a bring-up starts`() {
        val model = model()
        model.sessionActive()

        model.pending(reconnect = setOf("wifi.ssid"), restart = setOf("window.width"))
        assertEquals(setOf("wifi.ssid"), model.state.reconnectPending)
        assertEquals(setOf("window.width"), model.state.restartPending)

        model.sessionEnded()
        model.bringUp(LinkStatus.Starting)
        assertEquals(emptySet<String>(), model.state.reconnectPending)
        assertEquals(setOf("window.width"), model.state.restartPending)
    }

    @Test
    fun `listeners hear every change`() {
        val model = model()
        val seen = mutableListOf<Screen>()
        model.addListener { seen += it.screen }

        model.sessionActive()
        model.carButton()

        assertEquals(listOf(Screen.CARPLAY, Screen.SETTINGS), seen)
    }
}
