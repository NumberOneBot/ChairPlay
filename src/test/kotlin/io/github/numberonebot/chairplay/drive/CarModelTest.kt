package io.github.numberonebot.chairplay.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarModelTest {
    private val dt = 1.0 / 60

    private fun CarModel.run(seconds: Double, input: CarInput, each: (CarState) -> Unit = {}) {
        repeat((seconds / dt).toInt()) { each(step(input, dt)) }
    }

    // The default car, 300 hp and 1500 kg, geared and gripping for the launch: under 4.5 s to 100 km/h, on to where its
    // power meets the drag (near 283 km/h, short of the top gear's red line), the engine always between idle and the
    // red line, the seven gears going up one by one, each shift close to the red line.
    @Test
    fun `full throttle takes the car to 100 and on to its top speed, shifting close to the red line`() {
        val car = CarModel(CarSpec())
        var to100 = -1.0
        var time = 0.0
        val gears = mutableListOf<Int>()
        val shiftRpms = mutableListOf<Double>()
        var lastRpm = 0.0
        car.run(120.0, CarInput(throttle = 1.0)) { state ->
            time += dt
            if (to100 < 0 && state.speedKmh >= 100) to100 = time
            assertTrue("rpm ${state.rpm}", state.rpm in car.spec.idleRpm..car.spec.redlineRpm)
            if (gears.lastOrNull() != state.gear) {
                if (gears.isNotEmpty()) shiftRpms += lastRpm
                gears += state.gear
            }
            lastRpm = state.rpm
        }

        assertTrue("0-100 in $to100 s", to100 in 3.0..4.5)
        assertEquals(CarModel.topSpeed(car.spec) * 3.6, car.state.speedKmh, 3.0)
        assertTrue("top ${car.state.speedKmh} km/h", car.state.speedKmh > 275.0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), gears)
        assertTrue("shifts at $shiftRpms", shiftRpms.all { it >= 6400 })
    }

    // Only the speedometer limits the speed: a car powerful enough runs to its end and no further.
    @Test
    fun `a powerful car runs to the end of the speedometer and no further`() {
        val car = CarModel(CarSpec(powerHp = 2000.0))
        car.run(120.0, CarInput(throttle = 1.0)) { assertTrue("${it.speedKmh} km/h", it.speedKmh <= 300.5) }
        assertEquals(300.0, car.state.speedKmh, 1.0)
    }

    // Twice the power, a quicker car; twice the mass, a slower one.
    @Test
    fun `power quickens the car and mass slows it`() {
        fun speedAfter(spec: CarSpec): Double = CarModel(spec).also { it.run(10.0, CarInput(throttle = 1.0)) }.state.speedKmh
        val base = speedAfter(CarSpec())
        assertTrue(speedAfter(CarSpec(powerHp = 600.0)) > base + 10)
        assertTrue(speedAfter(CarSpec(massKg = 3000.0)) < base - 10)
    }

    @Test
    fun `the brake stops the car within a few seconds, and coasting slows it`() {
        val car = CarModel(CarSpec())
        car.run(12.0, CarInput(throttle = 1.0))
        val cruising = car.state.speedKmh
        car.run(3.0, CarInput())
        assertTrue(car.state.speedKmh in 1.0..cruising - 2)

        var time = 0.0
        var stoppedAt = -1.0
        car.run(8.0, CarInput(brake = 1.0)) { state ->
            time += dt
            if (stoppedAt < 0 && state.speed <= 0.0) stoppedAt = time
        }
        assertTrue("stopped at $stoppedAt s", stoppedAt in 2.0..6.0)
    }

    // Standing 10 s, with the brake held or not, the car goes into park and its engine stops; a touch of the throttle
    // starts it and drives off.
    @Test
    fun `standing ten seconds parks the car and stops its engine, and the throttle starts it`() {
        val car = CarModel(CarSpec())
        car.run(9.5, CarInput(brake = 1.0))
        assertEquals("D1", car.state.gearName)
        assertEquals(car.spec.idleRpm, car.state.rpm, 1.0)
        car.run(2.0, CarInput())
        assertTrue(car.state.parked)
        assertEquals("P", car.state.gearName)
        assertTrue("rpm ${car.state.rpm}", car.state.rpm < 50)
        car.run(1.0, CarInput(brake = 1.0, steer = 1.0))
        assertTrue(car.state.parked)

        car.run(1.0, CarInput(throttle = 0.3))
        assertEquals("D1", car.state.gearName)
        assertTrue("rpm ${car.state.rpm}", car.state.rpm > car.spec.idleRpm)
        assertTrue(car.state.speed > 0)
    }

    // A sports car's brakes: full brake from 100 km/h stops the car within 30 m.
    @Test
    fun `the brake stops the car from 100 as a sports car's`() {
        val car = CarModel(CarSpec())
        while (car.state.speedKmh < 100) car.step(CarInput(throttle = 1.0), dt)
        var meters = 0.0
        while (car.state.speed > 0) {
            car.step(CarInput(brake = 1.0), dt)
            meters += car.lastMeters
        }
        assertTrue("stopped in $meters m", meters <= 30.0)
    }

    // Off the throttle the engine brakes the car as a real one does in gear: 100 km/h loses over 15 in 3 s, and 50 km/h
    // rolls to a stop within 15 s.
    @Test
    fun `lifting off slows the car briskly`() {
        val car = CarModel(CarSpec())
        while (car.state.speedKmh < 100) car.step(CarInput(throttle = 1.0), dt)
        val from = car.state.speedKmh
        car.run(3.0, CarInput())
        assertTrue("${car.state.speedKmh} km/h after 3 s from $from", car.state.speedKmh < from - 15)

        while (car.state.speedKmh > 50) car.step(CarInput(), dt)
        var time = 0.0
        while (car.state.speed > 0 && time < 20) {
            car.step(CarInput(), dt)
            time += dt
        }
        assertTrue("stopped after $time s", time <= 15.0)
    }

    // Reverse is off for now: held at a standstill, the brake only holds the car.
    @Test
    fun `without reverse the brake held at a standstill holds the car`() {
        val car = CarModel(CarSpec())
        car.run(4.0, CarInput(brake = 1.0))
        assertEquals(0.0, car.state.speed, 0.0)
        assertEquals(1, car.state.gear)
    }

    // Arcade, with reverse on: held at a standstill, the brake backs the car up, slowly; the throttle then stops it and
    // drives on.
    @Test
    fun `the brake held at a standstill reverses, and the throttle drives forward again`() {
        val car = CarModel(CarSpec(reverse = true))
        car.run(4.0, CarInput(brake = 1.0))
        assertTrue("speed ${car.state.speed}", car.state.speed < -1.0)
        assertEquals(-1, car.state.gear)
        assertTrue(car.state.speedKmh <= 25.0)

        car.run(6.0, CarInput(throttle = 1.0))
        assertTrue(car.state.speed > 1.0)
        assertTrue(car.state.gear >= 1)
    }

    // Arcade steering, without slip: the stick turns a moving car, more gently the faster it goes, and a standing one on
    // the spot, a quarter turn a second, so it can be pointed before it drives off.
    @Test
    fun `the stick turns a moving car, and a standing one on the spot`() {
        val standing = CarModel(CarSpec())
        standing.run(1.0, CarInput(steer = 1.0))
        assertEquals(90.0, standing.state.headingDegrees, 2.0)
        assertEquals(0.0, standing.state.speed, 0.0)
        standing.run(2.0, CarInput(steer = -1.0))
        assertEquals(270.0, standing.state.headingDegrees, 3.0)

        val slow = CarModel(CarSpec(), CarState(speed = 50 / 3.6, gear = 3))
        slow.run(1.0, CarInput(steer = 1.0, throttle = 0.3))
        val fast = CarModel(CarSpec(), CarState(speed = 150 / 3.6, gear = 5))
        fast.run(1.0, CarInput(steer = 1.0, throttle = 0.3))
        assertTrue("slow ${slow.state.headingDegrees}", slow.state.headingDegrees in 30.0..120.0)
        assertTrue("fast ${fast.state.headingDegrees}", fast.state.headingDegrees in 5.0..slow.state.headingDegrees - 5)
        // Slow, it turns as fast as it does standing: only speed takes the turning down.
        val crawl = CarModel(CarSpec(), CarState(speed = 15 / 3.6, gear = 1))
        crawl.run(1.0, CarInput(steer = 1.0, throttle = 0.05))
        assertEquals(90.0, crawl.state.headingDegrees, 2.0)
        val creep = CarModel(CarSpec(), CarState(speed = 3 / 3.6, gear = 1))
        creep.run(1.0, CarInput(steer = 1.0, throttle = 0.05))
        assertEquals(90.0, creep.state.headingDegrees, 2.0)
        val left = CarModel(CarSpec(), CarState(speed = 50 / 3.6, gear = 3))
        left.run(1.0, CarInput(steer = -1.0, throttle = 0.3))
        assertTrue(left.state.headingDegrees > 180.0)
    }

    // Sharp handling turns harder than gentle, slow and fast alike; normal sits between.
    @Test
    fun `sharp handling turns harder than gentle`() {
        fun turned(handling: Handling, kmh: Double): Double {
            val car = CarModel(CarSpec(handling = handling), CarState(speed = kmh / 3.6, gear = 3))
            car.run(0.5, CarInput(steer = 1.0, throttle = 0.3))
            return car.state.headingDegrees
        }
        for (kmh in listOf(15.0, 80.0)) {
            assertTrue("$kmh km/h", turned(Handling.GENTLE, kmh) < turned(Handling.NORMAL, kmh))
            assertTrue("$kmh km/h", turned(Handling.NORMAL, kmh) < turned(Handling.SHARP, kmh))
        }
    }

    // The tachometer: the engine idles at rest, revs with the throttle as the clutch slips at a launch.
    @Test
    fun `the engine idles at rest and revs at a launch`() {
        val car = CarModel(CarSpec())
        car.run(1.0, CarInput())
        assertEquals(car.spec.idleRpm, car.state.rpm, 50.0)
        car.run(0.3, CarInput(throttle = 1.0))
        assertTrue("rpm ${car.state.rpm}", car.state.rpm > 2500)
    }

    // Starting out of park the engine flares above idle, then settles back to idle, as a real one caught starting does.
    @Test
    fun `the engine flares on start out of park, then settles to idle`() {
        val car = CarModel(CarSpec(), CarState(parked = true))
        // A touch of the throttle starts it; then lift off and let it idle at a standstill.
        car.step(CarInput(throttle = 0.1), dt)
        var peak = car.state.rpm
        car.run(1.5, CarInput()) { peak = maxOf(peak, it.rpm) }
        assertTrue("peak $peak", peak > car.spec.idleRpm + 250)

        car.run(3.0, CarInput())
        assertEquals(car.spec.idleRpm, car.state.rpm, 80.0)
        assertEquals(0.0, car.state.speed, 0.0)
    }

    // A pedal too light to beat the rolling resistance leaves the car standing, never rolling the other way.
    @Test
    fun `a pedal too light to move the car leaves it standing`() {
        val car = CarModel(CarSpec())
        car.run(2.0, CarInput(throttle = 0.01))
        assertEquals(0.0, car.state.speed, 0.0)
        val heavy = CarModel(CarSpec(powerHp = 20.0, massKg = 10000.0))
        heavy.run(2.0, CarInput(throttle = 0.2)) { assertTrue("speed ${it.speed}", it.speed >= 0.0) }
    }

    // Each step says how far the car went, for its place on the earth.
    @Test
    fun `a step reports the distance covered`() {
        val car = CarModel(CarSpec(), CarState(speed = 20.0, gear = 4))
        var meters = 0.0
        repeat(60) { car.step(CarInput(throttle = 0.4), dt); meters += car.lastMeters }
        assertEquals(20.0, meters, 2.0)
    }

    // A game's gear (its neutral, a truck's twelfth) is only shown: the model drives on in a gear it has.
    @Test
    fun `a gear the car does not have drives on in one it has`() {
        for (gear in listOf(0, 12)) {
            val car = CarModel(CarSpec(), CarState(speed = 10.0, gear = gear))
            car.run(1.0, CarInput(throttle = 0.5))
            assertTrue("gear ${car.state.gear}", car.state.gear in 1..CarSpec().gearRatios.size)
            assertTrue("speed ${car.state.speed}", car.state.speed > 10.0)
        }
    }
}
