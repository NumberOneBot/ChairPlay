package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2ArtworkTransfer
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import com.shilapi.xcertplay.transport.Iap2WirelessControlClient
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CarPlayDataLogTest {
    private val dir: Path = Files.createTempDirectory("chairplay-data-log").resolve("logs")
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:03:07.412Z"), ZoneOffset.ofHours(2))
    private val messages = ArrayList<String>()
    private val log = CarPlayDataLog(dir, clock) { messages += it }

    private fun lines(): List<String> = Files.readAllLines(log.file!!, Charsets.UTF_8)

    @Test
    fun `a decoded frame is one JSON line in a file named after the time`() {
        log.frame(DataLink.WIFI, Iap2Messages.buildRaw(0x5201) {
            u8(1, 1)
            string(3, "Main \"Street\"")
            u32(10, 150)
            u16List(13, listOf(3, 4))
        })
        log.close()

        assertEquals(dir.resolve("carplay-data-2026-10-22-140307.jsonl"), log.file)
        assertEquals(
            listOf(
                """{"time":"2026-10-22T14:03:07.412+02:00","link":"wifi","id":"0x5201","name":"RouteGuidanceUpdate",""" +
                    """"fields":{"routeGuidanceState":"ROUTE_SET","currentRoadName":"Main \"Street\"",""" +
                    """"distanceToNextManeuver":150,"currentManeuverList":[3,4]}}""",
            ),
            lines(),
        )
        assertEquals(listOf("CarPlay data log: ${log.file}"), messages)
    }

    @Test
    fun `groups nest and unknown parameters are kept as hex`() {
        log.frame(DataLink.BLUETOOTH, Iap2Messages.buildRaw(0x5001) {
            group(0) {
                string(1, "Song")
                raw(40, byteArrayOf(1))
            }
            group(1) { u8(0, 7) }
            raw(99, byteArrayOf(0x0a, 0x0b))
        })

        assertEquals(
            """{"time":"2026-10-22T14:03:07.412+02:00","link":"bluetooth","id":"0x5001","name":"NowPlayingUpdate",""" +
                """"fields":{"mediaItem":{"title":"Song","unknown":["40:01"]},"playback":{"playbackStatus":7}},""" +
                """"unknown":["99:0a0b"]}""",
            lines().single(),
        )
    }

    @Test
    fun `a message that cannot be decoded is written as hex`() {
        log.frame(DataLink.WIFI, Iap2Frame(0x4171, byteArrayOf(0, 6, 0, 0, 0x41, 0)))
        log.frame(DataLink.WIFI, Iap2Frame(0x5201, byteArrayOf(0, 9, 0)))

        assertEquals(
            listOf(
                """{"time":"2026-10-22T14:03:07.412+02:00","link":"wifi","id":"0x4171","name":"ListUpdate","hex":"000600004100"}""",
                """{"time":"2026-10-22T14:03:07.412+02:00","link":"wifi","id":"0x5201","name":"RouteGuidanceUpdate","hex":"000900"}""",
            ),
            lines(),
        )
    }

    @Test
    fun `lane guidance keeps its bytes beside the fields`() {
        log.frame(DataLink.WIFI, Iap2Messages.buildRaw(0x5204) { u16(1, 2) })

        assertEquals(
            """{"time":"2026-10-22T14:03:07.412+02:00","link":"wifi","id":"0x5204","name":"LaneGuidanceInfoUpdate",""" +
                """"fields":{"laneGuidanceIndex":2},"hex":"000600010002"}""",
            lines().single(),
        )
    }

    @Test
    fun `artwork is logged by its size`() {
        log.artwork(DataLink.WIFI, Iap2ArtworkTransfer(7, byteArrayOf(1, 2, 3)))

        assertEquals(
            """{"time":"2026-10-22T14:03:07.412+02:00","link":"wifi","name":"NowPlayingArtwork","fields":{"transferId":7,"bytes":3}}""",
            lines().single(),
        )
    }

    @Test
    fun `text is escaped as JSON and written as UTF-8`() {
        log.frame(DataLink.WIFI, Iap2Messages.buildRaw(0x5201) { string(3, "Line\nbreak\t\u0001 é\\") })

        assertTrue(lines().single(), lines().single().contains(""""currentRoadName":"Line\nbreak\t\u0001 é\\""""))
    }

    // The passphrase travels in 0x5703 and 0x4301, the MFi exchange in 0xAAxx: none of it may reach the file.
    @Test
    fun `secrets are never written`() {
        val endpoint = Iap2WirelessCarPlayEndpoint(
            ssid = "Home", passphrase = "secret123", channel = 36, security = Iap2WirelessSecurity.WPA_WPA2,
            ipAddresses = listOf("192.168.1.20"), airPlayPort = 7000, deviceIdentifier = "02:11:22:33:44:55",
            publicKey = "0123ab", sourceVersion = "950.7.1",
        )
        log.frame(DataLink.BLUETOOTH, Iap2WirelessControlClient.accessoryWiFiConfiguration(endpoint))
        log.frame(DataLink.WIFI, Iap2WirelessControlClient.carPlayStartSession(endpoint))
        log.frame(DataLink.BLUETOOTH, Iap2Messages.buildRaw(0xaa02) { bytes(0, byteArrayOf(1, 2, 3)) })
        log.close()

        assertNull(log.file)
        assertFalse(Files.exists(dir))
    }

    @Test
    fun `a log that cannot write says so once and stops`() {
        Files.createDirectories(dir.parent)
        Files.writeString(dir, "a file where the folder should be")
        val frame = Iap2Messages.buildRaw(0xae01) { u16(6, 100) }

        log.frame(DataLink.WIFI, frame)
        log.frame(DataLink.WIFI, frame)

        assertEquals(1, messages.size)
        assertTrue(messages.single(), messages.single().startsWith("CarPlay data log stopped: "))
    }
}
