import type { AppState } from '../api'
import type { GpsOutput } from '../gpsApi'
import type { Settings } from '../shell/useSettings'

/** The GPS output as the app reports it; before the app does (an older app, the state not in yet), as the settings say. */
export function outputOf(state: AppState, settings?: Settings): GpsOutput {
  return (
    state.gps?.output ?? {
      selected: settings?.value('gps.method') === 'usb' ? 'usb' : 'carplay',
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
      cleanup: 'clean',
      notices: [],
    }
  )
}

/** An operation is running: the whole settings window waits for it. */
export const isLocked = (output: GpsOutput | undefined): boolean => !!output?.operation
