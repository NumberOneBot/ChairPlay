package io.github.numberonebot.chairplay.carplay

import io.github.numberonebot.chairplay.session.desktopIdentification
import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.message.Iap2ControlMessages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import com.shilapi.xcertplay.transport.Iap2WirelessLinkRole
import com.shilapi.xcertplay.transport.forWirelessLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DataSubscriptionsTest {
    // Each request only asks for more of a message upstream already starts; none needs a new identification.
    @Test
    fun `the extra subscriptions ask for more of what upstream subscribes to`() {
        val upstream = Iap2ControlMessages.subscriptions().associateBy { it.messageId }
        val extra = DataSubscriptions.frames()

        assertEquals(listOf(0x5000, 0x4157, 0x4154), extra.map { it.messageId })
        for (frame in extra) {
            val asked = parameters(frame)
            val before = parameters(upstream.getValue(frame.messageId))
            assertTrue("0x${frame.messageId.toString(16)} drops $before", asked.keys.containsAll(before.keys))
            for ((id, children) in before) assertTrue("group $id", asked.getValue(id).containsAll(children))
        }
        assertEquals(
            mapOf(
                0 to setOf(1, 2, 4, 6, 7, 8, 9, 10, 12, 14, 16, 18, 26, 27),
                1 to setOf(0, 1, 2, 3, 5, 6, 7, 12, 16),
            ),
            parameters(extra[0]),
        )
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 8, 9, 10), parameters(extra[1]).keys)
        assertEquals(setOf(0, 1, 2, 3, 4, 7, 8, 9, 10, 11), parameters(extra[2]).keys)
    }

    @Test
    fun `the identification already declares every extra subscription`() {
        val radio = "10:5A:95:75:B2:AA"
        for (role in Iap2WirelessLinkRole.entries) {
            val identification = desktopIdentification(radio).forWirelessLink(role, Iap2WirelessIdentification(radio, "Home"))
            val sent = Iap2BodyReader.of(Iap2IdentificationClient.identificationInformation(identification)).u16List(6)

            for (frame in DataSubscriptions.frames()) assertTrue("$role 0x${frame.messageId.toString(16)}", frame.messageId in sent)
        }
    }

    @Test
    fun `the extra subscriptions go out in order and a failed send is logged, not thrown`() {
        val sent = ArrayList<Int>()
        val messages = ArrayList<String>()

        DataSubscriptions.send({ sent += it.messageId }, messages::add)
        assertEquals(listOf(0x5000, 0x4157, 0x4154), sent)
        assertEquals(listOf("iap2 extra data subscriptions sent"), messages)

        DataSubscriptions.send({ throw IOException("link closed") }, messages::add)
        assertEquals("iap2 extra data subscriptions not sent: IOException: link closed", messages.last())
    }

    /** A Start message's parameters, each with the ids of its children when it is a group. */
    private fun parameters(frame: Iap2Frame): Map<Int, Set<Int>> =
        Iap2BodyReader.of(frame).list().associate { parameter ->
            parameter.id to if (parameter.payload.isEmpty()) emptySet() else Iap2BodyReader.of(frame).group(parameter.id).list().map { it.id }.toSet()
        }
}
