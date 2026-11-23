import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import { absent, appState, control } from '../testing'

afterEach(cleanup)

/** Set up and idle, with [pairedIphone] paired in Windows' Bluetooth. */
function paired(pairedIphone: string | null, setupDone: boolean): AppState {
  const base = appState({
    phase: 'idle',
    setupDone,
    wifi: { ssid: 'ChairPlay-5G', security: 'WPA_WPA2', passphraseSet: true },
  })
  return { ...base, setup: { ...base.setup, mfiPresent: true, firewall: 'present', pairedIphone, pcWifi: 'ChairPlay-5G' } }
}

describe('Pair the iPhone after Forget', () => {
  // The iPhone forgets the PC too; with Windows still holding the old pairing, it never answers over Bluetooth.
  it('stays open while Windows keeps the forgotten iPhone, and is done once the iPhone is paired again', async () => {
    const backend = new FakeBackend()
    const { rerender } = render(<Shell state={paired('Iphone15', true)} backend={backend} />)
    await screen.findAllByRole('switch')
    fireEvent.click(control('tab-connection', 'tab'))
    fireEvent.click(control('connection-forget', 'button'))
    expect(backend.forgets).toBe(1)

    // Get started opens with the step asking to remove the iPhone in Windows, not done.
    rerender(<Shell state={paired('Iphone15', false)} backend={backend} />)
    fireEvent.click(control('setup-pair-forgotten', 'button'))
    expect(backend.opened).toEqual(['bluetooth'])
    expect(absent('setup-pair-bluetooth')).toBe(true)

    // Removed in Windows: the step asks to pair it, as on a first start.
    rerender(<Shell state={paired(null, false)} backend={backend} />)
    expect(absent('setup-pair-forgotten')).toBe(true)
    control('setup-pair-bluetooth', 'button')

    // Paired again: the step is done and closed.
    rerender(<Shell state={paired('Iphone15', false)} backend={backend} />)
    expect(absent('setup-pair-forgotten')).toBe(true)
    expect(absent('setup-pair-bluetooth')).toBe(true)
  })

  // Removed and paired again in one visit to Windows Settings: the page never sees the iPhone gone.
  it('lets Connect through, and counts the iPhone paired once a connection gets past Bluetooth', async () => {
    const backend = new FakeBackend()
    const { rerender } = render(<Shell state={paired('Iphone15', true)} backend={backend} />)
    await screen.findAllByRole('switch')
    fireEvent.click(control('tab-connection', 'tab'))
    fireEvent.click(control('connection-forget', 'button'))
    rerender(<Shell state={paired('Iphone15', false)} backend={backend} />)
    control('setup-pair-forgotten', 'button')

    fireEvent.click(control('tb-connect', 'button'))
    expect(backend.connects).toBe(1)

    rerender(<Shell state={{ ...paired('Iphone15', false), phase: 'joining' }} backend={backend} />)
    rerender(<Shell state={paired('Iphone15', false)} backend={backend} />)
    expect(absent('setup-pair-forgotten')).toBe(true)
    expect(absent('setup-pair-bluetooth')).toBe(true)
  })

  it('is done on a first start with the iPhone already paired in Windows', async () => {
    render(<Shell state={paired('Iphone15', false)} backend={new FakeBackend()} />)
    await screen.findByTestId('setup-skip')
    expect(absent('setup-pair-forgotten')).toBe(true)
    expect(absent('setup-pair-bluetooth')).toBe(true)
  })
})
