package io.github.numberonebot.chairplay.update

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
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

    private fun checks(current: String? = "1.0.5") = UpdateChecks(current,
        lookup = { requests++; if (failure) throw IOException(); latest?.let(ReleaseVersion::parse) },
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
            ReleaseVersion.parse("1.0.6")
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
            entered.countDown(); finish.await(5, TimeUnit.SECONDS); ReleaseVersion.parse("1.0.6")
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
