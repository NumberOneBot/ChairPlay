package io.github.numberonebot.chairplay.phone

/** How far a connection's bring-up got, for the start screen. A link that goes live is reported apart (onLink). */
sealed interface LinkStatus {
    /** Opening the PC's side: the network and the listeners the phone connects to. */
    data object Starting : LinkStatus

    /** Calling the paired phone over Bluetooth. */
    data object CallingPhone : LinkStatus

    /** The phone answered and is to join the Wi-Fi; a new one asks the driver to accept the car first. */
    data class Joining(val knownPhone: Boolean) : LinkStatus

    /** The bring-up failed for [message]; the next one starts after [retryMillis]. */
    data class Failed(val message: String, val retryMillis: Long) : LinkStatus
}
