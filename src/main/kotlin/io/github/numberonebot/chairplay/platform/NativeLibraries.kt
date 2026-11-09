package io.github.numberonebot.chairplay.platform

import java.io.File

/** Where the app's own native libraries are and what the system calls them. */
object NativeLibraries {
    /** The system's file name for the library [base] ("chairplay_audio" is chairplay_audio.dll on Windows). */
    fun fileName(base: String): String = System.mapLibraryName(base)

    /** The folders on [libraryPath], then the one holding the packaged executable [appPath]. */
    fun searchDirs(
        libraryPath: String? = System.getProperty("java.library.path"),
        appPath: String? = System.getProperty("jpackage.app-path"),
    ): List<File> =
        libraryPath.orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }.map(::File) +
            listOfNotNull(appPath?.let { File(it).parentFile })

    /** The library [base] in the first of [dirs] that holds it, or null. */
    fun find(base: String, dirs: List<File> = searchDirs()): File? =
        dirs.map { File(it, fileName(base)) }.firstOrNull { it.isFile }
}
