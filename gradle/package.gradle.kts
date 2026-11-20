// What ships: the notices in the jar and the distribution, the start scripts, the Windows app image jpackage makes,
// and the app folder it is deployed into. Apply it after gradle/native.gradle.kts and gradle/web.gradle.kts, whose
// DLLs it copies into the image.

interface FileOps {
    @get:Inject val fs: FileSystemOperations
}

@Suppress("UNCHECKED_CAST")
val isMac = (project.extra["isMac"] as? Boolean) ?: System.getProperty("os.name").startsWith("Mac")
val isWindows = (project.extra["isWindows"] as? Boolean) ?: System.getProperty("os.name").startsWith("Windows")
val socketHandleOpens = project.extra["socketHandleOpens"] as List<String>
// The release version (chairplay.version, X.Y.Z), set in build.gradle.kts; null in a development build.
val appVersion = project.extra["appVersion"] as String?
val appNumericVersion = project.extra["appNumericVersion"] as String?
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
    val macOpts = if (isMac) listOf("-XstartOnFirstThread", "-Djava.awt.headless=true") else emptyList()
    defaultJvmOpts = socketHandleOpens + macOpts + "-Djava.library.path=__APP_HOME__/bin"
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
val appImageModules = listOfNotNull(
    "java.base", "java.desktop", "java.logging", "java.management", "java.naming", "java.sql", "java.xml",
    "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki",
    if (isWindows) "jdk.crypto.mscapi" else null,
    // The UI's local HTTP server (com.sun.net.httpserver).
    "jdk.httpserver",
    // The release checker uses java.net.http.HttpClient; the bundled runtime must include it too.
    "java.net.http",
)

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = if (isMac) "Builds build/jpackage/ChairPlay.app." else "Builds build/jpackage/ChairPlay with the launcher ChairPlay.exe."
    dependsOn("installDist")
    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    val installDir = layout.buildDirectory.dir("install/chairplay")
    val outDir = layout.buildDirectory.dir("jpackage")
    val upstream = layout.projectDirectory.dir("upstream")
    val notices = layout.projectDirectory.dir("notices")
    val thirdParty = layout.projectDirectory.file("THIRD_PARTY_NOTICES")
    val icon = if (isMac) layout.projectDirectory.file("packaging/macos/chairplay.icns")
        else layout.projectDirectory.file("packaging/chairplay.ico")
    val macResDir = temporaryDir.resolve("mac-resources")
    val ops = objects.newInstance<FileOps>()
    doFirst {
        ops.fs.delete { delete(outDir) }
        val jpackageBin = if (isWindows) "bin/jpackage.exe" else "bin/jpackage"
        val jpackagePath = launcher.get().metadata.installationPath.file(jpackageBin).asFile.path
        val numericVersion = appNumericVersion ?: (if (isMac) "1.0.0" else "0.0.0")
        val extraJavaOptions = if (isMac) {
            listOf(
                "--java-options", "-XstartOnFirstThread",
                "--java-options", "-Djava.awt.headless=true",
                "--java-options", "-Djava.library.path=\$APPDIR",
            )
        } else emptyList()
        val macArgs = if (isMac) {
            macResDir.mkdirs()
            val plistTemplate = layout.projectDirectory.file("packaging/macos/Info.plist").asFile.readText()
            val displayVersion = appVersion ?: numericVersion
            val substituted = plistTemplate.replace("<string>1.0.0</string>", "<string>$displayVersion</string>")
            macResDir.resolve("Info.plist").writeText(substituted)
            listOf(
                "--mac-package-identifier", "io.github.numberonebot.chairplay",
                "--resource-dir", macResDir.path,
            )
        } else emptyList()

        commandLine(
            listOf(
                jpackagePath,
                "--type", "app-image",
                "--name", "ChairPlay",
                "--app-version", numericVersion,
                "--vendor", "Oleksiy Strelets",
                "--input", installDir.get().dir("lib").asFile.path,
                "--main-jar", "chairplay.jar",
                "--main-class", "io.github.numberonebot.chairplay.MainKt",
                "--arguments", "run",
                "--icon", icon.asFile.path,
                "--add-modules", appImageModules.joinToString(","),
                "--dest", outDir.get().asFile.path,
            ) + macArgs +
                socketHandleOpens.flatMap { listOf("--java-options", it) } +
                extraJavaOptions,
        )
    }
    doLast {
        if (isMac) {
            val appDir = outDir.get().dir("ChairPlay.app")
            ops.fs.copy {
                from(installDir.get().file("bin/libspeex_echo.dylib"))
                from(installDir.get().file("bin/libchairplay_macos.dylib"))
                into(appDir.dir("Contents/app"))
            }
            ops.fs.copy {
                from(installDir.get().file("LICENSE-concentus"))
                from(installDir.get().file("LICENSE-speexdsp"))
                from(layout.projectDirectory.file("LICENSE"))
                from(thirdParty)
                from(upstream.dir("docs/licenses/dependencies")) { into("licenses") }
                from(notices) { into("licenses") }
                into(appDir.dir("Contents/Resources"))
            }
        } else {
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
                from(installDir.get().file("bin/chairplay_bridge.dll"))
                into(outDir.get().dir("ChairPlay"))
            }
            ops.fs.copy {
                from(installDir.get().dir("apac"))
                into(outDir.get().dir("ChairPlay/apac"))
            }
        }
    }
}
// The app folder holds the program next to its data (settings, keys, pairings), which the app reads from
// its exe's folder. deploy refreshes the program there and never touches the data. It is dist/ChairPlay
// in the project (outside build/, which clean wipes), or the path in the chairplay.home property: on the
// command line (-Pchairplay.home=<path>) or, to keep one for good, in %USERPROFILE%\.gradle\gradle.properties.
val appHome: Provider<String> = providers.gradleProperty("chairplay.home")
    .orElse(if (isMac) layout.projectDirectory.dir("dist/ChairPlay.app").asFile.path
            else layout.projectDirectory.dir("dist/ChairPlay").asFile.path)

// The app data, which the app writes next to its exe: settings, controls, window places and car icon, the AirPlay
// identity, pairings, the MFi files, the data logs, screenshots, the instance lock, and cache\ (the USB GPS developer
// tools and its cleanup journal). deploy keeps them, and the installer (gradle/installer.gradle.kts) refuses an image
// holding any of them.
val appDataFiles = listOf(
    "settings.properties", "controls.properties", "window.properties", "carplay-icon.png", "identity.properties",
    "pairings/**", "offline-mfi/**", "logs/**", "screenshots/**", "ChairPlay.lock", "webview2/**", "cache/**",
)
project.extra["appDataFiles"] = appDataFiles

val syncApp by tasks.registering(Sync::class) {
    description = "Copies the app image into the app folder (chairplay.home), keeping its settings, keys and pairings."
    dependsOn(jpackageImage)
    from(if (isMac) layout.buildDirectory.dir("jpackage/ChairPlay.app") else layout.buildDirectory.dir("jpackage/ChairPlay"))
    into(appHome)
    if (!isMac) {
        preserve { include(appDataFiles + "helper/**") }
    }
    filePermissions {
        user {
            write = true
            read = true
            if (isMac) execute = true
        }
    }
    val home = appHome.map { file(it) }
    doFirst {
        val target = home.get()
        if (isMac) {
            target.parentFile?.mkdirs()
        } else {
            val entries = target.list().orEmpty()
            val known = setOf("ChairPlay.exe", "settings.properties", "controls.properties", "carplay-icon.png", "identity.properties", "pairings", "offline-mfi")
            check(entries.isEmpty() || entries.any { it in known }) {
                "$target has files but no ChairPlay.exe or app data; refusing to replace its contents"
            }
            target.resolve("ChairPlay.exe").takeIf { it.isFile && !it.canWrite() }?.setWritable(true)
        }
    }
    doLast {
        if (isMac) {
            home.get().resolve("Contents/MacOS/ChairPlay").setExecutable(true)
        }
    }
}

val deployHelper by tasks.registering(Sync::class) {
    description = "Copies the frozen USB GPS helper into the app folder's helper\\ when it changed."
    if (isWindows) {
        dependsOn("usbGpsHelper")
        from(layout.buildDirectory.dir("usb-gps/dist/helper"))
        into(appHome.map { "$it/helper" })
        filePermissions { user { write = true } }
    }
}

tasks.register<JavaExec>("deploy") {
    group = "distribution"
    description = "Installs the app into the app folder (chairplay.home) with default settings where it has none."
    dependsOn(syncApp)
    if (isWindows) dependsOn(deployHelper)
    mainClass = "io.github.numberonebot.chairplay.config.AppDefaultsKt"
    classpath = mainSourceSet.runtimeClasspath
    jvmArgs("-Djava.awt.headless=true")
    val defaultTarget = if (isMac) {
        providers.provider {
            System.getProperty("chairplay.home") ?: System.getenv("CHAIRPLAY_HOME")
                ?: "${System.getProperty("user.home")}/Library/Application Support/ChairPlay"
        }
    } else {
        appHome
    }
    args(defaultTarget.get())
}

tasks.register<Exec>("launchApp") {
    group = "application"
    description = "Starts the app from the deployed location, detached from Gradle."
    val home = appHome.map { file(it) }
    doFirst {
        if (isMac) {
            check(home.get().exists()) { "no app bundle at ${home.get()}; run deploy first" }
            commandLine("/usr/bin/open", "-n", home.get().path)
        } else {
            check(home.get().resolve("ChairPlay.exe").isFile) { "no ChairPlay.exe in ${home.get()}; run deploy first" }
            val folder = home.get().path.replace("'", "''")
            commandLine("powershell", "-NoProfile", "-Command",
                "Start-Process -FilePath '$folder\\ChairPlay.exe' -WorkingDirectory '$folder'")
        }
    }
    mustRunAfter("deploy")
}

tasks.register("deployAndLaunch") {
    group = "application"
    description = "Installs the app into the app folder (deploy) and starts it there (launchApp)."
    dependsOn("deploy", "launchApp")
}

fun latestLog(logDir: File, prefix: String): File? =
    logDir.listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".log") }?.maxByOrNull { it.name }

fun registerCliTask(name: String, desc: String, arg: String, logPrefix: String) = tasks.register<Exec>(name) {
    group = "application"; description = desc
    val home = appHome.map { file(it) }
    val exe = if (isMac) home.map { it.resolve("Contents/MacOS/ChairPlay") } else home.map { it.resolve("ChairPlay.exe") }
    commandLine(exe.get().path, arg)
    doLast {
        val logDir = if (isMac) File(System.getProperty("user.home"), "Library/Application Support/ChairPlay/logs") else home.get().resolve("logs")
        val newest = latestLog(logDir, logPrefix)
        check(newest != null) { "no $logPrefix*.log in $logDir" }
        println(newest.readText())
    }
}

registerCliTask("listDevices", "Lists the Bluetooth radio, paired devices and sound devices with the deployed app.", "devices", "devices-")
registerCliTask("probeBt", "Probes Bluetooth radio and iAP2 link with paired iPhone.", "probe-bt", "probe-bt-")
