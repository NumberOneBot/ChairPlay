package io.github.numberonebot.chairplay.platform

import java.nio.file.Path

/** A platform for tests: each answer is a field the test sets, and nothing reaches the real system. */
internal class FakePlatform(override val shell: FakeShell = FakeShell()) : Platform

/** A desktop shell that answers what the test set. */
internal class FakeShell(
    var name: String = "Windows 11 24H2 (build 26300)",
    var dark: Boolean? = null,
    var directory: Path = Path.of(System.getProperty("java.io.tmpdir")),
) : DesktopShell {
    override fun systemName(): String = name

    override fun appsDark(): Boolean? = dark

    override fun dataDirectory(): Path = directory
}
