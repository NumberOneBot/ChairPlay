package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The location component (22) offers the sentences ChairPlay sends: GGA (17) and RMC (18) always, wheel speed
 * (20, $PASCD) when turned on, and never satellites in view (19, $GPGSV). Only the Wi-Fi tunnel offers it.
 */
class Iap2LocationIdentificationTest {
    private val config = Iap2IdentificationConfig(
        name = "ChairPlay",
        modelIdentifier = "ChairPlay",
        manufacturer = "ChairPlay",
        serialNumber = "test",
        firmwareVersion = "1",
        hardwareVersion = "1",
        carPlayUsbInterfaceNumber = 3,
        locationInformationEnabled = true,
        vehicleSpeedEnabled = true,
    )
    private val wireless = Iap2WirelessIdentification("AA:BB:CC:DD:EE:FF", "ChairPlay")

    private fun locationFields(config: Iap2IdentificationConfig): List<Int>? {
        val identification = Iap2ParameterList.parse(Iap2IdentificationClient.identificationInformation(config).payload).asList()
        val component = identification.singleOrNull { it.id == 22 } ?: return null
        return Iap2ParameterList.parse(component.payload).asList().map { it.id }
    }

    @Test
    fun `the location component offers wheel speed when turned on`() {
        assertEquals(listOf(0, 1, 17, 18, 20), locationFields(config.forWirelessLink(Iap2WirelessLinkRole.RUNTIME_TUNNEL, wireless)))
        assertEquals(listOf(0, 1, 17, 18), locationFields(config.copy(vehicleSpeedEnabled = false)))
    }

    @Test
    fun `the Bluetooth link offers no location`() {
        val bluetooth = config.forWirelessLink(Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP, wireless)
        assertEquals(null, locationFields(bluetooth))
        assertTrue(!bluetooth.vehicleSpeedEnabled)
    }
}
