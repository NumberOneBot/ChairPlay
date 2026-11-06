package io.github.numberonebot.chairplay

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT.HANDLE
import java.io.Closeable
import kotlin.system.exitProcess

/**
 * One ChairPlay on this PC, whatever its folder. [InstanceLock] locks a copy's own folder, so the installed copy and a
 * portable one could both run and fight over the iPhone's Bluetooth channel and port 7000. A named mutex in the
 * user's session is held open while a copy runs: one that finds it there already knows another copy is running.
 * Only the handle's being open counts, not which thread owns it, so it lasts as long as the process does.
 */
internal class OneCopy private constructor(private val handle: HANDLE) : Closeable {
    override fun close() {
        Kernel32.INSTANCE.CloseHandle(handle)
    }

    companion object {
        const val NAME = "Local\\ChairPlay"
        /** How long a copy that is ending may still hold the name after letting go of its folder. */
        const val GOING_WAIT_MILLIS = 5_000L
        private const val POLL_MILLIS = 100L

        /**
         * Claims [name], waiting up to [timeoutMillis] for a copy that is going (the same folder's previous run lets
         * go of its folder lock before it ends); null while another copy still holds it.
         */
        fun claim(name: String = NAME, timeoutMillis: Long): OneCopy? {
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            while (true) {
                val handle = Kernel32.INSTANCE.CreateMutex(null, false, name) ?: return null
                if (Kernel32.INSTANCE.GetLastError() != WinError.ERROR_ALREADY_EXISTS) return OneCopy(handle)
                Kernel32.INSTANCE.CloseHandle(handle)
                if (System.nanoTime() >= deadline) return null
                Thread.sleep(POLL_MILLIS)
            }
        }
    }
}

/** The copy held for the whole run; never closed, so it goes with the process. */
private var held: OneCopy? = null

/**
 * Holds the claim for this run, or, with another copy of ChairPlay running from another folder, says so in a message
 * box ([show]) and ends this one before it starts a session.
 */
internal fun claimOneCopy(waitMillis: Long, show: (String) -> Unit = ::showStartFailure, exit: (Int) -> Unit = { exitProcess(it) }) {
    held = OneCopy.claim(timeoutMillis = waitMillis) ?: run {
        log("another ChairPlay is running from another folder; this one ends")
        runCatching { show("ChairPlay is already running from another folder. Close it, then start this one again.") }
        exit(1)
        return
    }
}
