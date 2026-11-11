// The city presets show only for a ready USB GPS, as Track playback does (owner, 2026-11-11).
import { cleanup } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { outputs } from '../gpsFixtures'
import { absent, control } from '../testing'
import { gpsState, openSection } from './gpsTesting'

afterEach(cleanup)

const inert = (element: HTMLElement) => element.closest('[inert]') !== null
const paris = () => control('city-preset-PAR', 'radio')

describe('GPS page, the city presets', () => {
  it('shows them open for a ready USB GPS', async () => {
    await openSection(gpsState(outputs.usbOn))
    expect(inert(paris())).toBe(false)
  })

  it('shows them held with Position while the simulation is off', async () => {
    await openSection(gpsState(outputs.usbOff))
    expect(inert(paris())).toBe(true)
  })

  it.each([
    ['CarPlay in force', outputs.carplayOn],
    ['USB chosen, CarPlay still in force', outputs.reconnectNeeded],
    ['USB not set up', outputs.usbUnprepared],
    ['no cable', outputs.usbPreparedNoCable],
    ['the phone missing at start', outputs.usbStartupMissing],
    ['no position yet', outputs.usbNoPosition],
  ])('leaves them off the page with %s', async (_, output) => {
    await openSection(gpsState(output))
    expect(absent('city-preset-PAR')).toBe(true)
  })
})
