// The track playback as the app reports it (state.gps.track) and the command that drives it (POST track {action}).
// Codes, never English, as gpsApi.ts: the page words them.

export type TrackPhase = 'stopped' | 'playing' | 'paused'

export type TrackAction = 'open' | 'play' | 'pause' | 'stop'

/** The recording loaded: its file's name, where the player is in it and its length, in seconds. */
export interface TrackState {
  name: string
  phase: TrackPhase
  time: number
  duration: number
  /** The whole recording's distance; absent when the file does not give one. */
  distanceKm?: number
}
