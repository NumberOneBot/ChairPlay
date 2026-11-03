// ChairPlay-owned WASAPI output. gradle/apac.gradle.kts builds the separate APAC decoder.
interface SpatialFileOps {
    @get:Inject val fs: FileSystemOperations
}
val spatialZig = project.extra["zigExe"] as File
val spatialZigRoot = project.extra["zigRoot"] as File
val spatialZigVersion = project.extra["zigVersion"] as String
val spatialNativeDir = layout.buildDirectory.dir("native")
val wasapiDll by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the WASAPI shared-mode output adapter."
    dependsOn("installZig")
    val source = layout.projectDirectory.file("native/audio/wasapi.cpp")
    val dll = spatialNativeDir.map { it.file("chairplay_audio.dll") }
    val work = temporaryDir
    val ops = objects.newInstance<SpatialFileOps>()
    inputs.file(source)
    inputs.property("zig", spatialZigVersion)
    outputs.file(dll)
    environment("ZIG_GLOBAL_CACHE_DIR", spatialZigRoot.resolve("cache").path)
    environment("ZIG_LOCAL_CACHE_DIR", work.resolve("zig-cache").path)
    doFirst {
        commandLine(spatialZig.path, "c++", "-target", "x86_64-windows-gnu", "-std=c++11",
            "-O2", "-Wall", "-Wextra", "-Werror", "-fno-exceptions", "-fno-rtti", "-shared",
            source.asFile.path, "-lole32", "-luuid", "-o", work.resolve("chairplay_audio.dll").path)
    }
    doLast { ops.fs.copy { from(work.resolve("chairplay_audio.dll")); into(dll.get().asFile.parentFile) } }
}
tasks.named<Test>("test") {
    dependsOn(wasapiDll)
    inputs.files(wasapiDll).withPropertyName("wasapiDll")
}
tasks.named<JavaExec>("run") { dependsOn(wasapiDll) }
the<DistributionContainer>()["main"].contents {
    from(wasapiDll) { into("bin") }
}
