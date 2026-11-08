package io.github.numberonebot.chairplay.carplay

import com.shilapi.xcertplay.iap2.catalog.Iap2Endpoints
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.iap2.wire.Iap2Parameter
import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import com.shilapi.xcertplay.iap2.wire.Iap2ProtocolException
import com.shilapi.xcertplay.iap2.wire.Iap2WireCodec

/** An enumerated value as sent: its [code] and, when the schema knows it, its [name]. */
data class CodedValue(val code: Int, val name: String?)

/**
 * A decoded parameter list: known parameters by name, in wire order, as Int, Long, Boolean, String,
 * [CodedValue], a list of Int, or nested [DecodedFields] (a list of them for a repeated group); the rest
 * as they came.
 */
class DecodedFields(val values: Map<String, Any>, val unknown: List<Iap2Parameter>) {
    fun has(name: String): Boolean = name in values
}

/**
 * The iAP2 messages the iPhone sends about the drive and the phone, and their parameter layouts. The
 * parameter ids of 0x5001, 0x5201, 0x5202 and 0x4155 that upstream reads (CarPlayPlaybackStatus,
 * BydHudRouteState, FceNavigationState, CarPlayCallState) are the same here; the others follow Apple's
 * iAP2 parameter order. Upstream's Iap2ParameterList and Iap2WireCodec read the wire. A parameter of
 * an unexpected size, or one not listed, stays raw, so a wrong guess shows up in the data log as bytes
 * rather than as a wrong value.
 */
internal object CarPlayDataMessages {
    /**
     * The data groups: Communications (calls, lists), Now Playing, Navigation and Power. Identification,
     * authentication and Wi-Fi sharing, which carry certificates, challenges and the passphrase, are outside.
     */
    fun isData(messageId: Int): Boolean = DATA_RANGES.any { messageId in it }

    fun name(messageId: Int): String =
        SCHEMAS[messageId]?.name ?: Iap2Endpoints.byId(messageId)?.name ?: "0x" + messageId.toString(16).padStart(4, '0')

    /** A layout not yet seen from an iPhone: the data log keeps the bytes beside what was decoded. */
    fun keepsRawBytes(messageId: Int): Boolean = SCHEMAS[messageId]?.verified == false

    /** Null for a message without a layout here, or a body that is not a parameter list. */
    fun decode(frame: Iap2Frame): DecodedFields? {
        val schema = SCHEMAS[frame.messageId] ?: return null
        return try {
            decode(frame.payload, schema.fields)
        } catch (_: Iap2ProtocolException) {
            null
        }
    }

    private fun decode(body: ByteArray, fields: List<Field>): DecodedFields {
        val parameters = Iap2ParameterList.parse(body).asList()
        val byId = fields.associateBy { it.id }
        val values = LinkedHashMap<String, Any>()
        val unknown = ArrayList<Iap2Parameter>()
        for (parameter in parameters) {
            val field = byId[parameter.id]
            val value = field?.let { runCatching { read(it, parameter.payload) }.getOrNull() }
            if (field == null || value == null) {
                unknown += parameter
                continue
            }
            val previous = values[field.name]
            when {
                previous == null -> values[field.name] = if (field.kind == Kind.GROUP && field.repeated) listOf(value) else value
                field.kind == Kind.GROUP && field.repeated -> values[field.name] = (previous as List<*>) + value
                field.kind == Kind.U16_LIST || field.kind == Kind.I16_LIST ->
                    values[field.name] = (previous as List<*>) + (value as List<*>)
                // A single-valued parameter sent twice: the first counts, the second is shown as it came.
                else -> unknown += parameter
            }
        }
        return DecodedFields(values, unknown)
    }

    /** Null when the payload does not fit the field's type. */
    private fun read(field: Field, payload: ByteArray): Any? = when (field.kind) {
        Kind.BOOL -> payload.takeIf { it.size == 1 }?.let { Iap2WireCodec.readU8(it) != 0 }
        Kind.U8 -> payload.takeIf { it.size == 1 }?.let { Iap2WireCodec.readU8(it) }
        Kind.U16 -> payload.takeIf { it.size == 2 }?.let { Iap2WireCodec.readU16(it) }
        Kind.I16 -> payload.takeIf { it.size == 2 }?.let { Iap2WireCodec.readI16(it) }
        Kind.U32 -> payload.takeIf { it.size == 4 }?.let { Iap2WireCodec.readU32(it) }
        Kind.U64 -> payload.takeIf { it.size == 8 }?.let { Iap2WireCodec.readU64(it) }
        Kind.ENUM -> payload.takeIf { it.size == 1 }?.let { Iap2WireCodec.readU8(it) }?.let { CodedValue(it, field.names[it]) }
        // iAP2 strings end in NUL; one that does not is still read whole.
        Kind.STRING -> String(payload, 0, payload.indexOf(0.toByte()).takeIf { it >= 0 } ?: payload.size, Charsets.UTF_8)
        Kind.U16_LIST -> payload.takeIf { it.size % 2 == 0 }?.let { Iap2WireCodec.readU16List(it) }
        Kind.I16_LIST -> payload.takeIf { it.size % 2 == 0 }?.let { bytes -> List(bytes.size / 2) { Iap2WireCodec.readI16(bytes, it * 2) } }
        Kind.GROUP -> decode(payload, field.children)
    }

    private enum class Kind { BOOL, U8, U16, I16, U32, U64, ENUM, STRING, U16_LIST, I16_LIST, GROUP }

    private class Field(
        val id: Int,
        val name: String,
        val kind: Kind,
        val names: Map<Int, String> = emptyMap(),
        val children: List<Field> = emptyList(),
        /** A group that comes once per record. */
        val repeated: Boolean = false,
    )

    private class Schema(val id: Int, val name: String, val fields: List<Field>, val verified: Boolean = true)

    private fun field(id: Int, name: String, kind: Kind) = Field(id, name, kind)

    private fun enumField(id: Int, name: String, codes: Iap2Codes<*>) = Field(id, name, Kind.ENUM, names = codes.names)

    private fun group(id: Int, name: String, vararg children: Field, repeated: Boolean = false) =
        Field(id, name, Kind.GROUP, children = children.toList(), repeated = repeated)

    private val DATA_RANGES = listOf(0x4150..0x417f, 0x5000..0x50ff, 0x5200..0x52ff, 0xae00..0xaeff)

    private val SCHEMAS: Map<Int, Schema> = listOf(
        Schema(
            0x5201, "RouteGuidanceUpdate",
            listOf(
                field(0, "displayComponentIds", Kind.U16_LIST),
                enumField(1, "routeGuidanceState", Iap2DataCodes.routeGuidanceState),
                enumField(2, "maneuverState", Iap2DataCodes.maneuverState),
                field(3, "currentRoadName", Kind.STRING),
                field(4, "destinationName", Kind.STRING),
                field(5, "estimatedTimeOfArrival", Kind.U64),
                field(6, "timeRemainingToDestination", Kind.U64),
                field(7, "distanceRemaining", Kind.U32),
                field(8, "distanceRemainingDisplayStr", Kind.STRING),
                enumField(9, "distanceRemainingDisplayUnits", Iap2DataCodes.distanceUnits),
                field(10, "distanceToNextManeuver", Kind.U32),
                field(11, "distanceToNextManeuverDisplayStr", Kind.STRING),
                enumField(12, "distanceToNextManeuverDisplayUnits", Iap2DataCodes.distanceUnits),
                field(13, "currentManeuverList", Kind.U16_LIST),
                field(14, "maneuverCount", Kind.U16),
                field(15, "visibleInApp", Kind.BOOL),
                field(16, "laneGuidanceIndex", Kind.U16),
                field(17, "laneGuidanceTotalCount", Kind.U16),
                field(18, "laneGuidanceShowing", Kind.BOOL),
                field(19, "sourceName", Kind.STRING),
                field(20, "sourceSupportsRouteGuidance", Kind.BOOL),
            ),
        ),
        Schema(
            0x5202, "RouteGuidanceManeuverUpdate",
            listOf(
                field(0, "displayComponentIds", Kind.U16_LIST),
                field(1, "index", Kind.U16),
                field(2, "maneuverDescription", Kind.STRING),
                enumField(3, "maneuverType", Iap2DataCodes.maneuverType),
                field(4, "afterManeuverRoadName", Kind.STRING),
                field(5, "distanceBetweenManeuver", Kind.U32),
                field(6, "distanceBetweenManeuverDisplayStr", Kind.STRING),
                enumField(7, "distanceBetweenManeuverDisplayUnits", Iap2DataCodes.distanceUnits),
                enumField(8, "drivingSide", Iap2DataCodes.drivingSide),
                enumField(9, "junctionType", Iap2DataCodes.junctionType),
                field(10, "junctionElementAngles", Kind.I16_LIST),
                field(11, "junctionElementExitAngle", Kind.I16),
            ),
        ),
        Schema(
            0x5204, "LaneGuidanceInfoUpdate",
            listOf(
                field(0, "displayComponentIds", Kind.U16_LIST),
                field(1, "laneGuidanceIndex", Kind.U16),
                // The MHI2Q project's layout (hook/routeguidance/rgd_tlv.c), read off the head unit's libesoiap2: the
                // angles come one per parameter there; packed ones are read too.
                group(
                    2, "laneInformation",
                    field(0, "position", Kind.U16),
                    enumField(1, "status", Iap2DataCodes.laneStatus),
                    field(2, "angles", Kind.I16_LIST),
                    field(3, "highlightAngle", Kind.I16),
                    repeated = true,
                ),
                field(3, "laneGuidanceDescription", Kind.STRING),
            ),
            verified = false,
        ),
        Schema(
            0x5001, "NowPlayingUpdate",
            listOf(
                group(
                    0, "mediaItem",
                    field(0, "persistentId", Kind.U64),
                    field(1, "title", Kind.STRING),
                    field(2, "mediaType", Kind.U8),
                    field(3, "rating", Kind.U8),
                    field(4, "playbackDurationMs", Kind.U32),
                    field(5, "albumPersistentId", Kind.U64),
                    field(6, "albumTitle", Kind.STRING),
                    field(7, "albumTrackNumber", Kind.U16),
                    field(8, "albumTrackCount", Kind.U16),
                    field(9, "albumDiscNumber", Kind.U16),
                    field(10, "albumDiscCount", Kind.U16),
                    field(11, "artistPersistentId", Kind.U64),
                    field(12, "artist", Kind.STRING),
                    field(13, "albumArtistPersistentId", Kind.U64),
                    field(14, "albumArtist", Kind.STRING),
                    field(15, "genrePersistentId", Kind.U64),
                    field(16, "genre", Kind.STRING),
                    field(17, "composerPersistentId", Kind.U64),
                    field(18, "composer", Kind.STRING),
                    field(19, "isPartOfCompilation", Kind.BOOL),
                    field(21, "isLikeSupported", Kind.BOOL),
                    field(22, "isBanSupported", Kind.BOOL),
                    field(23, "isLiked", Kind.BOOL),
                    field(24, "isBanned", Kind.BOOL),
                    field(25, "isResidentOnDevice", Kind.BOOL),
                    field(26, "artworkFileTransferId", Kind.U8),
                    field(27, "chapterCount", Kind.U16),
                ),
                group(
                    1, "playback",
                    enumField(0, "playbackStatus", Iap2DataCodes.playbackStatus),
                    field(1, "elapsedTimeMs", Kind.U32),
                    field(2, "queueIndex", Kind.U32),
                    field(3, "queueCount", Kind.U32),
                    field(4, "queueChapterIndex", Kind.U32),
                    enumField(5, "shuffleMode", Iap2DataCodes.shuffleMode),
                    enumField(6, "repeatMode", Iap2DataCodes.repeatMode),
                    field(7, "appName", Kind.STRING),
                    field(8, "mediaLibraryUid", Kind.STRING),
                    field(12, "playbackSpeed", Kind.U16),
                    field(13, "setElapsedTimeAvailable", Kind.BOOL),
                    field(14, "queueListAvailable", Kind.BOOL),
                    field(15, "queueListTransferId", Kind.U8),
                    field(16, "appBundleId", Kind.STRING),
                ),
            ),
        ),
        Schema(
            0x4155, "CallStateUpdate",
            listOf(
                field(0, "remoteId", Kind.STRING),
                field(1, "displayName", Kind.STRING),
                enumField(2, "status", Iap2DataCodes.callStatus),
                enumField(3, "direction", Iap2DataCodes.callDirection),
                field(4, "callUuid", Kind.STRING),
                field(6, "addressBookId", Kind.STRING),
                field(7, "label", Kind.STRING),
                enumField(8, "service", Iap2DataCodes.callService),
                field(9, "isConferenced", Kind.BOOL),
                field(10, "conferenceGroup", Kind.U8),
                enumField(11, "disconnectReason", Iap2DataCodes.disconnectReason),
            ),
        ),
        Schema(
            0x4158, "CommunicationsUpdate",
            listOf(
                field(0, "signalStrength", Kind.U8),
                enumField(1, "registrationStatus", Iap2DataCodes.registrationStatus),
                field(2, "airplaneModeStatus", Kind.BOOL),
                field(3, "carrierName", Kind.STRING),
                // An iPhone 15 on iOS 18.6 sends "UA-KYIVSTAR" here and leaves carrierName empty.
                field(4, "carrierId", Kind.STRING),
                field(5, "telephonyEnabled", Kind.BOOL),
                field(6, "faceTimeAudioEnabled", Kind.BOOL),
                field(7, "faceTimeVideoEnabled", Kind.BOOL),
                field(8, "muteStatus", Kind.BOOL),
                field(9, "currentCallCount", Kind.U8),
                field(10, "newVoicemailCount", Kind.U8),
                field(11, "initiateCallAvailable", Kind.BOOL),
                field(12, "endAndAcceptAvailable", Kind.BOOL),
                field(13, "holdAndAcceptAvailable", Kind.BOOL),
                field(14, "swapAvailable", Kind.BOOL),
                field(15, "mergeAvailable", Kind.BOOL),
                field(16, "holdAvailable", Kind.BOOL),
            ),
        ),
        Schema(
            0xae01, "PowerUpdate",
            listOf(
                field(0, "maximumCurrentDrawnFromAccessory", Kind.U16),
                field(1, "deviceBatteryWillChargeIfPowerIsPresent", Kind.BOOL),
                field(2, "accessoryPowerMode", Kind.U8),
                field(4, "isExternalChargerConnected", Kind.BOOL),
                enumField(5, "batteryChargingState", Iap2DataCodes.batteryChargingState),
                field(6, "batteryChargeLevel", Kind.U16),
            ),
        ),
    ).associateBy { it.id }
}
