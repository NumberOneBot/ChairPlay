// Build the pinned APAC decoder separately from ChairPlay's WASAPI output.
// Runtime capability detection still permits an installation without this DLL.
interface ApacFileOps {
    @get:Inject val fs: FileSystemOperations
}
val apacZig = project.extra["zigExe"] as File
val apacZigRoot = project.extra["zigRoot"] as File
val apacZigVersion = project.extra["zigVersion"] as String
val apacDll by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the APAC decoder for Windows x64."
    dependsOn("installZig")
    val source = layout.projectDirectory.dir("native/apac")
    val sources = fileTree(source.dir("src")) { include("*.cpp") }.files.sortedBy { it.name } +
        source.file("bridge/apac_bridge.cpp").asFile
    val dll = layout.buildDirectory.file("native/chairplay_apac.dll")
    val work = temporaryDir
    val ops = objects.newInstance<ApacFileOps>()
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree(source) { include("**/*.h") }).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("zig", apacZigVersion)
    outputs.file(dll)
    environment("ZIG_GLOBAL_CACHE_DIR", apacZigRoot.resolve("cache").path)
    environment("ZIG_LOCAL_CACHE_DIR", work.resolve("zig-cache").path)
    doFirst {
        commandLine(apacZig.path, "c++", "-target", "x86_64-windows-gnu", "-std=c++11",
            "-O2", "-UNDEBUG", "-Wall", "-Wextra", "-fno-exceptions", "-fno-rtti", "-shared",
            "-I${source.dir("include").asFile.path}", *sources.map { it.path }.toTypedArray(),
            "-o", work.resolve("chairplay_apac.dll").path)
    }
    doLast { ops.fs.copy { from(work.resolve("chairplay_apac.dll")); into(dll.get().asFile.parentFile) } }
}
tasks.named<Test>("test") {
    dependsOn(apacDll)
    inputs.files(apacDll).withPropertyName("apacDll")
}
the<DistributionContainer>()["main"].contents {
    from(apacDll) { into("apac") }
}
