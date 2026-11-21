// The track playback as the app reports it (state.gps.track) and the command that drives it (POST track {action}).
// Codes, never English, as gpsApi.ts: the page words them.

export type TrackPhase = 'stopped' | 'playing' | 'paused'

/** A seek key: back or forward by its seconds (Kotlin's TrackCommands.SEEKS). */
export type TrackSeek = 'back-60' | 'back-10' | 'forward-10' | 'forward-60'
/** The speed key's choices (Kotlin's TrackCommands.SPEEDS), each sent as `speed-<pace>`. */
export const TRACK_SPEEDS = [0.25, 0.5, 1, 2, 5, 10] as const
export type TrackAction = 'open' | 'play' | 'pause' | 'stop' | TrackSeek | `speed-${(typeof TRACK_SPEEDS)[number]}`

/** Why Open…'s file cannot be played: the track-unreadable notice's args.reason (Kotlin's TrackFormatReason). */
export type TrackFormatReason = 'not-gpx' | 'not-vbo' | 'no-track' | 'no-times' | 'too-short'

/** The recording loaded: its file's name, where the player is in it and its length, in seconds. */
export interface TrackState {
  name: string
  phase: TrackPhase
  time: number
  duration: number
  /** The pace of the recording's time: 1 as recorded, one of TRACK_SPEEDS. */
  speed: number
  /** The whole recording's distance; absent when the file does not give one. */
  distanceKm?: number
}
