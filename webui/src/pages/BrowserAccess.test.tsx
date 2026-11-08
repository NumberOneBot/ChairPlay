import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import type { Settings } from '../shell/useSettings'
import { appState } from '../testing'
import { BrowserAccess } from './BrowserAccess'

afterEach(cleanup)

const settings: Settings = { data: null, loadError: null, value: () => '', error: () => undefined, save: async () => true }

const uiServer = (overrides: Partial<AppState['uiServer']>): AppState['uiServer'] =>
  ({ port: 7529, configuredPort: 7529, network: false, urls: [], ...overrides })

const browserAccess = (overrides: Partial<AppState['uiServer']>) =>
  render(<BrowserAccess state={appState({ uiServer: uiServer(overrides) })} settings={settings} />)

describe('Browser access', () => {
  it('says when the port in the settings was taken', () => {
    browserAccess({ port: 51234, configuredPort: 7529 })
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('says nothing while the port is the one asked for', () => {
    browserAccess({ port: 7529, configuredPort: 7529 })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  // CHAIRPLAY_UI_PORT=0 asks for any free port, so whichever it got is the one asked for.
  it('says nothing when any free port was asked for', () => {
    browserAccess({ port: 51234, configuredPort: 0 })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('saves the home network switch', async () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })

    fireEvent.click(screen.getByRole('switch', { name: 'Allow access from the home network' }))

    await waitFor(() => expect(backend.saved).toEqual([{ 'ui.network': 'true' }]))
  })

  it('lists the addresses while the network is on', () => {
    browserAccess({ network: true, urls: ['http://192.168.1.20:7529/'] })
    expect(screen.getByText('http://192.168.1.20:7529/')).toBeTruthy()
  })

  // The switch applies at the next start: the addresses follow what this run listens on, not the saved value.
  it('lists no address while this run listens on this PC only', () => {
    browserAccess({ network: false, urls: ['http://192.168.1.20:7529/'] })
    expect(screen.queryByText('http://192.168.1.20:7529/')).toBeNull()
  })
})
