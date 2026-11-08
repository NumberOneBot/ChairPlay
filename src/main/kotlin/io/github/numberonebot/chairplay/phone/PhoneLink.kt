package io.github.numberonebot.chairplay.phone

/** The phone protocols the app speaks. */
enum class Protocol { CARPLAY, ANDROID_AUTO }

/** The phone's side of the AltScreen, the car's instrument cluster: the view it is asked for, or told it is not wanted. */
interface ClusterControl {
    /** The view at [url], asked for at once with [send], else kept for the next time it is; true when it went out. */
    fun setClusterUrl(url: String, send: Boolean): Boolean

    /** Asks for the view, or tells the phone it is not wanted; true when it went out. */
    fun setClusterUiShown(shown: Boolean): Boolean
}

/** A live session with the phone: what the app asks of it while it lasts. */
interface PhoneLink {
    val protocol: Protocol

    /** The keys, the knob and the touches. */
    val control: PhoneControl

    /** The AltScreen's view, or null when the phone draws none. */
    val cluster: ClusterControl?

    /** The phone's look, dark when [on], as a car's headlights turn it; true when it went out. */
    fun nightMode(on: Boolean): Boolean

    /** Asks the phone for a keyframe of [screen], at most once a second. */
    fun keyframe(screen: ScreenKind)
}
