package io.github.numberonebot.chairplay.config

import com.shilapi.xcertplay.airplay.AirPlayIcon
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import io.github.numberonebot.chairplay.config.DesktopConfig.Companion.DEFAULT_ICON_FILE
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.HexFormat
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.writeText

// The receiver's own files in the app folder [dir], besides settings.properties: its AirPlay identity, the iPhones'
// pairings, and the car button's icon.

private const val MAX_ICON_PIXELS = 192
private val HEX = HexFormat.of()

/** identity.properties: the receiver's AirPlay keys, generated and written the first time. */
internal fun loadIdentity(dir: Path): AirPlayIdentity {
    val file = dir.resolve("identity.properties")
    if (file.exists()) {
        val remedy = "delete it to make a new one; the iPhone then pairs with ChairPlay again"
        val saved = loadProperties(file, remedy)
        fun key(name: String): ByteArray = saved.getProperty(name)?.trim()
            ?.let { runCatching { HEX.parseHex(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
            ?: throw DamagedFileException(file, "has no valid $name", remedy.replaceFirstChar(Char::uppercase))
        val pairingId = saved.getProperty("pairingId")?.trim()?.ifEmpty { null }
            ?: throw DamagedFileException(file, "has no pairingId", remedy.replaceFirstChar(Char::uppercase))
        return AirPlayIdentity(key("privateKey"), key("publicKey"), pairingId)
    }
    return AirPlayIdentity.generate().also {
        file.writeText(
            "privateKey=${HEX.formatHex(it.privateKey)}\npublicKey=${HEX.formatHex(it.publicKey)}\n" +
                "pairingId=${it.pairingId}\n",
        )
    }
}

/**
 * The icon on the CarPlay home screen, from car.icon ([setting]) in the app folder. The default file is written with
 * ChairPlay's armchair ([CarIcon]) the first time, to be replaced; any square image is sent at [MAX_ICON_PIXELS] at most,
 * as upstream's AirPlayIconSizing does, because a large icon in /info stopped the iPhone connecting (#567).
 */
internal fun carIcon(dir: Path, setting: String?): AirPlayIcon {
    val name = setting?.trim()?.ifEmpty { null } ?: DEFAULT_ICON_FILE
    val file = dir.resolve(name)
    if (name == DEFAULT_ICON_FILE && !file.exists()) Files.write(file, CarIcon.png())
    require(file.exists()) { "car.icon: $file does not exist" }
    val image = runCatching { ImageIO.read(file.toFile()) }.getOrNull()
        ?: throw IllegalArgumentException("car.icon: $file is not an image ImageIO can read (PNG, JPEG, BMP, GIF)")
    require(image.width == image.height) { "car.icon: $file is ${image.width}x${image.height}; the icon must be square" }
    if (image.width <= MAX_ICON_PIXELS) return AirPlayIcon(image.width, image.height, png(image))
    val scaled = BufferedImage(MAX_ICON_PIXELS, MAX_ICON_PIXELS, BufferedImage.TYPE_INT_ARGB)
    scaled.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        drawImage(image, 0, 0, MAX_ICON_PIXELS, MAX_ICON_PIXELS, null)
        dispose()
    }
    return AirPlayIcon(MAX_ICON_PIXELS, MAX_ICON_PIXELS, png(scaled))
}

private fun png(image: BufferedImage): ByteArray = ByteArrayOutputStream().use {
    check(ImageIO.write(image, "png", it)) { "no PNG writer" }
    it.toByteArray()
}
