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
        if (helper.components() == AppleComponents.MISSING) return NotReady(GpsReason.USB_SETUP_NEEDED, SetupStep.USB)
        val list = when (val r = helper.devices()) {
            is UsbResult.Ok -> r.value
            is UsbResult.Failed -> return when (r.code) {
                UsbErrorCode.NO_DEVICE, UsbErrorCode.LINK_LOST, UsbErrorCode.USBMUX_UNAVAILABLE ->
                    NotReady(GpsReason.USB_NOT_CONNECTED, SetupStep.USB)
                UsbErrorCode.SEVERAL_DEVICES -> NotReady(GpsReason.USB_SEVERAL, SetupStep.USB)
                UsbErrorCode.DRIVERS_MISSING -> NotReady(GpsReason.USB_SETUP_NEEDED, SetupStep.USB)
                UsbErrorCode.LOCKED -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TRUST)
                else -> NotReady(GpsReason.USB_HELPER_FAILED, null)
            }
        }
        if (list.size > 1) return NotReady(GpsReason.USB_SEVERAL, SetupStep.USB)
        val device = list.firstOrNull()?.takeIf { it.udid == preparedUdid }
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
            p.trusted == false || p.locked == true -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TRUST)
            p.developerMode == DeveloperModeStatus.OFF -> NotReady(GpsReason.USB_NOT_READY, SetupStep.DEVELOPER_MODE)
            p.imageMounted == false || p.serviceReady == false -> NotReady(GpsReason.USB_NOT_READY, SetupStep.TOOLS)
            else -> UsbReadiness.Ready(device)
        }
    }

    private fun NotReady(reason: GpsReason, step: SetupStep?) = UsbReadiness.NotReady(reason, step)
}
