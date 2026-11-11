import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { Shell } from '../shell/Shell'
import { appState, control } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

const firstStart: AppState = appState({
  phase: 'idle',
  setupDone: false,
  wifi: { ssid: '', security: 'WPA_WPA2', passphraseSet: false },
})

/** The window has the focus back: the user is back from Windows Settings or File Explorer. */
const focusBack = () => act(() => void window.dispatchEvent(new Event('focus')))

// The app reads Bluetooth, the network, the firewall, and the MFi files only when these pages ask, not on a timer.
describe('Setup checks', () => {
  it('Get started asks as it opens and as the window has the focus back', async () => {
    const backend = new FakeBackend()
    render(<Shell state={firstStart} backend={backend} />)
    await screen.findByRole('heading', { name: 'Get started' })

    await waitFor(() => expect(backend.setupChecks).toBe(1))
    focusBack()
    await waitFor(() => expect(backend.setupChecks).toBe(2))
  })

  it('Connection asks as it opens and as the window has the focus back, and no other page does', async () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })

    await waitFor(() => expect(backend.setupChecks).toBe(1))
    focusBack()
    await waitFor(() => expect(backend.setupChecks).toBe(2))

    fireEvent.click(control('tab-application', 'tab'))
    focusBack()
    expect(backend.setupChecks).toBe(2)
  })
})
