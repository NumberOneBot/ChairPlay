package io.github.numberonebot.chairplay.ui

import io.github.numberonebot.chairplay.gps.CommandOutcome
import io.github.numberonebot.chairplay.gps.GpsCommands
import io.github.numberonebot.chairplay.gps.GpsMethod
import io.github.numberonebot.chairplay.gps.GpsOutput
import io.github.numberonebot.chairplay.gps.SetupAction
import io.github.numberonebot.chairplay.gps.SetupIntent
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.UsbSetupView
import org.junit.Assert.assertEquals
import org.junit.Test

/** The GPS output's commands over HTTP (A0 §3): what each post asks of the coordinator, and its status. */
class AppUiGpsTest : UiControllerFixture() {
    private val asked = mutableListOf<String>()
    private var outcome = CommandOutcome.ACCEPTED

    private val commands = object : GpsCommands {
        override fun output(): GpsOutput = throw UnsupportedOperationException()
        override fun usbSetup(): UsbSetupView = UsbSetupView.closed()
        override fun selectMethod(method: GpsMethod, generation: Long) = outcome.also { asked += "method ${method.key} $generation" }
        override fun setEnabled(enabled: Boolean) = outcome.also { asked += "enabled $enabled" }
        override fun openUsbSetup(intent: SetupIntent) = outcome.also { asked += "open ${intent.key}" }
        override fun usbSetupAction(operationId: Long, action: SetupAction, step: SetupStep?) =
            outcome.also { asked += "act $operationId ${action.key} ${step?.key}" }
    }

    private fun post(route: String, body: Map<String, Any?>) = postRoute(AppUiApi(controller(), gps = { commands }) { null }, route, body)

    @Test
    fun `the posts reach the coordinator and answer its outcome`() {
        assertEquals(200 to mapOf("outcome" to "accepted"), post("gps-method", mapOf("method" to "usb", "generation" to 3)))
        assertEquals(200, post("gps-enabled", mapOf("enabled" to true)).first)
        assertEquals(200, post("usb-setup", mapOf("action" to "open", "intent" to "enable")).first)
        assertEquals(200, post("usb-setup", mapOf("action" to "retry", "operationId" to 7, "step" to "tools")).first)
        assertEquals(200, post("usb-setup", mapOf("action" to "cancel", "operationId" to 7)).first)
        assertEquals(listOf("method usb 3", "enabled true", "open enable", "act 7 retry tools", "act 7 cancel null"), asked)
    }

    @Test
    fun `busy and stale answer 409, and a malformed post 400 without reaching the coordinator`() {
        outcome = CommandOutcome.BUSY
        assertEquals(409 to mapOf("outcome" to "busy"), post("gps-enabled", mapOf("enabled" to false)))
        outcome = CommandOutcome.SETUP_OPENED
        assertEquals(200, post("gps-enabled", mapOf("enabled" to true)).first)
        asked.clear()

        for ((route, body) in listOf(
            "gps-method" to mapOf("method" to "bluetooth", "generation" to 1),
            "gps-method" to mapOf("method" to "usb"),
            "gps-enabled" to mapOf("enabled" to "yes"),
            "usb-setup" to mapOf("action" to "open", "intent" to "later"),
            "usb-setup" to mapOf("action" to "retry", "operationId" to 7, "step" to "everything"),
            "usb-setup" to mapOf("action" to "enable"),
            "usb-setup" to mapOf("action" to "dance", "operationId" to 7),
        )) assertEquals("$route $body", 400 to mapOf("outcome" to "invalid"), post(route, body))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `without a coordinator the posts are refused`() {
        val api = AppUiApi(controller()) { null }
        assertEquals(409, postRoute(api, "gps-enabled", mapOf("enabled" to true)).first)
    }
}
