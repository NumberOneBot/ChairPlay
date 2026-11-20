import java.net.URI
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

// The app's C and C++ parts, built by a pinned zig into build/native: upstream's SpeexDSP echo canceller and the
// HUD's maneuver renderer. build.gradle.kts puts the DLLs on the tests' and run's library path and into the
// distribution; gradle/web.gradle.kts builds the web view's DLL with the same zig (zigExe, zigRoot, zigVersion).

interface FileOps {
    @get:Inject val fs: FileSystemOperations
    @get:Inject val archives: ArchiveOperations
}

// Upstream's call echo canceller (SpeexEchoCanceller) is SpeexDSP in C behind JNI, which DiPlay builds with the
// Android NDK. Here zig's C compiler builds the same sources into speex_echo.dll. Zig is a pinned release,
// downloaded once into the Gradle user home and checked against the SHA-256 in
// https://ziglang.org/download/index.json; moving to another release means changing both values.
val isMac = (project.extra["isMac"] as? Boolean) ?: System.getProperty("os.name").startsWith("Mac")
val isWindows = (project.extra["isWindows"] as? Boolean) ?: System.getProperty("os.name").startsWith("Windows")
val isArm64 = (project.extra["isArm64"] as? Boolean) ?: (System.getProperty("os.arch") == "aarch64")

val zigVersion = "0.16.0"
val zigSha256 = "68659eb5f1e4eb1437a722f1dd889c5a322c9954607f5edcf337bc3684a75a7e"
val zigName = "zig-x86_64-windows-$zigVersion"
val zigRoot: File = gradle.gradleUserHomeDir.resolve("caches/chairplay/zig")
val zigExe: File = zigRoot.resolve("$zigName/zig.exe")
project.extra["zigVersion"] = zigVersion
project.extra["zigRoot"] = zigRoot
project.extra["zigExe"] = zigExe

if (isWindows) {
    tasks.register("installZig") {
        description = "Downloads zig $zigVersion into the Gradle user home, unless it is there already."
        val ops = objects.newInstance<FileOps>()
        val root = zigRoot
        val exe = zigExe
        val name = zigName
        val url = "https://ziglang.org/download/$zigVersion/$name.zip"
        val sha256 = zigSha256
        // No declared output: Gradle would create the output's folder before the run, and the finished zig folder
        // is moved into place in one step. Once zig is there, the task only checks for zig.exe.
        doLast {
            if (exe.isFile) return@doLast
            root.mkdirs()
            // Unique names, so builds in two checkouts at once do not share half-written files.
            val part = "$name.${UUID.randomUUID()}.part"
            val zip = root.resolve("$part.zip")
            val staging = root.resolve(part)
            try {
                logger.lifecycle("Downloading $url")
                val digest = MessageDigest.getInstance("SHA-256")
                val connection = URI(url).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                }
                connection.getInputStream().use { input ->
                    DigestOutputStream(zip.outputStream(), digest).use { input.copyTo(it) }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual == sha256) { "$url has SHA-256 $actual, expected $sha256" }
                ops.fs.copy {
                    from(ops.archives.zipTree(zip))
                    into(staging)
                }
                check(staging.resolve("$name/zig.exe").isFile) { "$url has no $name/zig.exe" }
                // An empty folder left by an interrupted run would block the move.
                exe.parentFile.delete()
                try {
                    Files.move(staging.resolve(name).toPath(), exe.parentFile.toPath())
                } catch (e: FileSystemException) {
                    // Another build installed it first.
                    if (!exe.isFile) throw e
                }
            } finally {
                ops.fs.delete { delete(zip, staging) }
            }
        }
    }
}

// The sources and flags of upstream's shared/src/main/jni/Android.mk (module speex_echo). The JNI headers come
// from the build's JDK 17 toolchain. Zig links the DLL against Windows' own C runtime (UCRT), so it needs no
// DLL that Windows 10 or later lacks.
val speexCflags = listOf("-O2", "-DFLOATING_POINT", "-DUSE_KISS_FFT", "-DEXPORT=", "-Wno-unused-parameter")
val speexSources = listOf("speex_echo_jni.c") +
    listOf("mdf.c", "preprocess.c", "fftwrap.c", "kiss_fft.c", "kiss_fftr.c", "filterbank.c").map { "speexdsp/$it" }
val nativeDir = layout.buildDirectory.dir("native")
val javaToolchains = extensions.getByType<JavaToolchainService>()

if (isWindows) {
    tasks.register<Exec>("speexEchoDll") {
        group = "build"
        description = "Compiles upstream's SpeexDSP echo canceller into build/native/speex_echo.dll."
        dependsOn("installZig")
        val jni = layout.projectDirectory.dir("upstream/shared/src/main/jni")
        val jdk = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }.map { it.metadata }
        val dll = nativeDir.map { it.file("speex_echo.dll") }
        val work = temporaryDir
        val ops = objects.newInstance<FileOps>()
        inputs.files(speexSources.map { jni.file(it) }).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(fileTree(jni.dir("speexdsp")) { include("**/*.h") }).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("zig", zigVersion)
        inputs.property("cflags", speexCflags)
        inputs.property("jdk", jdk.map { it.javaRuntimeVersion })
        outputs.file(dll)
        workingDir(jni)
        environment("ZIG_GLOBAL_CACHE_DIR", zigRoot.resolve("cache").path)
        environment("ZIG_LOCAL_CACHE_DIR", work.resolve("zig-cache").path)
        doFirst {
            val include = jdk.get().installationPath.dir("include").asFile
            commandLine(
                listOf(zigExe.path, "cc", "-target", "x86_64-windows-gnu", "-shared") + speexCflags +
                    listOf("-Ispeexdsp", "-Ispeexdsp/include", "-I${include.path}", "-I${include.resolve("win32").path}") +
                    speexSources + listOf("-o", work.resolve("speex_echo.dll").path),
            )
        }
        doLast {
            ops.fs.copy {
                from(work.resolve("speex_echo.dll"))
                into(dll.get().asFile.parentFile)
            }
        }
    }
} else if (isMac) {
    tasks.register<Exec>("speexEchoDll") {
        group = "build"
        description = "Compiles upstream's SpeexDSP echo canceller into build/native/libspeex_echo.dylib."
        val jni = layout.projectDirectory.dir("upstream/shared/src/main/jni")
        val jdk = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }.map { it.metadata }
        val dylib = nativeDir.map { it.file("libspeex_echo.dylib") }
        val work = temporaryDir
        val ops = objects.newInstance<FileOps>()
        val arch = if (isArm64) "arm64" else "x86_64"
        inputs.files(speexSources.map { jni.file(it) }).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(fileTree(jni.dir("speexdsp")) { include("**/*.h") }).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("cflags", speexCflags)
        inputs.property("arch", arch)
        inputs.property("jdk", jdk.map { it.javaRuntimeVersion })
        outputs.file(dylib)
        workingDir(jni)
        doFirst {
            val include = jdk.get().installationPath.dir("include").asFile
            commandLine(
                listOf("clang", "-shared", "-O2", "-arch", arch) + speexCflags +
                    listOf("-Ispeexdsp", "-Ispeexdsp/include", "-I${include.path}", "-I${include.resolve("darwin").path}") +
                    speexSources + listOf("-o", work.resolve("libspeex_echo.dylib").path),
            )
        }
        doLast {
            ops.fs.copy {
                from(work.resolve("libspeex_echo.dylib"))
                into(dylib.get().asFile.parentFile)
            }
        }
    }
}

// The HUD's maneuver renderer: the MHI2Q project's maneuver_render (native/maneuver, see its README) with
// ChairPlay's engine, GL layer and JNI entry points (native/maneuver/chairplay), built by the same zig into
// chairplay_maneuver.dll. It goes wherever speex_echo.dll goes: build/native for tests and run, bin/ of the
// distribution, the app image's root. The C++ part links zig's static libc++, so the DLL needs only Windows' UCRT.
val maneuverCSources = listOf("render.c", "maneuver.c", "route_path.c") +
    listOf("engine.c", "gl_core.c", "jni.c", "stderr_capture.c").map { "chairplay/$it" }
val maneuverCxxSources = listOf("scene.cpp", "geometry.cpp", "layout.cpp", "lane_panel.cpp").map { "scene/$it" }
// Upstream's flags (maneuver_render/Makefile), plus the stderr capture forced into every C file (C++ files print nothing).
val maneuverDefines = listOf("-DPLATFORM_CHAIRPLAY")
val maneuverCFlags = listOf("-std=gnu99", "-Wall", "-Wextra", "-Wno-unused-parameter", "-include", "chairplay/stderr_capture.h")
val maneuverCxxFlags = listOf("-std=c++11", "-fno-exceptions", "-fno-rtti", "-Wall")

if (isWindows) {
    tasks.register<Exec>("maneuverDll") {
        group = "build"
        description = "Compiles the HUD's maneuver renderer into build/native/chairplay_maneuver.dll."
        dependsOn("installZig")
        val src = layout.projectDirectory.dir("native/maneuver")
        val jdk = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }.map { it.metadata }
        val dll = nativeDir.map { it.file("chairplay_maneuver.dll") }
        val work = temporaryDir
        val ops = objects.newInstance<FileOps>()
        inputs.files(fileTree(src) { include("**/*.c", "**/*.cpp", "**/*.h", "**/*.hpp") }).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("zig", zigVersion)
        inputs.property("flags", maneuverDefines + maneuverCFlags + maneuverCxxFlags)
        inputs.property("jdk", jdk.map { it.javaRuntimeVersion })
        outputs.file(dll)
        workingDir(src)
        environment("ZIG_GLOBAL_CACHE_DIR", zigRoot.resolve("cache").path)
        environment("ZIG_LOCAL_CACHE_DIR", work.resolve("zig-cache").path)
        doFirst {
            val include = jdk.get().installationPath.dir("include").asFile
            commandLine(
                listOf(
                    zigExe.path, "build-lib", "-dynamic", "-target", "x86_64-windows-gnu", "-O", "ReleaseFast",
                    "-lc", "-lc++", "-lopengl32", "--name", "chairplay_maneuver",
                    "-femit-bin=${work.resolve("chairplay_maneuver.dll").path}",
                    "-I.", "-Ichairplay", "-I${include.path}", "-I${include.resolve("win32").path}",
                ) + maneuverDefines + listOf("-cflags") + maneuverCFlags + listOf("--") + maneuverCSources +
                    listOf("-cflags") + maneuverCxxFlags + listOf("--") + maneuverCxxSources,
            )
        }
        doLast {
            ops.fs.copy {
                from(work.resolve("chairplay_maneuver.dll"))
                into(dll.get().asFile.parentFile)
            }
        }
    }
}

// The tests and run load the DLLs from build/native; a new build of the maneuver renderer runs the tests again, as its
// render test runs it. In the distribution they go into bin/, not lib/: lib/ is jpackage's input, whose files land in
// the image's app/ folder, which is not on the launcher's library path.
tasks.named<Test>("test") {
    dependsOn("speexEchoDll")
    systemProperty("java.library.path", nativeDir.get().asFile.path)
    if (isWindows) {
        inputs.files(tasks.named("maneuverDll")).withPropertyName("maneuverDll")
    }
}

tasks.named<JavaExec>("run") {
    if (isWindows) {
        dependsOn("speexEchoDll", "maneuverDll")
    } else {
        dependsOn("speexEchoDll")
    }
    systemProperty("java.library.path", nativeDir.get().asFile.path)
}

the<DistributionContainer>()["main"].contents {
    from(tasks.named("speexEchoDll")) { into("bin") }
    if (isWindows) {
        from(tasks.named("maneuverDll")) { into("bin") }
    }
}
