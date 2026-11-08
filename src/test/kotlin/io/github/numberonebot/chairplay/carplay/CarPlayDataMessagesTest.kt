package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.message.Iap2ControlMessages
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import com.shilapi.xcertplay.transport.Iap2WirelessControlClient
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CarPlayDataMessages: now playing, call, communications and power updates, what stays raw, and which messages are data. */
class CarPlayDataMessagesTest {
    @Test
    fun `now playing update decodes its two groups`() {
        val frame = Iap2Messages.buildRaw(0x5001) {
            group(0) {
                string(1, "Song")
                u32(4, 215_000)
                string(6, "Album")
                u16(7, 4)
                string(12, "Artist")
                string(14, "Band")
                u8(26, 7)
            }
            group(1) {
                u8(0, 2)
                u32(1, 1_000)
                u8(5, 1)
                u8(6, 2)
                string(7, "Music")
            }
        }

        val values = CarPlayDataMessages.decode(frame)!!.values
        val item = values["mediaItem"] as DecodedFields
        val playback = values["playback"] as DecodedFields

        assertEquals(
            mapOf(
                "title" to "Song", "playbackDurationMs" to 215_000L, "albumTitle" to "Album", "albumTrackNumber" to 4,
                "artist" to "Artist", "albumArtist" to "Band", "artworkFileTransferId" to 7,
            ),
            item.values,
        )
        assertEquals(
            mapOf(
                "playbackStatus" to CodedValue(2, "PAUSED"), "elapsedTimeMs" to 1_000L,
                "shuffleMode" to CodedValue(1, "SONGS"), "repeatMode" to CodedValue(2, "ALL"), "appName" to "Music",
            ),
            playback.values,
        )
    }

    @Test
    fun `call state, communications and power updates decode`() {
        val call = CarPlayDataMessages.decode(Iap2Messages.buildRaw(0x4155) {
            string(0, "+15551234567")
            string(1, "Alice")
            u8(2, 2)
            u8(3, 1)
            string(4, "call-1")
            string(7, "mobile")
            u8(8, 1)
        })!!.values
        assertEquals(
            mapOf(
                "remoteId" to "+15551234567", "displayName" to "Alice", "status" to CodedValue(2, "RINGING"),
                "direction" to CodedValue(1, "INCOMING"), "callUuid" to "call-1", "label" to "mobile",
                "service" to CodedValue(1, "TELEPHONY"),
            ),
            call,
        )

        val communications = CarPlayDataMessages.decode(Iap2Messages.buildRaw(0x4158) {
            u8(0, 4)
            u8(1, 4)
            // As an iPhone 15 on iOS 18.6 sends it: an empty name, and the carrier in parameter 4.
            string(3, "")
            string(4, "UA-KYIVSTAR")
            bool(5, true)
            u8(9, 1)
        })!!.values
        assertEquals(
            mapOf(
                "signalStrength" to 4, "registrationStatus" to CodedValue(4, "REGISTERED_HOME"), "carrierName" to "",
                "carrierId" to "UA-KYIVSTAR", "telephonyEnabled" to true, "currentCallCount" to 1,
            ),
            communications,
        )

        val power = CarPlayDataMessages.decode(Iap2Messages.buildRaw(0xae01) {
            bool(4, false)
            u8(5, 1)
            u16(6, 37)
        })!!.values
        assertEquals(
            mapOf("isExternalChargerConnected" to false, "batteryChargingState" to CodedValue(1, "CHARGING"), "batteryChargeLevel" to 37),
            power,
        )
    }

    // A parameter the schema does not know, or one of the wrong size, stays as bytes for the data log.
    @Test
    fun `unknown and malformed parameters are kept raw`() {
        val fields = CarPlayDataMessages.decode(Iap2Messages.buildRaw(0xae01) {
            u8(6, 3)
            u8(5, 9)
            raw(99, byteArrayOf(1, 2))
        })!!

        assertEquals(mapOf("batteryChargingState" to CodedValue(9, null)), fields.values)
        assertEquals(listOf(6 to listOf(3), 99 to listOf(1, 2)), fields.unknown.map { it.id to it.payload.map(Byte::toInt) })
    }

    @Test
    fun `strings without their terminating NUL still decode`() {
        val frame = Iap2Messages.buildRaw(0x5201) { raw(3, "Elm".toByteArray()) }

        assertEquals("Elm", CarPlayDataMessages.decode(frame)!!.values["currentRoadName"])
    }

    @Test
    fun `a data message without a schema is named and left undecoded`() {
        val frame = Iap2Frame(0x4171, Iap2Messages.buildRaw(0x4171) { string(0, "x") }.payload)

        assertTrue(CarPlayDataMessages.isData(0x4171))
        assertEquals("ListUpdate", CarPlayDataMessages.name(0x4171))
        assertNull(CarPlayDataMessages.decode(frame))
    }

    @Test
    fun `a body that is not a parameter list is left undecoded`() {
        assertNull(CarPlayDataMessages.decode(Iap2Frame(0x5201, byteArrayOf(0, 9, 0))))
    }

    // Nothing that carries the Wi-Fi passphrase, a certificate, a challenge or the identification is data.
    @Test
    fun `authentication, identification and Wi-Fi messages are not data`() {
        val endpoint = Iap2WirelessCarPlayEndpoint(
            ssid = "Home", passphrase = "secret123", channel = 36, security = Iap2WirelessSecurity.WPA_WPA2,
            ipAddresses = listOf("192.168.1.20"), airPlayPort = 7000, deviceIdentifier = "02:11:22:33:44:55",
            publicKey = "0123ab", sourceVersion = "950.7.1",
        )
        val secrets = listOf(
            Iap2WirelessControlClient.accessoryWiFiConfiguration(endpoint).messageId,
            Iap2WirelessControlClient.carPlayStartSession(endpoint).messageId,
            0x5701, 0x1d01, 0xaa00, 0xaa01, 0xaa02, 0xaa03, 0xaa04, 0xaa05,
        )
        for (id in secrets) assertFalse("0x${id.toString(16)}", CarPlayDataMessages.isData(id))
        for (id in listOf(0x5001, 0x5201, 0x5202, 0x5204, 0x4155, 0x4158, 0x4171, 0xae01)) {
            assertTrue("0x${id.toString(16)}", CarPlayDataMessages.isData(id))
        }
        // The accessory's own requests never arrive, but are no secret either.
        assertTrue(CarPlayDataMessages.isData(Iap2ControlMessages.startNowPlayingUpdates().messageId))
    }
}
