package io.github.numberonebot.chairplay.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

/** Whose screen a game's car display shows, as a car's head unit decides it (owner, 2026-11-24). */
class BridgeScreenTest {
    private val published = mutableListOf<Int>()
    private val screen = BridgeScreen { published += it }

    @Test
    fun `the car button gives the screen to the car, and the game's CarPlay button takes it back`() {
        screen.carButton()
        screen.carButton()
        screen.carPlay()
        assertEquals(listOf(BridgeScreen.CAR, BridgeScreen.CARPLAY), published)
    }

    // As CarPlay borrows a car's screen: Siri or a call shows CarPlay for as long as it lasts, then the car's again.
    @Test
    fun `Siri or a call borrows the car's screen for as long as it lasts`() {
        screen.carButton()
        screen.voice(true)
        screen.voice(false)
        assertEquals(listOf(BridgeScreen.CAR, BridgeScreen.CARPLAY, BridgeScreen.CAR), published)
    }

    @Test
    fun `Siri over CarPlay changes nothing`() {
        screen.voice(true)
        screen.voice(false)
        assertEquals(emptyList<Int>(), published)
    }

    // The game's CarPlay button during a call keeps CarPlay once the call ends.
    @Test
    fun `CarPlay taken back during a call stays after it`() {
        screen.carButton()
        screen.voice(true)
        screen.carPlay()
        screen.voice(false)
        assertEquals(listOf(BridgeScreen.CAR, BridgeScreen.CARPLAY), published)
    }

    // The car button pressed while Siri borrows the screen gives it to the car once Siri ends.
    @Test
    fun `the car button during Siri gives the screen to the car after it`() {
        screen.voice(true)
        screen.carButton()
        assertEquals(emptyList<Int>(), published)
        screen.voice(false)
        assertEquals(listOf(BridgeScreen.CAR), published)
    }
}
