package io.github.numberonebot.chairplay.gps.setup

import io.github.numberonebot.chairplay.gps.AppleComponents
import io.github.numberonebot.chairplay.gps.DeveloperModeStatus
import io.github.numberonebot.chairplay.gps.GpsReason
import io.github.numberonebot.chairplay.gps.SetupStep
import io.github.numberonebot.chairplay.gps.UsbErrorCode
import io.github.numberonebot.chairplay.gps.UsbHelper
import io.github.numberonebot.chairplay.gps.UsbReadiness
import io.github.numberonebot.chairplay.gps.UsbResult

/** A quick look at the prepared phone, without a wizard: can it take positions now. Sends no position. */
internal class QuickReadiness(private val helper: UsbHelper) {
    fun check(preparedUdid: String?): UsbReadiness {
        if (preparedUdid == null) return NotReady(GpsReason.USB_SETUP_NEEDED, SetupStep.USB)
        when (helper.components()) {
            AppleComponents.MISSING -> return NotReady(GpsReason.USB_SETUP_NEEDED, SetupStep.USB)
            // Installed but not answering: the wizard asks to open Apple Devices.
            AppleComponents.NOT_RUNNING -> return NotReady(GpsReason.USB_NOT_READY, SetupStep.USB)
            else -> {}
        }
        val list = when (val r = helper.devices()) {
            is UsbResult.Ok -> r.value
            is UsbResult.Failed -> return when (r.code) {
                UsbErrorCode.USBMUX_UNAVAILABLE -> NotReady(GpsReason.USB_NOT_READY, SetupStep.USB)
                UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> NotReady(GpsReason.USB_NOT_CONNECTED, SetupStep.USB)
                UsbErrorCode.DRIVERS_MISSING -> NotReady(GpsReason.USB_SETUP_NEEDED, SetupStep.USB)
                UsbErrorCode.LOCKED -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TRUST)
                else -> NotReady(GpsReason.USB_HELPER_FAILED, null)
            }
        }
        val device = list.firstOrNull { it.udid == preparedUdid }
            ?: return NotReady(GpsReason.USB_NOT_CONNECTED, SetupStep.USB)
        val p = when (val r = helper.probe(device.udid)) {
            is UsbResult.Ok -> r.value
            is UsbResult.Failed -> return when (r.code) {
                UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST -> NotReady(GpsReason.USB_NOT_CONNECTED, SetupStep.USB)
                UsbErrorCode.NOT_TRUSTED, UsbErrorCode.LOCKED -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TRUST)
                else -> NotReady(GpsReason.USB_HELPER_FAILED, null)
            }
        }
        return when {
            // Only a definite yes is ready; a locked phone that is trusted and whose service answers still is.
            p.trusted != true -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TRUST)
            p.developerMode == DeveloperModeStatus.OFF -> NotReady(GpsReason.USB_NOT_READY, SetupStep.DEVELOPER_MODE)
            p.imageMounted != true || p.serviceReady != true -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TOOLS)
            else -> UsbReadiness.Ready(device)
        }
    }

    private fun NotReady(reason: GpsReason, step: SetupStep?) = UsbReadiness.NotReady(reason, step)
}
