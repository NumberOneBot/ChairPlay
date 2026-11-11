import type { AppState } from '../api'
import type { GpsOutput } from '../gpsApi'
import type { Settings } from '../shell/useSettings'

/**
 * A USB GPS in force, set up, and with its iPhone on the cable: only then do the parts that need it show, the city
 * presets and Track playback (owner, 2026-11-11). The simulation switch holds them; it does not hide them.
 */
export function usbGpsReady(o: GpsOutput): boolean {
  return o.selected === 'usb' && o.applied === 'usb' && o.usbPrepared && o.device !== null && o.availability === 'ready'
}

/** The GPS output as the app reports it; before the app does (an older app, the state not in yet), as the settings say. */
export function outputOf(state: AppState, settings?: Settings): GpsOutput {
  const selected = settings?.value('gps.method') === 'usb' ? 'usb' : 'carplay'
  return (
    state.gps?.output ?? {
      selected,
      applied: selected,
      enabledIntent: settings?.value('gps.enabled') === 'true',
      activeMethod: null,
      running: false,
      availability: 'ready',
      reason: null,
      usbPrepared: false,
      device: null,
      successfulSends: 0,
      rateHz: 4,
      generation: 0,
      operation: null,
      notices: [],
    }
  )
}
