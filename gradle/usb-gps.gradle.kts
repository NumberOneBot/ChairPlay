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
        marker.get().asFile.writeText(usbGpsLock.asFile.readText() + usbGpsBuildRequirements.asFile.readText())
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
        ops.exec.exec {
            workingDir(usbGpsTools.asFile)
            commandLine(venvPython().path, "run_tests.py", "--timeout", "300")
        }
        passed.get().asFile.writeText("ok\n")
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
    doLast {
        ops.fs.delete { delete(dist, usbGpsWork.map { it.dir("pyinstaller") }) }
        ops.exec.exec {
            commandLine(venvPython().path, "-m", "PyInstaller", "--noconfirm", "--clean", "--log-level", "WARN",
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
    }
}

// The image's helper\ folder, which the app starts (WindowsShell.usbGpsHelper), and its packages' licences in
// licenses\usb-gps-helper\. deploy (a Sync) and the installer carry the image whole; an update replaces helper\.
tasks.named("jpackageImage") {
    dependsOn(usbGpsHelper)
    val image = layout.buildDirectory.dir("jpackage/ChairPlay")
    val helper = usbGpsWork.map { it.dir("dist/helper") }
    val licences = usbGpsWork.map { it.dir("licenses") }
    val ops = objects.newInstance<HelperBuildOps>()
    doLast {
        ops.fs.copy {
            from(helper)
            into(image.get().dir("helper"))
        }
        ops.fs.copy {
            from(licences)
            into(image.get().dir("licenses/usb-gps-helper"))
        }
    }
}
