package io.github.numberonebot.chairplay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WindowPlacementTest {
    private val configured = WindowSize(1280, 720)

    @Test
    fun `remembers where the window was left`() {
        val store = WindowPlacementStore(Files.createTempDirectory("chairplay-window"))
        val placement = WindowPlacement(x = -1500, y = 120, width = 1600, height = 900, maximized = true)

        store.save(placement, configured)

        assertEquals(placement, store.load(configured))
    }

    // Get started shows until an iPhone has connected once; Forget this iPhone brings it back.
    @Test
    fun `setup is done once remembered so, and not before`() {
        val dir = Files.createTempDirectory("chairplay-setup")
        val store = SetupStore(dir)
        assertFalse(store.done())

        store.save(true)
        assertTrue(store.done())
        store.save(false)
        assertFalse(store.done())
    }

    // An install that connected before this mark existed has its pairing records: its setup is done.
    @Test
    fun `an install with pairing records is set up, until said otherwise`() {
        val dir = Files.createTempDirectory("chairplay-setup")
        Files.createDirectories(dir.resolve("pairings"))
        assertFalse(SetupStore(dir).done())
        Files.writeString(dir.resolve("pairings").resolve("iphone.properties"), "x")

        assertTrue(SetupStore(dir).done())
        SetupStore(dir).save(false)
        assertFalse(SetupStore(dir).done())
    }

    // Forget this iPhone: ChairPlay's pairing records go, and Get started opens again; Windows' pairing stays.
    @Test
    fun `forgetting deletes the pairing records and undoes the setup`() {
        val dir = Files.createTempDirectory("chairplay-setup")
        val pairings = Files.createDirectories(dir.resolve("pairings"))
        Files.writeString(pairings.resolve("iphone.properties"), "x")
        Files.writeString(pairings.resolve("other.properties"), "y")
        val store = SetupStore(dir)
        store.save(true)

        store.forget()

        assertFalse(store.done())
        assertTrue(Files.isDirectory(pairings))
        assertEquals(0L, Files.list(pairings).use { it.count() })
    }

    // The keys the running app loaded go too: the iPhone pairs again at its next connection, without a restart.
    @Test
    fun `forgetting clears the pairings the app has loaded`() {
        var cleared = 0

        SetupStore(Files.createTempDirectory("chairplay-setup")) { cleared++ }.forget()

        assertEquals(1, cleared)
    }

    @Test
    fun `nothing is remembered before the first close`() {
        assertNull(WindowPlacementStore(Files.createTempDirectory("chairplay-window")).load(configured))
    }

    // A file edited into nonsense is forgotten rather than stopping the start.
    @Test
    fun `a broken file is ignored`() {
        val dir = Files.createTempDirectory("chairplay-window")
        Files.writeString(dir.resolve(WindowPlacementStore.FILE), "x=left\ny=10\nwidth=0\nheight=720\n")

        assertNull(WindowPlacementStore(dir).load(configured))
    }

    // window.width/height (or the display size they follow) changed since: the new size wins, the place stays.
    @Test
    fun `a new window size in the settings replaces the remembered one`() {
        val store = WindowPlacementStore(Files.createTempDirectory("chairplay-window"))
        store.save(WindowPlacement(200, 100, 1600, 900, maximized = false), configured)

        assertEquals(WindowPlacement(200, 100, 960, 540, maximized = false), store.load(WindowSize(960, 540)))
    }

    // The altscreen window keeps its place in the same file under its own prefix, next to the main window's.
    @Test
    fun `two windows share the file without overwriting each other`() {
        val dir = Files.createTempDirectory("chairplay-window")
        val main = WindowPlacementStore(dir)
        val altscreen = WindowPlacementStore(dir, prefix = "altscreen.")
        main.save(WindowPlacement(10, 20, 1280, 720, maximized = false), configured)
        altscreen.save(WindowPlacement(30, 900, 1280, 480, maximized = false), WindowSize(1280, 480))
        main.save(WindowPlacement(11, 21, 1300, 740, maximized = true), configured)

        assertEquals(WindowPlacement(11, 21, 1300, 740, maximized = true), main.load(configured))
        assertEquals(WindowPlacement(30, 900, 1280, 480, maximized = false), altscreen.load(WindowSize(1280, 480)))
    }

    // With window.keep.aspect a remembered or configured size takes the picture's shape, keeping its width.
    @Test
    fun `a size is fitted to the picture's aspect by its width`() {
        assertEquals(WindowSize(1280, 720), WindowSize(1280, 828).fittedTo(WindowSize(1280, 720)))
        assertEquals(WindowSize(1000, 375), WindowSize(1000, 900).fittedTo(WindowSize(1280, 480)))
    }

    // A window that keeps its picture's shape must not stop at a minimum of another shape, or the picture gets bars:
    // 800x480 is 5:3, so a 16:9 picture's window stops at 854x480 and a 4:3 one's at 800x600.
    @Test
    fun `a minimum grows to the picture's shape`() {
        assertEquals(WindowSize(854, 480), MAIN_WINDOW_MIN_SIZE.grownTo(WindowSize(1280, 720)))
        assertEquals(WindowSize(800, 600), MAIN_WINDOW_MIN_SIZE.grownTo(WindowSize(1024, 768)))
        assertEquals(WindowSize(800, 480), MAIN_WINDOW_MIN_SIZE.grownTo(WindowSize(1000, 600)))
        assertEquals(WindowSize(1280, 480), MAIN_WINDOW_MIN_SIZE.grownTo(WindowSize(1920, 720)))
    }

    // The scaled minimum must not keep a window from its picture's own size: an 800x480 stream at 160 % display scale
    // would otherwise stop the window at 1280x768, and Actual size could never reach one pixel per pixel.
    @Test
    fun `a window's smallest size never outgrows its picture`() {
        assertEquals(WindowSize(800, 480), MAIN_WINDOW_MIN_SIZE.limitFor(1.6f, picture = WindowSize(800, 480)))
        assertEquals(WindowSize(1280, 768), MAIN_WINDOW_MIN_SIZE.limitFor(1.6f, picture = WindowSize(1920, 1080)))
        assertEquals(WindowSize(1280, 768), MAIN_WINDOW_MIN_SIZE.limitFor(1.6f, picture = null))
        assertEquals(WindowSize(1440, 540), WindowSize(1600, 600).limitFor(1f, picture = WindowSize(1440, 540)))
    }

    // A minimum is in the web view's pixels: at 150 % display scale 800x480 takes 1200x720 screen pixels.
    @Test
    fun `a minimum size grows with the display scale`() {
        assertEquals(WindowSize(1200, 720), MAIN_WINDOW_MIN_SIZE.scaledBy(1.5f))
        assertEquals(WindowSize(1000, 600), MAIN_WINDOW_MIN_SIZE.scaledBy(1.25f))
        assertEquals(MAIN_WINDOW_MIN_SIZE, MAIN_WINDOW_MIN_SIZE.scaledBy(0f))
    }

    // The AltScreen shows no page, so its smallest size is in screen pixels: 1280 wide in the picture's shape, below a
    // 1440x540 picture's own size.
    @Test
    fun `the AltScreen's smallest size is 1280 screen pixels wide in its picture's shape`() {
        val min = WindowSize(ALTSCREEN_MIN_WIDTH, 1).fittedTo(WindowSize(1440, 540))
        assertEquals(WindowSize(1280, 480), min)
        assertEquals(WindowSize(1280, 480), min.limitFor(1f, picture = WindowSize(1440, 540)))
    }

    // The controls window's size is its page's, in the web view's pixels, which GLFW scales to the monitor; a size it
    // was left at is in screen pixels already, scaled once, so only its place is taken back.
    @Test
    fun `a window sized by its page opens at its page's size wherever it was left`() {
        val left = WindowPlacement(2247, 1036, 960, 444, maximized = false)

        assertEquals(WindowSize(518, 218), openingSize(WindowSize(518, 218), left, pageSized = true))
        assertEquals(WindowSize(960, 444), openingSize(WindowSize(518, 218), left, pageSized = false))
        assertEquals(WindowSize(1280, 720), openingSize(configured, null, pageSized = false))
    }

    // The controls window opens shown or hidden as it was left; before it was ever left, window.controls decides.
    @Test
    fun `the controls window's visibility is remembered`() {
        val dir = Files.createTempDirectory("chairplay-window")
        val store = ControlsWindowStore(dir)
        assertTrue(store.load(default = true))
        assertFalse(store.load(default = false))
        WindowPlacementStore(dir).save(WindowPlacement(10, 20, 1280, 720, maximized = false), configured)
        store.save(visible = false)
        assertFalse(store.load(default = true))
        store.save(visible = true)
        assertTrue(store.load(default = false))
        // The windows' places stay beside it.
        assertEquals(WindowPlacement(10, 20, 1280, 720, maximized = false), WindowPlacementStore(dir).load(configured))
    }

    // The volume stays where it was left, as a car's does; a first start plays at full volume.
    @Test
    fun `the volume and mute are remembered`() {
        val dir = Files.createTempDirectory("chairplay-window")
        val store = VolumeStore(dir)
        assertEquals(100 to false, store.load())
        ControlsWindowStore(dir).save(visible = false)
        store.save(level = 35, muted = true)
        assertEquals(35 to true, store.load())
        // The controls window's state stays beside it.
        assertFalse(ControlsWindowStore(dir).load(default = true))
    }

    @Test
    fun `a window on a monitor that is still there is visible`() {
        val screens = listOf(ScreenArea(0, 0, 1920, 1040), ScreenArea(-1920, 0, 1920, 1040))

        assertTrue(WindowPlacement(-1500, 120, 1280, 720, false).isVisibleOn(screens))
        // Mostly off the right edge, but enough of its title bar is left to drag it back.
        assertTrue(WindowPlacement(1700, 300, 1280, 720, false).isVisibleOn(screens))
    }

    @Test
    fun `a window left on a monitor that is gone is not visible`() {
        val screens = listOf(ScreenArea(0, 0, 1920, 1040))

        assertFalse(WindowPlacement(-1500, 120, 1280, 720, false).isVisibleOn(screens))
        // Its title bar would sit above the top of the screen, out of reach.
        assertFalse(WindowPlacement(300, -200, 1280, 720, false).isVisibleOn(screens))
        // Only a sliver of the title bar shows at the right edge.
        assertFalse(WindowPlacement(1900, 300, 1280, 720, false).isVisibleOn(screens))
    }
}
