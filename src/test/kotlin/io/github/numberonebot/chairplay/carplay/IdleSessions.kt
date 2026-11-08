package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.PairingStore
import java.net.Socket

/** A CarPlay session with no iPhone behind it: nothing it sends goes out, and a night mode waits in it ([pendingNight]). */
internal fun idleSession(): AirPlaySession = AirPlaySession(
    socket = Socket(),
    config = AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:01",
        sourceVersion = "1.0", main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480)),
    identity = AirPlayIdentity.generate(), pairings = PairingStore(), mfi = null,
    listener = object : AirPlaySessionListener {}, media = CarPlayMediaEngine(object : MediaSink {}, false),
)

/** The night mode [session] was asked for and holds until its event channel takes it; null for none. */
internal fun pendingNight(session: AirPlaySession): Boolean? =
    AirPlaySession::class.java.getDeclaredField("pendingNightMode").apply { isAccessible = true }.get(session) as Boolean?
