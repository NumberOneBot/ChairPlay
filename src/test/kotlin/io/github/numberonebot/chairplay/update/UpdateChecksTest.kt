package io.github.numberonebot.chairplay.update

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class UpdateChecksTest {
    private val notices = mutableListOf<UpdateResult>()
    private var requests = 0
    private var opened = 0
    private var open = false
    private var latest: String? = "1.0.6"
    private var failure = false
    private var published: LocalDate? = LocalDate.of(2026, 11, 10)
    private fun found() = latest?.let { Release(ReleaseVersion.parse(it), published) }

    private fun checks(current: String? = "1.0.5") = UpdateChecks(current,
        lookup = { requests++; if (failure) throw IOException(); found() },
        show = { notices += it; open }, openReleases = { opened++ }, log = {})

    @Test fun `startup only notifies about a newer release`() {
        checks().use { assertTrue(it.check(manual = false)) }
        assertEquals(UpdateResult.Available("1.0.5", "1.0.6"), notices.single())
        notices.clear()
        for (version in listOf("1.0.5", "1.0.4", null)) {
            latest = version
            checks().use { it.check(manual = false) }
        }
        failure = true
        checks().use { it.check(manual = false) }
        assertTrue(notices.isEmpty())
    }

    @Test fun `manual checks report current and failed requests and allow retry`() {
        latest = "1.0.5"
        checks().use { updates ->
            updates.check()
            assertEquals(UpdateResult.Current, notices.last())
            failure = true
            updates.check()
            assertEquals(UpdateResult.Failed, notices.last())
            failure = false
            latest = "1.1.0"
            updates.check()
            assertTrue(notices.last() is UpdateResult.Available)
        }
    }

    @Test fun `Open launches releases and Cancel only dismisses the notice`() {
        checks().use { it.check() }
        assertEquals(0, opened)
        open = true
        checks().use { it.check() }
        assertEquals(1, opened)
    }

    // What a check found stays for the About page, which offers it while it is newer (owner, 2026-11-12).
    @Test fun `a check that reaches GitHub remembers the release it found`() {
        val remembered = mutableListOf<String>()
        fun checks() = UpdateChecks("1.0.5",
            lookup = { if (failure) throw IOException(); found() },
            show = { false }, openReleases = {}, remember = { remembered += it.version.toString() }, log = {})
        checks().use { it.check(manual = false) }
        latest = "1.0.5"
        checks().use { it.check() }
        failure = true
        checks().use { it.check() }
        failure = false
        latest = null
        checks().use { it.check() }
        assertEquals(listOf("1.0.6", "1.0.5"), remembered)
    }

    // A build without a version, from a commit of [built], takes a release published after that day for newer, in the
    // startup dialog and Check now alike (owner, 2026-11-12).
    @Test fun `a development build takes a release published after its commit for newer`() {
        val built = LocalDate.of(2026, 11, 1)
        fun dev() = UpdateChecks(null, built = built, lookup = { requests++; found() },
            show = { notices += it; false }, openReleases = {}, log = {})
        dev().use { it.check(manual = false) }
        assertEquals(UpdateResult.Available(null, "1.0.6", built), notices.single())
        notices.clear()
        for (day in listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 30), null)) {
            published = day
            dev().use { it.check(manual = false) }
        }
        assertTrue(notices.isEmpty())
        dev().use { it.check() }
        assertEquals(UpdateResult.Development, notices.single())
    }

    @Test fun `a remembered release is offered only while it is newer than this build`() {
        assertEquals("1.0.6", newerRelease("1.0.6", "1.0.5"))
        assertEquals("1.1.0", newerRelease("1.1.0", "1.1.0-rc1"))
        for ((stored, installed) in listOf("1.0.5" to "1.0.5", "1.0.4" to "1.0.5", null to "1.0.5", "1.0.6" to null,
                "junk" to "1.0.5", "" to "1.0.5")) {
            assertNull("$stored over $installed", newerRelease(stored, installed))
        }
        // A build without a version goes by the day: the release's publication against its own commit's
        assertEquals("1.1.1", newerRelease("1.1.1", null, published = "2026-11-10", built = "2026-11-01"))
        for ((published, built) in listOf("2026-11-01" to "2026-11-01", null to "2026-11-01", "2026-11-10" to null,
                "junk" to "2026-11-01")) {
            assertNull("$published over $built", newerRelease("1.1.1", null, published, built))
        }
    }

    @Test fun `startup can be disabled without disabling manual checks`() {
        checks().use {
            it.start(enabled = false)
            assertEquals(0, requests)
            it.check()
            assertEquals(1, requests)
        }
    }

    @Test fun `unversioned builds do not claim a release is newer`() {
        checks(null).use {
            it.check(manual = false)
            assertTrue(notices.isEmpty())
            it.check()
        }
        assertEquals(0, requests)
        assertEquals(UpdateResult.Development, notices.single())
    }

    @Test fun `startup returns while lookup runs and concurrent checks do not stack dialogs`() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val shown = CountDownLatch(1)
        val updates = UpdateChecks("1.0.5", lookup = {
            entered.countDown()
            assertTrue(finish.await(5, TimeUnit.SECONDS))
            Release(ReleaseVersion.parse("1.0.6"), null)
        }, show = { shown.countDown(); false }, openReleases = {}, log = {})
        updates.use {
            it.start(true)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(it.check())
            it.start(true)
            finish.countDown()
            assertTrue(shown.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun `closing during a request suppresses its notification`() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val updates = UpdateChecks("1.0.5", lookup = {
            entered.countDown(); finish.await(5, TimeUnit.SECONDS); Release(ReleaseVersion.parse("1.0.6"), null)
        }, show = { notices += it; false }, openReleases = {}, log = {})
        val worker = thread { updates.check() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        updates.close()
        finish.countDown()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(notices.isEmpty())
    }
}
