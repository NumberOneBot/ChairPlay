// The installer: build/installer/ChairPlay-<version>-setup.exe, which Inno Setup 6 makes from the app image with
// packaging/installer/ChairPlay.iss. Apply it after gradle/package.gradle.kts, whose image and data list it uses.

// The release version (chairplay.version, X.Y.Z), set in build.gradle.kts; null in a development build.
val appVersion = project.extra["appVersion"] as String?
// The files and folders the app writes next to its exe, set in gradle/package.gradle.kts.
@Suppress("UNCHECKED_CAST")
val appDataFiles = project.extra["appDataFiles"] as List<String>

// ISCC.exe from the inno.iscc property, else from Inno Setup 6's machine-wide or per-user install folder.
val isccProperty: Provider<String> = providers.gradleProperty("inno.iscc")
val isccCandidates: List<File> = listOfNotNull(
    providers.environmentVariable("ProgramFiles(x86)").orNull,
    providers.environmentVariable("LOCALAPPDATA").orNull?.let { "$it\\Programs" },
).map { File(it, "Inno Setup 6\\ISCC.exe") }

fun iscc(): File {
    isccProperty.orNull?.let { path ->
        return File(path).also { check(it.isFile) { "ISCC.exe not found at $path (the inno.iscc property). Point it at Inno Setup 6's ISCC.exe." } }
    }
    return isccCandidates.firstOrNull { it.isFile } ?: throw GradleException(
        "Inno Setup 6 not found in ${isccCandidates.joinToString(" or ")}. Install it from https://jrsoftware.org/isdl.php, " +
            "or pass its ISCC.exe with -Pinno.iscc=<path>."
    )
}

fun requireVersion(): String = checkNotNull(appVersion) {
    "The installer needs a release version. Pass it with -Pchairplay.version=X.Y.Z, like 0.1.0."
}

// Explicit opt-in: Inno reads this external directory directly, never through the app image or a cached task input.
val mfiSource = providers.gradleProperty("installer.mfiDir")
fun mfiDirectory(): File? = mfiSource.orNull?.let { value ->
    check(value.isNotBlank()) { "installer.mfiDir must name an external directory" }
    val dir = file(value).canonicalFile
    check(!dir.toPath().startsWith(rootDir.canonicalFile.toPath())) { "MFi packaging input must be outside the repository" }
    check(listOf("identity.pk8", "certificate.p7b").all { dir.resolve(it).isFile }) {
        "installer.mfiDir must contain both identity.pk8 and certificate.p7b"
    }
    dir
}

val validateInstallerMfi by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the optional external MFi pair without logging its contents."
    onlyIf { mfiSource.isPresent }
    val java = project.extensions.getByType<JavaToolchainService>().launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    doFirst {
        commandLine(java.get().executablePath.asFile.path,
            layout.projectDirectory.file("packaging/installer/ValidateMfi.java").asFile.path,
            checkNotNull(mfiDirectory()).path)
    }
}

val installer by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds build/installer/ChairPlay-<version>-setup.exe with Inno Setup 6 (needs -Pchairplay.version)."
    dependsOn("jpackageImage", validateInstallerMfi)
    val image = layout.buildDirectory.dir("jpackage/ChairPlay")
    val outDir = layout.buildDirectory.dir("installer")
    val script = layout.projectDirectory.file("packaging/installer/ChairPlay.iss")
    doFirst {
        val dir = image.get().asFile
        // An image is the program alone; a data file in it would overwrite the user's on every update.
        val data = appDataFiles.map { it.removeSuffix("/**") }.filter { dir.resolve(it).exists() }
        check(data.isEmpty()) { "$dir holds app data (${data.joinToString()}); refusing to put it into the installer" }
        commandLine(listOf(
            iscc().path,
            "/DAppVersion=${requireVersion()}",
            "/DSourceDir=${dir.path}",
            "/DOutputDir=${outDir.get().asFile.path}",
            // What the uninstaller deletes when the user asks it to: the data deploy keeps, as file and folder names.
            "/DAppData=${appDataFiles.joinToString(";") { it.removeSuffix("/**") }}",
        ) + listOfNotNull(mfiDirectory()?.let { "/DMfiSourceDir=${it.path}" }) + script.asFile.path)
    }
}

// The portable build: the app image in a ChairPlay folder of its own, to unpack anywhere the user can write. It holds
// no data, which the app writes next to ChairPlay.exe at its first start, but the same MFi pair as the installer
// (installer.mfiDir), so that it works as the installed app does.
val portableZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packs build/jpackage/ChairPlay into build/portable/ChairPlay-<version>-portable.zip."
    dependsOn("jpackageImage", validateInstallerMfi)
    val image = layout.buildDirectory.dir("jpackage/ChairPlay")
    from(image) { into("ChairPlay") }
    if (mfiSource.isPresent) {
        from(mfiSource.map { mfiDirectory()!! }) { include("identity.pk8", "certificate.p7b"); into("ChairPlay/offline-mfi") }
    }
    destinationDirectory = layout.buildDirectory.dir("portable")
    archiveFileName = "ChairPlay-${appVersion ?: "0.0.0"}-portable.zip"
    val data = appDataFiles.map { it.removeSuffix("/**") }
    doFirst {
        val found = data.filter { image.get().asFile.resolve(it).exists() }
        check(found.isEmpty()) { "The app image holds app data (${found.joinToString()}); refusing to put it into the zip" }
    }
}

// Without a version or Inno Setup, fail before the app image is built rather than after.
gradle.taskGraph.whenReady {
    if (hasTask(installer.get())) {
        requireVersion()
        iscc()
        mfiDirectory()
    }
}
