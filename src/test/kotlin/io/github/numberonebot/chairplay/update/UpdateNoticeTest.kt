package io.github.numberonebot.chairplay.update

import io.github.numberonebot.chairplay.config.SupportStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class UpdateNoticeTest {
    // stored version, setup done, this build's version → toast (desktop-docs/2026-11-21-update-toast.md, rows 1 to 8)
    @Test
    fun `a toast shows on the first start of a new minor or major version only`() {
        val rows = listOf(
            Triple(null, false, "1.2.0") to false, // a new install
            Triple(null, true, "1.2.0") to true, // an install from before this feature
            Triple("1.1.4", true, "1.2.0") to true,
            Triple("1.1.4", true, "1.2.1") to true, // 1.2.0 skipped
            Triple("1.1.4", true, "2.0.0") to true,
            Triple("1.2.0", true, "1.2.1") to false, // a patch
            Triple("1.2.0", true, "1.2.0") to false, // the same version again
            Triple("1.3.0", true, "1.2.0") to false, // a downgrade
            Triple("1.1.4", true, "1.2.0-rc1") to false, // a pre-release
            Triple("1.2.0-rc1", true, "1.2.0-rc2") to false,
            Triple("1.2.0-rc1", true, "1.2.0") to true, // the release after its candidate
            Triple("1.2.0", true, null) to false, // a development build
            Triple("garbage", true, "1.2.0") to false,
        )
        for ((row, toast) in rows) {
            val (stored, setupDone, current) = row
            assertEquals(row.toString(), toast, showsUpdateToast(stored, current, setupDone))
        }
    }

    // Row 9: the toast asks by the card's rule, and an install from before the count asks without waiting for it.
    @Test
    fun `the toast asks for a coffee by the card's rule, Later aside`() {
        val store = SupportStore(Files.createTempDirectory("chairplay-toast")) { 0L }
        assertTrue("no time counted yet, an install from before", store.asks(unknownTime = true))
        assertFalse(store.asks())
        store.addCarPlay(60)
        assertFalse("a count started: it waits for two hours", store.asks(unknownTime = true))
        store.addCarPlay(7200)
        store.later()
        assertTrue(store.asks())
        assertFalse(store.due())
        store.supported()
        assertFalse(store.asks(unknownTime = true))
    }
}
