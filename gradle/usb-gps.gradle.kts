// The USB GPS helper (tools/usb-gps): its Python tests in `check`, and its frozen build (PyInstaller --onedir, D7),
// which the app image carries in helper\ beside ChairPlay.exe with the licences of its packages. Apply it after
// gradle/package.gradle.kts, whose image it adds to.
//
// Everything runs in build/usb-gps/venv: Python 3.12 with exactly the locked packages (requirements-lock.txt) and
// the pinned PyInstaller (requirements-build.txt), made again only when those change. The Python comes from the
// usbgps.python property (the release workflow sets it as ORG_GRADLE_PROJECT_usbgps.python), else the py launcher's
// 3.12. The first build downloads the packages from PyPI.

interface HelperBuildOps {
    @get:Inject val fs: FileSystemOperations
    @get:Inject val exec: ExecOperations
}

val usbGpsTools = layout.projectDirectory.dir("tools/usb-gps")
val usbGpsWork = layout.buildDirectory.dir("usb-gps")
val usbGpsLock = usbGpsTools.file("requirements-lock.txt")
val usbGpsBuildRequirements = usbGpsTools.file("requirements-build.txt")
val buildPython: Provider<String> = providers.gradleProperty("usbgps.python")
    .orElse(providers.environmentVariable("CHAIRPLAY_BUILD_PYTHON"))

fun basePython(): List<String> = buildPython.orNull?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: listOf("py", "-3.12")

fun venvPython(): File = usbGpsWork.get().dir("venv/Scripts").file("python.exe").asFile

// The helper's own sources, not its development venv or what Python and PyInstaller leave in the folder.
val usbGpsSources = fileTree(usbGpsTools) {
    include("**/*.py", "helper.spec", "requirements*.txt")
    exclude(".venv/**", "**/__pycache__/**", "build/**", "dist/**")
}

// Gradle runs a task again whenever this script changes, even with its inputs as they were. The venv, the tests and
// the frozen helper each keep a stamp of what they were made from, and do nothing when it still matches: editing the
// script never costs a reinstall of the packages or a new PyInstaller run.
fun usbGpsStamp(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    usbGpsSources.files.sortedBy { it.relativeTo(usbGpsTools.asFile).invariantSeparatorsPath }.forEach { file ->
        digest.update(file.relativeTo(usbGpsTools.asFile).invariantSeparatorsPath.toByteArray())
        digest.update(file.readBytes())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun stampMatches(stamp: File, expected: String, vararg made: File): Boolean =
    stamp.isFile && stamp.readText() == expected && made.all { it.exists() }

val usbGpsVenv by tasks.registering {
    group = "build"
    description = "Makes build/usb-gps/venv: Python 3.12 with the helper's locked packages and PyInstaller."
    inputs.files(usbGpsLock, usbGpsBuildRequirements)
    inputs.property("python", buildPython.orElse("py -3.12"))
    val venv = usbGpsWork.map { it.dir("venv") }
    val marker = venv.map { it.file("chairplay-venv.txt") }
    outputs.file(marker)
    val ops = objects.newInstance<HelperBuildOps>()
    doLast {
        val wanted = usbGpsLock.asFile.readText() + usbGpsBuildRequirements.asFile.readText()
        if (stampMatches(marker.get().asFile, wanted, venvPython())) {
            logger.lifecycle("USB GPS helper venv: already made from these requirements")
            return@doLast
        }
        val base = basePython()
        val version = ops.exec.runCatching {
            exec { commandLine(base + listOf("-c", "import sys; sys.exit(sys.version_info[:2] != (3, 12))")) }
        }
        check(version.isSuccess) {
            "The USB GPS helper builds with Python 3.12 (${base.joinToString(" ")} is not one, or is missing). " +
                "Install it, or name it with -Pusbgps.python=<path to python.exe>."
        }
        ops.fs.delete { delete(venv) }
        ops.exec.exec { commandLine(base + listOf("-m", "venv", venv.get().asFile.path)) }
        ops.exec.exec {
            commandLine(venvPython().path, "-m", "pip", "install", "--disable-pip-version-check", "--no-input",
                "--require-virtualenv", "-r", usbGpsLock.asFile.path, "-r", usbGpsBuildRequirements.asFile.path)
        }
        marker.get().asFile.writeText(wanted)
    }
}

val usbGpsTest by tasks.registering {
    group = "verification"
    description = "Runs the USB GPS helper's Python tests (fake phones only) in the build venv."
    dependsOn(usbGpsVenv)
    inputs.files(usbGpsSources)
    inputs.files(usbGpsVenv)
    val passed = usbGpsWork.map { it.file("tests-passed.txt") }
    outputs.file(passed)
    val ops = objects.newInstance<HelperBuildOps>()
    doLast {
        val stamp = usbGpsStamp()
        if (stampMatches(passed.get().asFile, stamp)) {
            logger.lifecycle("USB GPS helper tests: passed already for these sources")
            return@doLast
        }
        ops.exec.exec {
            workingDir(usbGpsTools.asFile)
            commandLine(venvPython().path, "run_tests.py", "--timeout", "300")
        }
        passed.get().asFile.writeText(stamp)
    }
}

tasks.named("check") { dependsOn(usbGpsTest) }

// build/usb-gps/dist/helper (helper.exe and _internal\), checked by its own --self-test, and build/usb-gps/licenses.
val usbGpsHelper by tasks.registering {
    group = "build"
    description = "Freezes the USB GPS helper with PyInstaller into build/usb-gps/dist/helper and checks it."
    dependsOn(usbGpsVenv)
    inputs.files(usbGpsSources)
    inputs.files(usbGpsVenv)
    val dist = usbGpsWork.map { it.dir("dist") }
    val helper = dist.map { it.dir("helper") }
    val licences = usbGpsWork.map { it.dir("licenses") }
    outputs.dir(helper)
    outputs.dir(licences)
    val ops = objects.newInstance<HelperBuildOps>()
    val built = usbGpsWork.map { it.file("helper-built.txt") }
    doLast {
        val stamp = usbGpsStamp()
        if (stampMatches(built.get().asFile, stamp, helper.get().file("helper.exe").asFile, licences.get().asFile)) {
            logger.lifecycle("USB GPS helper: already frozen from these sources")
            return@doLast
        }
        ops.fs.delete { delete(dist, built, usbGpsWork.map { it.dir("pyinstaller") }) }
        ops.exec.exec {
            commandLine(venvPython().path, "-m", "PyInstaller", "--noconfirm", "--clean", "--log-level", "ERROR",
                "--distpath", dist.get().asFile.path, "--workpath", usbGpsWork.get().dir("pyinstaller").asFile.path,
                usbGpsTools.file("helper.spec").asFile.path)
        }
        val report = java.io.ByteArrayOutputStream()
        ops.exec.exec {
            commandLine(helper.get().file("helper.exe").asFile.path, "--self-test")
            standardOutput = report
        }
        val line = report.toString(Charsets.UTF_8).trim()
        check(Regex(""""ok": true""").containsMatchIn(line) && Regex(""""protocol": 2\b""").containsMatchIn(line)) {
            "The frozen helper failed its self-test: $line"
        }
        logger.lifecycle("USB GPS helper: $line")
        ops.exec.exec {
            commandLine(venvPython().path, usbGpsTools.file("licenses.py").asFile.path, "--lock", usbGpsLock.asFile.path,
                "--extra", "pyinstaller", "--out", licences.get().asFile.path)
        }
        built.get().asFile.writeText(stamp)
    }
}

// The image gets the helper's packages' licences in licenses\usb-gps-helper\ with every build.
tasks.named("jpackageImage") {
    dependsOn(usbGpsHelper)
    val image = layout.buildDirectory.dir("jpackage/ChairPlay")
    val licences = usbGpsWork.map { it.dir("licenses") }
    val ops = objects.newInstance<HelperBuildOps>()
    doLast {
        ops.fs.copy {
            from(licences)
            into(image.get().dir("licenses/usb-gps-helper"))
        }
    }
}

// The helper itself, some 6,400 files, joins the image's helper\ (which the app starts, WindowsShell.usbGpsHelper)
// only for the installer and the portable zip, where an update replaces helper\ whole. deploy puts it in the app
// folder on its own, when it changed (deployHelper in gradle/package.gradle.kts).
val imageHelper by tasks.registering(Copy::class) {
    description = "Puts the frozen USB GPS helper into the app image's helper\\ for the installer and the portable zip."
    dependsOn(usbGpsHelper)
    mustRunAfter("jpackageImage")
    from(usbGpsWork.map { it.dir("dist/helper") })
    into(layout.buildDirectory.dir("jpackage/ChairPlay/helper"))
}
tasks.matching { it.name == "installer" || it.name == "portableZip" }.configureEach { dependsOn(imageHelper) }
