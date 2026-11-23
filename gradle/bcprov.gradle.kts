// The app image's BouncyCastle: only the classes ChairPlay's code reaches, some 330 of bcprov's (0.7 MB of its 8.6 MB).
// DiPlay's AirPlay crypto, its local MFi and the APAP reader use ASN.1, ChaCha20-Poly1305, HKDF, Ed25519 and X25519;
// the JCE provider is never registered. jdeps lists each class's references as JVM 17 reads the multi-release jar,
// and the jar keeps every version of the classes reached from the other jars. Apply it before gradle/package.gradle.kts,
// whose imageJars puts the trimmed jar in place of the full one; the tests and the start scripts keep the full jar.

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

interface BcprovOps {
    @get:Inject val exec: ExecOperations
}

val bcprovJdeps = extensions.getByType<JavaToolchainService>().launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    .map { it.metadata.installationPath.file("bin/jdeps.exe").asFile }

tasks.register("trimBcprov") {
    description = "Writes build/bcprov-trimmed with the bcprov classes the app's code reaches."
    dependsOn("installDist")
    val jars = fileTree(layout.buildDirectory.dir("install/chairplay/lib")) { include("*.jar") }
    val outDir = layout.buildDirectory.dir("bcprov-trimmed")
    val jdeps = bcprovJdeps
    val ops = objects.newInstance<BcprovOps>()
    inputs.files(jars)
    outputs.dir(outDir)
    doLast {
        val all = jars.files.sortedBy { it.name }
        val bcprov = all.single { it.name.startsWith("bcprov-") }
        val listing = ByteArrayOutputStream()
        ops.exec.exec {
            commandLine(listOf(jdeps.get().path, "--multi-release", "17", "-verbose:class", "-filter:none",
                "--ignore-missing-deps", "--class-path", all.joinToString(File.pathSeparator)) + all.map { it.path })
            standardOutput = listing
        }
        // "   a.B   -> c.D   jar"; a class of a versioned part reads "11/a.B".
        val line = Regex("""^\s+(?:\d+/)?(\S+)\s+->\s+(?:\d+/)?(\S+)""")
        val edges = HashMap<String, MutableSet<String>>()
        listing.toString().lineSequence().mapNotNull { line.find(it) }
            .forEach { edges.getOrPut(it.groupValues[1]) { HashSet() } += it.groupValues[2] }
        val bc = "org.bouncycastle."
        val todo = ArrayDeque(edges.filterKeys { !it.startsWith(bc) }.values.flatten().filter { it.startsWith(bc) })
        val kept = HashSet<String>()
        while (todo.isNotEmpty()) {
            val name = todo.removeLast()
            if (kept.add(name)) edges[name]?.filterTo(todo) { it.startsWith(bc) && it !in kept }
        }
        check(kept.isNotEmpty()) { "jdeps found no use of BouncyCastle; check its output" }
        fun className(entry: String) = entry.replace(Regex("^META-INF/versions/\\d+/"), "").removeSuffix(".class").replace('/', '.')
        val out = outDir.get().asFile.apply { deleteRecursively(); mkdirs() }.resolve(bcprov.name)
        ZipFile(bcprov).use { zip ->
            val classes = zip.entries().asSequence().filter { it.name.endsWith(".class") }.toList()
            val known = classes.mapTo(HashSet()) { className(it.name) }
            ZipOutputStream(out.outputStream()).use { dst ->
                dst.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                dst.write("Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n".toByteArray())
                for (entry in classes.filter { className(it.name) in kept }) {
                    val bytes = zip.getInputStream(entry).readBytes()
                    // A class named in a string is loaded by reflection, which jdeps does not see.
                    Regex("""org\.bouncycastle\.[\w.$]+""").findAll(String(bytes, Charsets.ISO_8859_1))
                        .map { it.value }.firstOrNull { it in known && it !in kept }
                        ?.let { throw GradleException("${entry.name} names $it, which the trimmed bcprov leaves out") }
                    dst.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    dst.write(bytes)
                }
            }
        }
        logger.lifecycle("bcprov trimmed to ${kept.size} classes: ${out.length() / 1024} KB of ${bcprov.length() / 1024} KB")
    }
}
