import java.net.URI
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

// The web UI (the start and settings screens, in webui/: React and TypeScript, built by Vite) and the web view it
// shows in: WebView2 as a child of the main window, through chairplay_webview.dll, built by gradle/native.gradle.kts's
// zig. Apply it after that script.

interface FileOps {
    @get:Inject val fs: FileSystemOperations
}

/**
 * A pinned download, as installZig does it for zig: fetched once into [target] under the Gradle user home, checked
 * against [sha256], unpacked beside it and moved into place in one step. [topFolder] is the archive's folder that
 * becomes [target] (none: the whole archive), [marker] a file that shows the install is complete.
 */
abstract class InstallArchive : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input @get:Optional abstract val topFolder: Property<String>
    /** Only these paths of the archive (Ant patterns); all of it when empty. */
    @get:Input abstract val only: ListProperty<String>
    @get:Input abstract val marker: Property<String>
    // Not an output: Gradle would create the folder before the run, and it is moved into place whole.
    @get:Internal abstract val target: Property<File>
    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val archives: ArchiveOperations

    @TaskAction
    fun install() {
        val dir = target.get()
        if (dir.resolve(marker.get()).isFile) return
        val root = dir.parentFile.apply { mkdirs() }
        // Unique names, so builds in two checkouts at once do not share half-written files.
        val part = "${dir.name}.${UUID.randomUUID()}.part"
        val archive = root.resolve("$part.zip")
        val staging = root.resolve(part)
        try {
            logger.lifecycle("Downloading ${url.get()}")
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = URI(url.get()).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            connection.getInputStream().use { input ->
                DigestOutputStream(archive.outputStream(), digest).use { input.copyTo(it) }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == sha256.get()) { "${url.get()} has SHA-256 $actual, expected ${sha256.get()}" }
            val patterns = only.get()
            fs.copy {
                from(archives.zipTree(archive)) { if (patterns.isNotEmpty()) include(patterns) }
                into(staging)
            }
            val unpacked = topFolder.orNull?.let { staging.resolve(it) } ?: staging
            check(unpacked.resolve(marker.get()).isFile) { "${url.get()} has no ${marker.get()}" }
            // An empty folder left by an interrupted run would block the move.
            dir.delete()
            try {
                Files.move(unpacked.toPath(), dir.toPath())
            } catch (e: FileSystemException) {
                // Another build installed it first.
                if (!dir.resolve(marker.get()).isFile) throw e
            }
        } finally {
            fs.delete { delete(archive, staging) }
        }
    }
}

// Node runs the UI's build and tests only; nothing of it ships. The release is pinned like zig, with the SHA-256
// from https://nodejs.org/dist/v<version>/SHASUMS256.txt. npm checks every package against the integrity hashes
// in webui/package-lock.json, and installs them without running their install scripts.
val nodeVersion = "24.22.1"
val nodeDir: File = gradle.gradleUserHomeDir.resolve("caches/chairplay/node/node-v$nodeVersion-win-x64")
val webui = layout.projectDirectory.dir("webui")
val webuiResources = layout.buildDirectory.dir("webui/resources")

val installNode by tasks.registering(InstallArchive::class) {
    description = "Downloads Node.js $nodeVersion into the Gradle user home, unless it is there already."
    url = "https://nodejs.org/dist/v$nodeVersion/node-v$nodeVersion-win-x64.zip"
    sha256 = "d2046d28c2c3dcd211ac5145c0bf30e2047ef9dfb306693c8c6e2eccd8b95fa5"
    topFolder = "node-v$nodeVersion-win-x64"
    marker = "node.exe"
    target = nodeDir
}

/** Runs [args] with the pinned node in webui/, with that node first on the PATH for the tools it starts. */
fun Exec.node(vararg args: String) {
    dependsOn(installNode)
    workingDir(webui)
    environment("PATH", nodeDir.path + File.pathSeparator + System.getenv("PATH"))
    // npm's cache stays beside node instead of in %LOCALAPPDATA%.
    environment("npm_config_cache", nodeDir.resolveSibling("npm-cache").path)
    environment("npm_config_update_notifier", "false")
    commandLine(listOf(nodeDir.resolve("node.exe").path) + args)
}

val npmCi by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs the UI's packages from webui/package-lock.json into webui/node_modules."
    node(nodeDir.resolve("node_modules/npm/bin/npm-cli.js").path, "ci", "--ignore-scripts", "--no-audit", "--no-fund")
    inputs.files(webui.file("package.json"), webui.file("package-lock.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    // npm's own record of the install, rather than the many thousand files beside it.
    outputs.file(webui.file("node_modules/.package-lock.json"))
}

// The page draws with the HUD's font files and shows the window's icon, which Vite copies from the app's resources.
val webuiSources = files(
    webui.dir("src"), webui.file("index.html"), webui.file("vite.config.ts"), webui.file("tsconfig.json"),
    webui.file("package-lock.json"), layout.projectDirectory.dir("src/main/resources/fonts"),
    layout.projectDirectory.dir("src/main/resources/icons"),
)

val webuiTypecheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Type-checks the UI's TypeScript."
    dependsOn(npmCi)
    node("node_modules/typescript/bin/tsc", "--noEmit", "-p", "tsconfig.json")
    val done = layout.buildDirectory.file("webui/typecheck.done")
    inputs.files(webuiSources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(done)
    doLast { done.get().asFile.writeText("type-checked\n") }
}

val webuiBuild by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the UI with Vite into build/webui/resources/ui, which goes into the jar."
    dependsOn(npmCi, webuiTypecheck)
    val out = webuiResources.map { it.dir("ui") }
    node("node_modules/vite/bin/vite.js", "build", "--outDir", out.get().asFile.path, "--emptyOutDir")
    inputs.files(webuiSources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(out)
}

val webuiTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the UI's Vitest tests; the report goes to build/test-results/webui."
    dependsOn(npmCi)
    val report = layout.buildDirectory.file("test-results/webui/TEST-webui.xml")
    node(
        "node_modules/vitest/vitest.mjs", "run", "--reporter=default", "--reporter=junit",
        "--outputFile.junit=${report.get().asFile.path}",
    )
    inputs.files(webuiSources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(report)
}

// The built page goes into the jar, and the UI's tests are part of the suite.
the<SourceSetContainer>()["main"].resources.srcDir(files(webuiResources).builtBy(webuiBuild))
tasks.named("test") { dependsOn(webuiTest) }

// The webview library (https://github.com/webview/webview, MIT) embeds WebView2 into an existing window; zig's
// C++ compiler builds it with our few additions (src/native/webview) into chairplay_webview.dll. The headers
// come from Microsoft's WebView2 SDK on NuGet, whose WebView2Loader.dll ships beside the DLL: the library uses
// it when it is there, which honours WebView2's environment variables (the profile folder among them), and
// finds the runtime itself when it is not. Both downloads are pinned by version and SHA-256 (GitHub's archive of
// the release tag; the .nupkg from api.nuget.org).
val webviewVersion = "0.12.0"
val webview2SdkVersion = "1.0.4258.31"
val webviewSources: File = gradle.gradleUserHomeDir.resolve("caches/chairplay/webview/webview-$webviewVersion")
val webview2Sdk: File = gradle.gradleUserHomeDir.resolve("caches/chairplay/webview2/microsoft.web.webview2.$webview2SdkVersion")

val installWebviewSources by tasks.registering(InstallArchive::class) {
    description = "Downloads the webview library's $webviewVersion sources into the Gradle user home."
    url = "https://github.com/webview/webview/archive/refs/tags/$webviewVersion.zip"
    sha256 = "446ec9730ad4a1574f5364a62ec5989e78c8a59d0497082e6fbf6b423654a9bb"
    topFolder = "webview-$webviewVersion"
    only = listOf("webview-$webviewVersion/core/include/**")
    marker = "core/include/webview/webview.h"
    target = webviewSources
}

val installWebView2Sdk by tasks.registering(InstallArchive::class) {
    description = "Downloads the WebView2 SDK $webview2SdkVersion (headers and loader) into the Gradle user home."
    url = "https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/$webview2SdkVersion/microsoft.web.webview2.$webview2SdkVersion.nupkg"
    sha256 = "56f7f4b8bf9aee4b8efefbbdd4f67d5f74ebd1b100ed0806da71bf76af481aa9"
    only = listOf("build/native/include/**", "build/native/x64/WebView2Loader.dll")
    marker = "build/native/include/WebView2.h"
    target = webview2Sdk
}

val zigExe = project.extra["zigExe"] as File
val zigRoot = project.extra["zigRoot"] as File
val zigVersion = project.extra["zigVersion"] as String

val webviewDll by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles the UI's web view host into build/native/chairplay_webview.dll, with WebView2Loader.dll beside it."
    dependsOn("installZig", installWebviewSources, installWebView2Sdk)
    val source = layout.projectDirectory.dir("src/native/webview")
    val nativeDir = layout.buildDirectory.dir("native")
    val dll = nativeDir.map { it.file("chairplay_webview.dll") }
    val loader = nativeDir.map { it.file("WebView2Loader.dll") }
    val work = temporaryDir
    val ops = objects.newInstance<FileOps>()
    val sources = webviewSources
    val sdk = webview2Sdk
    inputs.dir(source).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("zig", zigVersion)
    inputs.property("webview", webviewVersion)
    inputs.property("webview2", webview2SdkVersion)
    outputs.files(dll, loader)
    workingDir(source)
    environment("ZIG_GLOBAL_CACHE_DIR", zigRoot.resolve("cache").path)
    environment("ZIG_LOCAL_CACHE_DIR", work.resolve("zig-cache").path)
    // libc++ is linked in statically, and Windows' own C runtime (UCRT) is used, as for speex_echo.dll.
    commandLine(
        zigExe.path, "c++", "-target", "x86_64-windows-gnu", "-shared", "-O2", "-std=c++17",
        "-I${sources.resolve("core/include").path}", "-I${sdk.resolve("build/native/include").path}", "-Icompat",
        "chairplay_webview.cc", "update_dialog.cc", "update_dialog.rc", "-ladvapi32", "-lole32", "-lshell32", "-lshlwapi", "-luser32", "-lversion",
        "-o", work.resolve("chairplay_webview.dll").path,
    )
    doLast {
        ops.fs.copy {
            from(work.resolve("chairplay_webview.dll"))
            from(sdk.resolve("build/native/x64/WebView2Loader.dll"))
            into(dll.get().asFile.parentFile)
        }
    }
}

// The DLLs go where speex_echo.dll goes: build/native for the tests and run, bin/ in the distribution, and the app
// image's root, next to the exe, where Windows looks for a DLL's own dependencies (WebView2Loader.dll) too. Their
// licences are in notices/.
tasks.named("test") { dependsOn(webviewDll) }
tasks.named("run") { dependsOn(webviewDll) }
the<DistributionContainer>()["main"].contents { from(webviewDll) { into("bin") } }
