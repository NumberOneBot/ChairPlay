// What ships: the notices in the jar and the distribution, the start scripts, the Windows app image jpackage makes,
// and the app folder it is deployed into. Apply it after gradle/native.gradle.kts and gradle/web.gradle.kts, whose
// DLLs it copies into the image.

interface FileOps {
    @get:Inject val fs: FileSystemOperations
}

@Suppress("UNCHECKED_CAST")
val socketHandleOpens = project.extra["socketHandleOpens"] as List<String>
// The release version (chairplay.version, X.Y.Z), set in build.gradle.kts; null in a development build.
val appVersion = project.extra["appVersion"] as String?
val javaToolchains = extensions.getByType<JavaToolchainService>()
val mainSourceSet = the<SourceSetContainer>()["main"]

// Concentus is BSD-licensed and its notice must travel with the binaries: inside the jar, and next to it in
// the distribution.
val concentusLicence = layout.buildDirectory.file("upstream-core/org/concentus/LICENSE")

tasks.named<Jar>("jar") {
    from(concentusLicence) {
        rename { "LICENSE-concentus" }
        into("META-INF")
    }
}

// SpeexDSP is BSD-licensed too; its notice goes next to speex_echo.dll wherever that is shipped.
val speexLicence = layout.projectDirectory.file("upstream/shared/src/main/jni/speexdsp/COPYING")

the<DistributionContainer>()["main"].contents {
    from(concentusLicence) { rename { "LICENSE-concentus" } }
    from(speexLicence) { rename { "LICENSE-speexdsp" } }
}

// The start scripts load speex_echo.dll from bin/. The placeholder becomes the scripts' own APP_HOME variable,
// which both scripts set before DEFAULT_JVM_OPTS.
tasks.named<CreateStartScripts>("startScripts") {
    defaultJvmOpts = socketHandleOpens + "-Djava.library.path=__APP_HOME__/bin"
    doLast {
        unixScript.writeText(unixScript.readText().replace("__APP_HOME__", "'\"\$APP_HOME\"'"))
        windowsScript.writeText(windowsScript.readText().replace("__APP_HOME__/bin", "%APP_HOME%\\bin"))
    }
}

// A self-contained Windows app image: the launcher, a trimmed runtime and the jars, no installer tooling
// needed. The module list is what `jdeps --print-module-deps` reports for the installed jars (java.base,
// java.desktop, java.management, java.naming, java.sql, jdk.unsupported) plus modules reached only
// reflectively: java.logging (JmDNS, SLF4J), java.xml, and the security providers, which are looked up by
// name. A missing provider would break MFi signing at runtime only.
val appImageModules = listOf(
    "java.base", "java.desktop", "java.logging", "java.management", "java.naming", "java.sql", "java.xml",
    "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.crypto.mscapi",
    // The UI's local HTTP server (com.sun.net.httpserver).
    "jdk.httpserver",
)

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds build/jpackage/ChairPlay with the launcher ChairPlay.exe."
    dependsOn("installDist")
    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    val installDir = layout.buildDirectory.dir("install/chairplay")
    val outDir = layout.buildDirectory.dir("jpackage")
    val upstream = layout.projectDirectory.dir("upstream")
    val notices = layout.projectDirectory.dir("notices")
    val thirdParty = layout.projectDirectory.file("THIRD_PARTY_NOTICES")
    // Written by packaging/icon/make_icon.py, as are the window icon PNGs in the resources.
    val icon = layout.projectDirectory.file("packaging/chairplay.ico")
    val ops = objects.newInstance<FileOps>()
    // Command-line arguments given to ChairPlay.exe replace the default `run`, so `devices` and
    // `probe-bt` work by passing them explicitly.
    doFirst {
        ops.fs.delete { delete(outDir) }
        commandLine(
            launcher.get().metadata.installationPath.file("bin/jpackage.exe").asFile.path,
            "--type", "app-image",
            "--name", "ChairPlay",
            "--app-version", appVersion ?: "0.0.0",
            "--vendor", "Oleksiy Strelets",
            "--input", installDir.get().dir("lib").asFile.path,
            "--main-jar", "chairplay.jar",
            "--main-class", "io.github.numberonebot.chairplay.MainKt",
            "--arguments", "run",
            "--icon", icon.asFile.path,
            "--add-modules", appImageModules.joinToString(","),
            "--dest", outDir.get().asFile.path,
            *socketHandleOpens.flatMap { listOf("--java-options", it) }.toTypedArray(),
        )
    }
    // jpackage on JDK 17 has no --app-content, so notices are copied into the image afterwards, and so are the
    // DLLs: into the image's root next to ChairPlay.exe, because the launcher sets no java.library.path and the
    // JVM's default one on Windows starts with the folder of the running exe.
    doLast {
        ops.fs.copy {
            from(installDir.get().file("bin/speex_echo.dll"))
            from(installDir.get().file("LICENSE-concentus"))
            from(installDir.get().file("LICENSE-speexdsp"))
            from(layout.projectDirectory.file("LICENSE"))
            from(thirdParty)
            from(upstream.dir("docs/licenses/dependencies")) { into("licenses") }
            from(notices) { into("licenses") }
            into(outDir.get().dir("ChairPlay"))
        }
        ops.fs.copy {
            from(installDir.get().file("bin/chairplay_webview.dll"), installDir.get().file("bin/WebView2Loader.dll"))
            from(installDir.get().file("bin/chairplay_maneuver.dll"))
            from(installDir.get().file("bin/chairplay_audio.dll"))
            into(outDir.get().dir("ChairPlay"))
        }
        ops.fs.copy {
            from(installDir.get().dir("apac"))
            into(outDir.get().dir("ChairPlay/apac"))
        }
    }
}

// The app folder holds the program next to its data (settings, keys, pairings), which the app reads from
// its exe's folder. deploy refreshes the program there and never touches the data. It is dist/ChairPlay
// in the project (outside build/, which clean wipes), or the path in the chairplay.home property: on the
// command line (-Pchairplay.home=<path>) or, to keep one for good, in %USERPROFILE%\.gradle\gradle.properties.
val appHome: Provider<String> = providers.gradleProperty("chairplay.home")
    .orElse(layout.projectDirectory.dir("dist/ChairPlay").asFile.path)

// The app data, which the app writes next to its exe: settings, controls, window places and car icon, the AirPlay
// identity, pairings, the MFi files, the data logs, screenshots and the instance lock. deploy keeps them, and the
// installer (gradle/installer.gradle.kts) refuses an image holding any of them.
val appDataFiles = listOf(
    "settings.properties", "controls.properties", "window.properties", "carplay-icon.png", "identity.properties",
    "pairings/**", "offline-mfi/**", "logs/**", "screenshots/**", "ChairPlay.lock", "gps-almanac.yuma",
)
project.extra["appDataFiles"] = appDataFiles

val syncApp by tasks.registering(Sync::class) {
    description = "Copies the app image into the app folder (chairplay.home), keeping its settings, keys and pairings."
    dependsOn(jpackageImage)
    from(layout.buildDirectory.dir("jpackage/ChairPlay"))
    into(appHome)
    preserve { include(appDataFiles) }
    // jpackage leaves ChairPlay.exe read-only, and Windows refuses to replace a read-only file: the copies stay
    // writable, so the next deploy can replace them.
    filePermissions { user { write = true } }
    // Sync deletes whatever else is there, so a mistyped folder must not be one with other files in it.
    val home = appHome.map { file(it) }
    doFirst {
        val target = home.get()
        val entries = target.list().orEmpty()
        val known = setOf("ChairPlay.exe", "settings.properties", "controls.properties", "carplay-icon.png", "identity.properties", "pairings", "offline-mfi")
        check(entries.isEmpty() || entries.any { it in known }) {
            "$target has files but no ChairPlay.exe or app data; refusing to replace its contents"
        }
        // A read-only exe left by an earlier deploy.
        target.resolve("ChairPlay.exe").takeIf { it.isFile && !it.canWrite() }?.setWritable(true)
    }
}

// Then writes the default settings, controls and car icon the folder lacks, so the Wi-Fi can be filled in
// before the first start; the templates live in the app's code (AppDefaults.kt).
tasks.register<JavaExec>("deploy") {
    group = "distribution"
    description = "Installs the app into the app folder (chairplay.home) with default settings where it has none."
    dependsOn(syncApp)
    mainClass = "io.github.numberonebot.chairplay.config.AppDefaultsKt"
    classpath = mainSourceSet.runtimeClasspath
    jvmArgs("-Djava.awt.headless=true")
    args(appHome.get())
}

// Starts the deployed app detached, so it is stopped by closing its window: a task runner's stop button
// would kill it hard, and the iPhone then ignores new sessions until it restarts.
tasks.register<Exec>("launchApp") {
    group = "application"
    description = "Starts ChairPlay.exe from the app folder (chairplay.home), detached from Gradle."
    val home = appHome.map { file(it) }
    doFirst {
        check(home.get().resolve("ChairPlay.exe").isFile) { "no ChairPlay.exe in ${home.get()}; run deploy first" }
    }
    // The app has no console: it logs into its logs folder and closes with its windows.
    val folder = home.get().path.replace("'", "''")
    commandLine("powershell", "-NoProfile", "-Command",
        "Start-Process -FilePath '$folder\\ChairPlay.exe' -WorkingDirectory '$folder'")
}

tasks.register<Exec>("listDevices") {
    group = "application"
    description = "Lists the Bluetooth radio, paired devices and sound devices with the deployed app."
    val home = appHome.map { file(it) }
    commandLine(home.get().resolve("ChairPlay.exe").path, "devices")
    // The app has no console: it wrote the list to logs/devices-<time>.log, which is shown here.
    doLast {
        val newest = home.get().resolve("logs").listFiles { f -> f.name.startsWith("devices-") && f.name.endsWith(".log") }
            ?.maxByOrNull { it.name }
        check(newest != null) { "no devices-*.log in ${home.get().resolve("logs")}" }
        println(newest.readText())
    }
}
