package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import io.github.numberonebot.chairplay.data.PhoneDataBus
import io.github.numberonebot.chairplay.data.PhoneDataEvent

/** What the Iap2DataDecoder tests share: a decoder on a clock of their own, its bus, the events raised, and frames. */
abstract class Iap2DataDecoderFixture {
    protected var now = 0L
    protected val bus = PhoneDataBus()
    protected val decoder = Iap2DataDecoder(bus, nanoTime = { now })
    protected val events = ArrayList<PhoneDataEvent>()

    init {
        bus.addListener { events += it }
    }

    protected fun frame(id: Int, block: Iap2BodyBuilder.() -> Unit) = decoder.accept(Iap2Messages.buildRaw(id, block))

    protected inline fun <reified E : PhoneDataEvent> last(): E = events.filterIsInstance<E>().last()
}
