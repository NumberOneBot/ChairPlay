// Helpers of the GPS screens' tests: the settings window on a GPS output and USB setup, as the app would report them.
import { fireEvent, render, screen } from '@testing-library/react'
import type { AppState, GpsState } from '../api'
import { FakeBackend } from '../fakeBackend'
import type { GpsOutput, UsbSetupView } from '../gpsApi'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'

export const car: GpsState = {
  position: [50.4501, 30.5234], heading: 90, speed: 48, rpm: 2300, gear: 'D3', throttle: 0.4, brake: 0,
  keyboard: false, gamepad: false, listening: false, sentences: null, sent: 0,
}

/** The app's state with [output] and [usbSetup] in its GPS. */
export function gpsState(output: GpsOutput, usbSetup?: UsbSetupView, extra: Partial<AppState> = {}): AppState {
  return appState({ phase: 'active', canShowCarPlay: true, gps: { ...car, output, usbSetup }, ...extra })
}

/** A backend with the GPS settings the page reads. */
export function gpsBackend(): FakeBackend {
  const backend = new FakeBackend()
  backend.data = { ...backend.data, values: { ...backend.data.values, 'gps.start': '48.85, 2.29', 'gps.heading': '0' } }
  return backend
}

/** Renders the settings window on the section [name], and returns what is needed to change its state. */
export async function openSection(state: AppState, backend = gpsBackend(), name = 'GPS') {
  const view = render(<Shell state={state} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  fireEvent.click(screen.getByRole('tab', { name }))
  return {
    backend,
    update: (next: AppState) => view.rerender(<Shell state={next} backend={backend} />),
  }
}
