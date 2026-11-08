package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.input.Size
import io.github.numberonebot.chairplay.phone.VideoCodecKind
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * A window's title: the one its owner sets ([base], from any thread), and, while the picture is not shown one to one,
 * its scale right after the resolution, between the titles' separators ("1280×720 · 1.05× · connecting…"). The picture
 * keeps its shape in the window, so the scale is the letterbox's: the smaller of the two ratios. [next] runs on the GL
 * thread each pass and gives the title only when it changed, as GLFW sets titles on that thread.
 */
internal class WindowTitle {
    private val wanted = AtomicReference<String?>()
    private val selectedCodec = AtomicReference<VideoCodecKind?>()
    private var shown: String? = null

    fun base(title: String) = wanted.set(title)

    fun codec(codec: VideoCodecKind?) = selectedCodec.set(codec)

    /** A notice after all the title says, as the phone gone quiet, until null clears it; safe from any thread. */
    fun notice(text: String?) = notice.set(text)

    private val notice = AtomicReference<String?>()

    fun next(framebuffer: Size, picture: Size): String? {
        val base = wanted.get() ?: return null
        val codec = selectedCodec.get()?.let { if (it == VideoCodecKind.H265) "H.265" else "H.264" }
        val suffix = listOfNotNull(codec, scale(framebuffer, picture)).joinToString(" · ").takeIf { it.isNotEmpty() }
        val scaled = suffix?.let { scale ->
            // Right after the resolution ("1280×720"), or last when the title has none.
            RESOLUTION.find(base)?.let { base.replaceRange(it.range, "${it.value} · $scale") } ?: "$base · $scale"
        } ?: base
        val title = notice.get()?.let { "$scaled · $it" } ?: scaled
        if (title == shown) return null
        shown = title
        return title
    }

    private fun scale(framebuffer: Size, picture: Size): String? {
        if (picture.width <= 0 || picture.height <= 0 || framebuffer.width <= 0 || framebuffer.height <= 0) return null
        val ratio = min(framebuffer.width.toDouble() / picture.width, framebuffer.height.toDouble() / picture.height)
        val text = String.format(Locale.ROOT, "%.2f", ratio)
        return if (text == "1.00") null else "$text×"
    }

    private companion object {
        val RESOLUTION = Regex("""\d+×\d+""")
    }
}

/** A [WindowTitle.notice] on this window's title, or null to clear it; safe from any thread. */
internal fun VideoWindow.titleNotice(text: String?) {
    titles.notice(text)
    repaint()
}
