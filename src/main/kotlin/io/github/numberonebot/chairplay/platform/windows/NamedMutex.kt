package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT.HANDLE
import java.io.Closeable

/**
 * A named mutex in the user's session, held open while it is wanted. Only the handle's being open counts, not which
 * thread owns it, so it lasts as long as the process does.
 */
internal class NamedMutex private constructor(private val handle: HANDLE) : Closeable {
    override fun close() {
        Kernel32.INSTANCE.CloseHandle(handle)
    }

    companion object {
        private const val POLL_MILLIS = 100L

        /**
         * Claims [name], waiting up to [timeoutMillis] for a copy that is going (the same folder's previous run lets
         * go of its folder lock before it ends); null while another copy still holds it.
         */
        fun claim(name: String, timeoutMillis: Long): NamedMutex? {
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            while (true) {
                val handle = Kernel32.INSTANCE.CreateMutex(null, false, name) ?: return null
                if (Kernel32.INSTANCE.GetLastError() != WinError.ERROR_ALREADY_EXISTS) return NamedMutex(handle)
                Kernel32.INSTANCE.CloseHandle(handle)
                if (System.nanoTime() >= deadline) return null
                Thread.sleep(POLL_MILLIS)
            }
        }
    }
}
