// The game bridge DLL: the picture as a named D3D11 shared texture, and the status and telemetry files.
interface BridgeFileOps {
    @get:Inject val fs: FileSystemOperations
}
val bridgeZig = project.extra["zigExe"] as File
val bridgeZigRoot = project.extra["zigRoot"] as File
val bridgeZigVersion = project.extra["zigVersion"] as String
val bridgeNativeDir = layout.buildDirectory.dir("native")
val bridgeDll by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the game bridge's frame and telemetry library."
    dependsOn("installZig")
    val sources = layout.projectDirectory.dir("native/bridge")
    val dll = bridgeNativeDir.map { it.file("chairplay_bridge.dll") }
    val work = temporaryDir
    val ops = objects.newInstance<BridgeFileOps>()
    inputs.dir(sources)
    inputs.property("zig", bridgeZigVersion)
    outputs.file(dll)
    environment("ZIG_GLOBAL_CACHE_DIR", bridgeZigRoot.resolve("cache").path)
    environment("ZIG_LOCAL_CACHE_DIR", work.resolve("zig-cache").path)
    doFirst {
        val cpp = sources.asFileTree.matching { include("*.cpp") }.files.sortedBy { it.name }.map { it.path }
        commandLine(listOf(bridgeZig.path, "c++", "-target", "x86_64-windows-gnu", "-std=c++17",
            "-O2", "-Wall", "-Wextra", "-Werror", "-fno-exceptions", "-fno-rtti", "-shared") +
            cpp + listOf("-ld3d11", "-ldxgi", "-o", work.resolve("chairplay_bridge.dll").path))
    }
    doLast { ops.fs.copy { from(work.resolve("chairplay_bridge.dll")); into(dll.get().asFile.parentFile) } }
}
tasks.named<Test>("test") {
    dependsOn(bridgeDll)
    inputs.files(bridgeDll).withPropertyName("bridgeDll")
}
tasks.named<JavaExec>("run") { dependsOn(bridgeDll) }
the<DistributionContainer>()["main"].contents {
    from(bridgeDll) { into("bin") }
}
