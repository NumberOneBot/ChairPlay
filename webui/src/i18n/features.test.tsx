import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import { absent, appState, control } from '../testing'
import { ALL_FEATURES, setPlatform, WINDOWS } from './platform'

afterEach(() => {
  cleanup()
  setPlatform(WINDOWS)
})

/** Each feature's controls by test id and role: the tab they are on, and a control always there once it is open. */
const PARTS = [
  { feature: 'gameBridge', tab: 'application', anchor: 'browser-network',
    parts: [['integrations-picture', 'switch'], ['integrations-telemetry', 'switch']] },
  { feature: 'usbGps', tab: 'gps', anchor: 'gps-enable', parts: [['gps-method-carplay', 'radio']] },
  { feature: 'location', tab: 'gps', anchor: 'gps-enable', parts: [['gps-locate', 'button']] },
  { feature: 'globalKeyboard', tab: 'keyboard', anchor: 'keys-reset-all', parts: [['global-shortcuts', 'switch']] },
] as const

async function openTab(tab: string, anchor: string) {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={new FakeBackend()} />)
  fireEvent.click(await screen.findByTestId(`tab-${tab}`))
  await screen.findByTestId(anchor)
}

// A system without a feature shows none of its controls (owner, 2026-11-24): Linux has no USB GPS.
describe('a feature’s controls', () => {
  for (const { feature, tab, anchor, parts } of PARTS) {
    it(`show for ${feature} where the system has it`, async () => {
      await openTab(tab, anchor)

      for (const [part, role] of parts) expect(control(part, role)).toBeTruthy()
    })

    it(`are hidden for ${feature} where the system lacks it`, async () => {
      setPlatform({ ...WINDOWS, platform: 'linux', features: ALL_FEATURES.filter((it) => it !== feature) })
      await openTab(tab, anchor)

      for (const [part] of parts) expect(absent(part), part).toBe(true)
    })
  }
})
