package io.github.numberonebot.chairplay.gps

/** The output as the page reads it, from the coordinator's state; called under the flows' lock. */
internal object GpsOutputs {
    fun of(flows: GpsFlows, operation: GpsOperation?, notices: List<GpsNotice>): GpsOutput {
        val saved = flows.store.current()
        val channel = flows.channel(flows.selected)
        val running = channel.running
        val prepared = saved.usbDevice != null
        val state = channel.state()
        val reason = when {
            running -> null
            flows.selected == GpsMethod.USB && !prepared -> GpsReason.USB_SETUP_NEEDED
            else -> (if (flows.enabled) flows.reason else null) ?: state.reason
        }
        val availability = when {
            operation != null -> Availability.PREPARING
            running -> Availability.READY
            reason == GpsReason.USB_HELPER_FAILED -> Availability.ERROR
            reason != null -> Availability.UNAVAILABLE
            else -> Availability.READY
        }
        return GpsOutput(
            selected = flows.selected,
            enabledIntent = flows.enabled,
            activeMethod = listOf(flows.carplay, flows.usb).firstOrNull { it.running }?.method,
            running = running,
            availability = availability,
            reason = reason,
            usbPrepared = prepared,
            device = flows.usb.state().device,
            successfulSends = channel.sends,
            rateHz = if (flows.selected == GpsMethod.USB) saved.usbRate else saved.rate,
            generation = flows.generation,
            operation = operation,
            cleanup = flows.cleanup,
            notices = notices,
        )
    }
}
