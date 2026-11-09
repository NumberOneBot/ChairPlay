// ChairPlay's build: the app and its tests, with DiPlay's core copied in from upstream/ (gradle/upstream.gradle.kts),
// the C and C++ parts zig builds (gradle/native.gradle.kts), the web UI and its web view (gradle/web.gradle.kts), and
// the app image deployed into the app folder (gradle/package.gradle.kts).

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.jmdns:jmdns:3.6.3")
    implementation("org.slf4j:slf4j-jdk14:2.0.7")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    // Native classifiers are pinned to windows-x86_64: the *-platform artifacts of javacpp presets pull
    // every OS, hundreds of megabytes this Windows-only app never loads.
    implementation("org.bytedeco:javacpp:1.5.12")
    implementation("org.bytedeco:javacpp:1.5.12:windows-x86_64")
    // 7.1.1 fixes the HEVC WPP slice-thread deadlock in software decoding (FFmpeg 79c47dfd).
    implementation("org.bytedeco:ffmpeg:7.1.1-1.5.12")
    implementation("org.bytedeco:ffmpeg:7.1.1-1.5.12:windows-x86_64")
    implementation("org.lwjgl:lwjgl:3.3.4")
    implementation("org.lwjgl:lwjgl-glfw:3.3.4")
    implementation("org.lwjgl:lwjgl-opengl:3.3.4")
    implementation("org.lwjgl:lwjgl-nanovg:3.3.4")
    runtimeOnly("org.lwjgl:lwjgl:3.3.4:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.4:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:3.3.4:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-nanovg:3.3.4:natives-windows")
    testImplementation("junit:junit:4.13.2")
}

// TcpLiveness reaches a socket's Winsock handle by reflection to set upstream's keep-alive timing, which
// JDK 17 does not offer on Windows. Every launcher passes the same opens: the start scripts, the tests
// and the app image.
val socketHandleOpens = listOf("java.base/java.net", "java.base/java.io").map { "--add-opens=$it=ALL-UNNAMED" }
extra["socketHandleOpens"] = socketHandleOpens

application {
    mainClass.set("io.github.numberonebot.chairplay.MainKt")
    applicationDefaultJvmArgs = socketHandleOpens
}

tasks.test {
    jvmArgs(socketHandleOpens)
    // Four test JVMs take the Kotlin tests from 88 s to 46 s on 12 cores; more add little, as DesktopBonjourTest's
    // mDNS waits hold one of them for 21 s. A class runs whole in one JVM, so a class's tests that share a fixed name
    // (the bridge's status file, port 5353) never run beside each other.
    maxParallelForks = minOf(4, Runtime.getRuntime().availableProcessors())
}

apply(from = "gradle/upstream.gradle.kts")
apply(from = "gradle/apap-upstream.gradle.kts")
apply(from = "gradle/spatial-session.gradle.kts")
apply(from = "gradle/timing-upstream.gradle.kts")

sourceSets {
    test {
        kotlin {
            srcDir(tasks.named("upstreamCoreTests"))
        }
    }
    main {
        // Concentus (Opus) is Java: a Kotlin srcDir alone would leave it uncompiled.
        java {
            srcDir(tasks.named("upstreamCore"))
        }
        kotlin {
            srcDir(tasks.named("upstreamCore"))
            srcDir("src/shim/kotlin")
        }
    }
}

// The About page's build: this repository's commit and the DiPlay core's (the upstream submodule), each with its
// date, in build-info.properties (BuildInfo). The commits move without a file Gradle watches, so the task runs every
// build; it writes the file only when that changes, which leaves the jar up to date otherwise.
val buildInfoResources = layout.buildDirectory.dir("generated/build-info")
// The release version, X.Y.Z, from -Pchairplay.version=...; a build without it is a development build.
val appVersion: String? = providers.gradleProperty("chairplay.version").orNull?.also {
    require(Regex("""\d+\.\d+\.\d+""").matches(it)) {
        "chairplay.version must be three numbers separated by dots, like 0.1.0 (without a v); got '$it'"
    }
}
extra["appVersion"] = appVersion
val buildInfo by tasks.registering {
    group = "build"
    description = "Writes the app's version and the app's and the DiPlay core's commits for the About page."
    val out = buildInfoResources.map { it.file("build-info.properties") }
    val root = layout.projectDirectory.asFile
    val version = appVersion
    inputs.property("version", version ?: "")
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        fun commit(dir: File): List<String> = runCatching {
            val process = ProcessBuilder("git", "log", "-1", "--format=%h %cs", "--abbrev=8").directory(dir).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0) text.split(' ') else emptyList()
        }.getOrDefault(emptyList())
        val lines = listOfNotNull(version?.let { "app.version=$it" }) + listOf("app" to commit(root), "core" to commit(root.resolve("upstream")))
            .filter { (_, commit) -> commit.size == 2 }
            .flatMap { (name, commit) -> listOf("$name.commit=${commit[0]}", "$name.date=${commit[1]}") }
        val text = lines.joinToString("") { "$it\n" }
        val file = out.get().asFile
        if (!file.exists() || file.readText() != text) {
            file.parentFile.mkdirs()
            file.writeText(text)
        }
    }
}

sourceSets {
    main {
        resources {
            srcDir(files(buildInfoResources).builtBy(buildInfo))
        }
    }
}

// The car button's default icon, drawn by packaging/icon/make_icon.py: the app writes it into its folder (CarIcon).
tasks.processResources {
    from("packaging/icon/carplay-icon.png") { into("icons") }
}

apply(from = "gradle/native.gradle.kts")
apply(from = "gradle/spatial.gradle.kts")
apply(from = "gradle/bridge.gradle.kts")
apply(from = "gradle/apac.gradle.kts")
apply(from = "gradle/web.gradle.kts")
apply(from = "gradle/package.gradle.kts")
apply(from = "gradle/installer.gradle.kts")
