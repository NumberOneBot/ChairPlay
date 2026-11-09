package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.ShellAPI
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

private const val SEE_MASK_NOCLOSEPROCESS = 0x40
private const val SEE_MASK_NOASYNC = 0x100

/**
 * Starts [program] with [parameters] as the administrator, which makes Windows ask for the consent, with no window,
 * and waits for it to end. Its exit code; null when the consent was refused, it could not start, or [timeoutMillis]
 * passed. The shell is asked on an apartment thread of its own, as ShellExecuteEx wants COM set up.
 */
internal fun runElevated(program: String, parameters: String, timeoutMillis: Long): Int? =
    onComThread("elevated", timeoutMillis + 5_000, Ole32.COINIT_APARTMENTTHREADED) {
        val info = ShellAPI.SHELLEXECUTEINFO().apply {
            cbSize = size()
            fMask = SEE_MASK_NOCLOSEPROCESS or SEE_MASK_NOASYNC
            lpVerb = "runas"
            lpFile = program
            lpParameters = parameters
            nShow = WinUser.SW_HIDE
        }
        if (!Shell32.INSTANCE.ShellExecuteEx(info)) return@onComThread null
        val process = info.hProcess ?: return@onComThread null
        try {
            val waited = Kernel32.INSTANCE.WaitForSingleObject(process, timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (waited != WinBase.WAIT_OBJECT_0) return@onComThread null
            val code = IntByReference()
            if (Kernel32.INSTANCE.GetExitCodeProcess(process, code)) code.value else null
        } finally {
            Kernel32.INSTANCE.CloseHandle(process)
        }
    }
