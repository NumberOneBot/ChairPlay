package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Writes [text] as UTF-8 to a new file in the temp folder and runs [use] with its path while this process holds the
 * file open, sharing it for reading only: a program started as the administrator on it (netsh -f) reads exactly what
 * was written, as no other program of the user's can change, replace, delete, or rename it meanwhile. The file is
 * created here, never opened if it already exists, and deleted after.
 */
internal fun <T> withLockedFile(text: String, use: (Path) -> T): T {
    val path = Path.of(System.getProperty("java.io.tmpdir"), "chairplay-${UUID.randomUUID()}.txt")
    val handle = Kernel32.INSTANCE.CreateFile(
        path.toString(), WinNT.GENERIC_WRITE, WinNT.FILE_SHARE_READ, null, WinNT.CREATE_NEW, WinNT.FILE_ATTRIBUTE_NORMAL, null,
    )
    check(handle != WinBase.INVALID_HANDLE_VALUE) { "cannot create $path" }
    try {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val written = IntByReference()
        check(Kernel32.INSTANCE.WriteFile(handle, bytes, bytes.size, written, null) && written.value == bytes.size) {
            "cannot write $path"
        }
        return use(path)
    } finally {
        Kernel32.INSTANCE.CloseHandle(handle)
        Files.deleteIfExists(path)
    }
}
