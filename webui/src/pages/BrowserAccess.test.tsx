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
  render(<BrowserAccess state={appState({ uiServer: uiServer(overrides) })} backend={new FakeBackend()} settings={settings} />)

const openInBrowser = () => screen.getByRole('button', { name: 'Open in browser' }) as HTMLButtonElement
const lock = (lock: Partial<AppState['lock']>): AppState['lock'] =>
  ({ mine: false, holder: null, shared: false, handOver: false, ...lock })

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

  it('hands the settings from the window over to the browser', () => {
    const backend = new FakeBackend()
    const state = appState({ embedded: true, lock: lock({ mine: true, holder: 'window' }) })
    render(<BrowserAccess state={state} backend={backend} settings={settings} />)

    fireEvent.click(openInBrowser())

    expect(backend.browserOpens).toBe(1)
  })

  // A tab is in a browser already, and a window without the settings has none to hand over.
  it('cannot open the browser from a tab, or from a window without the settings', () => {
    for (const state of [
      appState({ embedded: false, lock: lock({ mine: true, holder: 'tab' }) }),
      appState({ embedded: true, lock: lock({ holder: 'tab' }) }),
    ]) {
      const backend = new FakeBackend()
      render(<BrowserAccess state={state} backend={backend} settings={settings} />)

      expect(openInBrowser().disabled).toBe(true)
      fireEvent.click(openInBrowser())
      expect(backend.browserOpens).toBe(0)
      cleanup()
    }
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
