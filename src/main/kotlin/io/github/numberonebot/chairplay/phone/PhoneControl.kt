package io.github.numberonebot.chairplay.phone

/**
 * What the car's knob reports: the [select], [home] and [back] buttons held, the [x] and [y] tilt (-127..127) and
 * the [wheel] steps turned since the last report.
 */
data class KnobInput(
    val select: Boolean = false,
    val home: Boolean = false,
    val back: Boolean = false,
    val x: Int = 0,
    val y: Int = 0,
    val wheel: Int = 0,
)

/** One finger on the picture: [x] and [y] in 0..1 of it, [down] while it touches. */
data class TouchPoint(val id: Int, val x: Double, val y: Double, val down: Boolean)

/** The media keys a car has. */
enum class MediaKey { PREVIOUS, PLAY_PAUSE, NEXT }

/** The phone keys a car has. */
enum class CallKey { ANSWER, HANG_UP }

/** What the app asks of a connected phone, whatever its protocol. */
interface PhoneControl {
    /** The knob's state; [momentary] releases at once, as a wheel step does. */
    fun knob(input: KnobInput, momentary: Boolean)
    fun media(key: MediaKey)
    fun call(key: CallKey)
    fun voiceAssistant()
    fun touch(points: List<TouchPoint>)

    /** Zooms the AltScreen's map [steps] times, in when positive; false when the phone has no such thing. */
    fun mapZoom(steps: Int): Boolean
}
