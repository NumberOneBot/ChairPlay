package io.github.numberonebot.chairplay.platform.macos

import io.github.numberonebot.chairplay.platform.OneCopyClaim
import java.io.Closeable
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class MacOneCopy(
    private val lockDir: () -> Path = {
        Path.of(System.getProperty("user.home"), "Library", "Application Support", "ChairPlay")
    },
) : OneCopyClaim {

    override fun claim(name: String, timeoutMillis: Long): Closeable? {
        val dir = lockDir()
        runCatching { Files.createDirectories(dir) }
        val lockFile = dir.resolve("$name.lock")
        val channel = runCatching {
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }.getOrNull() ?: return null

        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock != null) {
                return Closeable {
                    runCatching { lock.release() }
                    runCatching { channel.close() }
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                runCatching { channel.close() }
                return null
            }
            Thread.sleep(25)
        }
    }
}
