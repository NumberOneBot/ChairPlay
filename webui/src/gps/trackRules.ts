// When Track playback shows, waits, owns the car, and can stop (desktop-docs/gps-track-playback-ui-spec.md §1, §6, §8).
import type { GpsOutput } from '../gpsApi'
import type { TrackState } from './trackApi'

/** Only a USB GPS in force, set up, and with its iPhone on the cable plays a track. */
export function trackShown(o: GpsOutput): boolean {
  return o.selected === 'usb' && o.applied === 'usb' && o.usbPrepared && o.device !== null && o.availability === 'ready'
}

/** Why the whole section waits, or null when it does not. */
export function trackHeld(o: GpsOutput): string | null {
  return !o.enabledIntent ? 'Turn on signal simulation to use track playback' : null
}

/** A recording playing or paused drives the car: nothing else may move it until Stop or its end. */
export function trackOwnsCar(t?: TrackState | null): boolean {
  return t?.phase === 'playing' || t?.phase === 'paused'
}

/** Stop also rewinds: stopped at the start, it has nothing left to do. */
export function stopOff(t: TrackState): boolean {
  return t.phase === 'stopped' && t.time <= 0
}
