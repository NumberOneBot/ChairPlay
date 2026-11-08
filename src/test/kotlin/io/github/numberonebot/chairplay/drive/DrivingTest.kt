package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingTest {
    private val kyiv = GeoPoint(50.4501, 30.5234)
    private val lviv = GeoPoint(49.8397, 24.0297)
    private var now = 0L

    private val placeSaved = mutableListOf<Map<String, String>>()

    private var asked = 0

    private fun driving(settings: GpsSettings, windows: WindowsLocation.Result = WindowsLocation.Unknown) =
        Driving(settings, gamepad = { null }, nanoTime = { now }, locate = { asked++; windows }, lookup = { it.run() })
            .also { it.savePlace = { place -> placeSaved += place } }

    // gps.start is where the car is: it starts there, facing gps.heading.
    @Test
    fun `the car starts at its start`() {
        val start = driving(GpsSettings(true, start = kyiv, headingDegrees = 90.0)).drive.snapshot()
        assertEquals(kyiv, start.position)
        assertEquals(90.0, start.car.headingDegrees, 0.0)
    }

    // Without a start, the car is nowhere until a start is saved.
    @Test
    fun `a car with no start is placed by a saved start`() {
        val driving = driving(GpsSettings(true))
        assertFalse(driving.placed)
        driving.saved(GpsSettings(true, start = kyiv), setOf("gps.start"))
        assertTrue(driving.placed)
        assertEquals(kyiv, driving.drive.snapshot().position)
    }

    // The GPS turned on with no start: the car goes where Windows locates the PC, and that place is saved as the start.
    @Test
    fun `a GPS turned on without a start starts where Windows locates the PC`() {
        val driving = driving(GpsSettings(false, headingDegrees = 90.0), windows = WindowsLocation.Found(kyiv))
        driving.saved(GpsSettings(true, headingDegrees = 90.0), setOf("gps.enabled"))
        assertTrue(driving.placed)
        assertEquals(kyiv, driving.drive.snapshot().position)
        assertEquals(mapOf("gps.start" to "50.45010, 30.52340", "gps.heading" to "90"), placeSaved.single())
    }

    // At launch with the GPS on and no start, Windows is asked once the place can be saved.
    @Test
    fun `a launch with the GPS on and no start asks Windows`() {
        val driving = driving(GpsSettings(true), windows = WindowsLocation.Found(kyiv))
        assertEquals(0, asked)
        val saved = mutableListOf<Map<String, String>>()
        driving.saveTo { saved += it }
        assertEquals(1, asked)
        assertEquals(kyiv, driving.drive.snapshot().position)
        assertEquals("50.45010, 30.52340", saved.single()["gps.start"])
    }

    // A start already set, or the GPS off, leaves Windows unasked.
    @Test
    fun `Windows is asked only for a GPS on with no start`() {
        driving(GpsSettings(true, start = lviv), windows = WindowsLocation.Found(kyiv)).apply {
            saveTo {}
            saved(GpsSettings(true, start = lviv), setOf("gps.enabled"))
        }
        driving(GpsSettings(false), windows = WindowsLocation.Found(kyiv)).apply {
            saveTo {}
            saved(GpsSettings(false), setOf("gps.enabled"))
        }
        assertEquals(0, asked)
    }

    // Windows refusing or finding nothing leaves the car nowhere, and nothing is saved.
    @Test
    fun `no Windows position leaves the car without a start`() {
        for (answer in listOf(WindowsLocation.Denied, WindowsLocation.Unknown)) {
            val driving = driving(GpsSettings(false), windows = answer)
            driving.saved(GpsSettings(true), setOf("gps.enabled"))
            assertFalse(driving.placed)
        }
        assertEquals(2, asked)
        assertTrue(placeSaved.isEmpty())
    }

    // Saving a start or heading puts the car there at once; saving the car changes it where it is.
    @Test
    fun `saved settings place the car or change it at once`() {
        val driving = driving(GpsSettings(true, start = kyiv))
        driving.saved(GpsSettings(true, start = lviv, headingDegrees = 45.0), setOf("gps.start", "gps.heading"))
        assertEquals(lviv, driving.drive.snapshot().position)
        assertEquals(45.0, driving.drive.snapshot().car.headingDegrees, 0.0)

        val strong = GpsSettings(true, start = lviv, headingDegrees = 45.0, car = CarSpec(powerHp = 600.0))
        driving.saved(strong, setOf("gps.car.power.hp"))
        assertEquals(lviv, driving.drive.snapshot().position)
        assertEquals(600.0, driving.settings.car.powerHp, 0.0)
        assertEquals(600.0, driving.drive.spec.powerHp, 0.0)

        driving.saved(strong.copy(car = strong.car.copy(handling = Handling.SHARP)), setOf("gps.car.handling"))
        assertEquals(Handling.SHARP, driving.drive.spec.handling)
    }

    // The page's heading field turns the car where it stands, rather than sending it back to the start point.
    @Test
    fun `a heading saved alone turns the car where it is`() {
        val driving = driving(GpsSettings(true, start = kyiv))
        driving.drive.input = CarInput(throttle = 1.0)
        repeat(120) { now += 16_666_667L; driving.drive.advance() }
        val there = driving.drive.snapshot().position
        driving.saved(GpsSettings(true, start = kyiv, headingDegrees = 200.0), setOf("gps.heading"))
        assertEquals(there, driving.drive.snapshot().position)
        assertEquals(200.0, driving.drive.snapshot().car.headingDegrees, 0.0)
    }

    // Where the car stopped goes into gps.start and gps.heading, which the page shows and the next start begins at; and
    // again as the app closes.
    @Test
    fun `the car's place is saved into the settings as the app closes`() {
        val driving = driving(GpsSettings(true, start = kyiv))
        driving.drive.input = CarInput(throttle = 1.0)
        repeat(120) { now += 16_666_667L; driving.drive.advance() }
        driving.close()
        val saved = placeSaved.single()
        val (latitude, longitude) = saved.getValue("gps.start").split(",").map { it.trim().toDouble() }
        assertTrue(latitude > kyiv.latitude)
        assertEquals(kyiv.longitude, longitude, 1e-4)
        assertEquals("0", saved["gps.heading"])
    }

    // The place the car saved itself comes back as a saved setting: it is where the car is, so the car drives on.
    @Test
    fun `the car's own saved place does not place it again`() {
        val driving = driving(GpsSettings(true, start = kyiv))
        driving.drive.input = CarInput(throttle = 1.0)
        repeat(120) { now += 16_666_667L; driving.drive.advance() }
        driving.close()
        val (latitude, longitude) = placeSaved.single().getValue("gps.start").split(",").map { it.trim().toDouble() }
        val speed = driving.drive.snapshot().car.speedKmh
        driving.saved(GpsSettings(true, start = GeoPoint(latitude, longitude)), setOf("gps.start", "gps.heading"))
        assertEquals(speed, driving.drive.snapshot().car.speedKmh, 0.0)
        assertFalse(driving.drive.snapshot().car.parked)
    }

    // With the GPS off the car stays put: the gamepad and drive mode do nothing, and a drive mode on ends.
    @Test
    fun `the car is driven only while the GPS is on`() {
        val off = Driving(GpsSettings(false, start = kyiv), gamepad = { floatArrayOf(0f, 0f, 0f, 0f, -1f, 1f) }, nanoTime = { now })
        assertNull(off.pass())
        assertFalse(off.toggleKeyboard())
        assertEquals(0.0, off.drive.snapshot().car.speed, 0.0)

        val on = Driving(GpsSettings(true, start = kyiv), gamepad = { null }, nanoTime = { now })
        assertTrue(on.toggleKeyboard())
        on.drive.input = CarInput(throttle = 1.0)
        repeat(120) { now += 16_666_667L; on.drive.advance() }
        assertTrue(on.drive.moving)
        // Turned off mid-drive: the car stops where it is, rather than standing still at speed for the iPhone, and
        // one more pass draws the gauges away.
        on.saved(GpsSettings(false, start = kyiv), setOf("gps.enabled"))
        assertEquals(DriveControl.FRAME_SECONDS, on.pass())
        assertFalse(on.control.keyboard)
        assertFalse(on.drive.moving)
        assertNull(on.gauges())
        assertNull(on.pass())
    }

    // The GPS page: where the car is, how it goes, and what the iPhone asked for.
    @Test
    fun `the state tells the page about the car and the iPhone`() {
        val driving = driving(GpsSettings(true, start = kyiv))
        val idle = driving.state()
        assertEquals(false, idle["listening"])
        assertEquals(false, idle["keyboard"])
        assertEquals(false, idle["gamepad"])
        assertEquals(listOf(50.4501, 30.5234), idle["position"])
        assertEquals(0, idle["speed"])
        assertEquals("P", idle["gear"])

        driving.location = LocationLink { LocationStatus(listening = true, requested = setOf(1, 2, 4), sent = 1) }
        val state = driving.state()
        assertEquals(true, state["listening"])
        assertEquals(listOf("GGA", "RMC", "PASCD"), state["sentences"])
        assertEquals(1L, state["sent"])
    }
}
