package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.update.updateToast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class WindowsToastTest {
    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream()).documentElement

    private fun Element.all(tag: String) = getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    @Test
    fun `the ask's toast opens Buy Me a Coffee and the release, both by protocol, tagged as the toast's`() {
        val toast = parse(toastXml(updateToast("1.2.0", "USB GPS & more <now>", ask = true)))
        val actions = toast.all("action")
        assertEquals(2, actions.size)
        assertTrue(actions.all { it.getAttribute("activationType") == "protocol" })
        assertTrue(actions[0].getAttribute("arguments").startsWith("https://www.buymeacoffee.com/alexstrelets?"))
        assertEquals("https://github.com/NumberOneBot/ChairPlay/releases/tag/v1.2.0" +
            "?utm_source=chairplay&utm_medium=app&utm_campaign=update-toast&utm_content=1.2.0", actions[1].getAttribute("arguments"))
        assertEquals("protocol", toast.getAttribute("activationType"))
        assertEquals(actions[1].getAttribute("arguments"), toast.getAttribute("launch"))
    }

    @Test
    fun `without the ask the toast tells the highlight, or nothing but its title`() {
        val plain = updateToast("1.2.0", "USB GPS & more <now>", ask = false)
        assertEquals("USB GPS & more <now>", plain.body)
        assertEquals(1, plain.buttons.size)
        assertEquals("USB GPS & more <now>", parse(toastXml(plain)).all("text")[1].textContent)
        assertNull(updateToast("1.2.0", null, ask = false).body)
    }
}
