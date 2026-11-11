import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import type { Settings } from '../shell/useSettings'
import { absent, appState, control } from '../testing'
import { BrowserAccess } from './BrowserAccess'

afterEach(cleanup)

const settings: Settings = { data: null, loadError: null, value: () => '', error: () => undefined, save: async () => true }

const uiServer = (overrides: Partial<AppState['uiServer']>): AppState['uiServer'] =>
  ({ port: 7529, configuredPort: 7529, network: false, urls: [], ...overrides })

const browserAccess = (overrides: Partial<AppState['uiServer']>) =>
  render(<BrowserAccess state={appState({ uiServer: uiServer(overrides) })} backend={new FakeBackend()} settings={settings} />)

/** The shell on the Application page, where the settings browser is, once the settings are in. */
async function onApplication(backend = new FakeBackend(), server = uiServer({})) {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true, uiServer: server })} backend={backend} />)
  fireEvent.click(control('tab-application', 'tab'))
  await screen.findByTestId('browser-port')
  return backend
}

describe('Settings browser', () => {
  // It moved to Application: nothing on Connection opens the settings to the network any more.
  it('is not on the Connection page', async () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
    await screen.findAllByRole('switch')

    for (const toggle of screen.getAllByRole('switch')) fireEvent.click(toggle)

    await waitFor(() => expect(backend.saved.length).toBeGreaterThan(0))
    expect(backend.saved.flatMap(Object.keys)).not.toContain('ui.network')
    expect(absent('browser-port')).toBe(true)
  })

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

  it('saves the local network switch', async () => {
    const backend = await onApplication()

    fireEvent.click(control('browser-network', 'switch'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'ui.network': 'true' }]))
  })

  // ui.port as saved, though this run fell back to another port.
  it('shows the saved port', async () => {
    const backend = new FakeBackend()
    backend.data.values['ui.port'] = '8080'
    await onApplication(backend, uiServer({ port: 51234, configuredPort: 8080 }))

    expect((control('browser-port', 'textbox') as HTMLInputElement).value).toBe('8080')
  })

  it('saves a port typed in, or keeps a refused one marked', async () => {
    const backend = await onApplication()
    const port = control('browser-port', 'textbox') as HTMLInputElement

    fireEvent.change(port, { target: { value: '8080' } })
    fireEvent.keyDown(port, { key: 'Enter' })
    await waitFor(() => expect(backend.saved).toEqual([{ 'ui.port': '8080' }]))

    backend.nextOutcome = { ok: false, errors: { 'ui.port': 'Enter a port from 1 to 65535' } }
    fireEvent.change(port, { target: { value: '70000' } })
    fireEvent.keyDown(port, { key: 'Enter' })

    await waitFor(() => expect(port.getAttribute('aria-invalid')).toBe('true'))
    expect(port.value).toBe('70000')
  })

  it('lists the addresses while the network is on', () => {
    browserAccess({ network: true, urls: ['http://192.168.1.20:7529'] })
    expect(screen.getByText('http://192.168.1.20:7529')).toBeTruthy()
  })

  // A link in the window would take its own page away: the app opens the default browser there instead.
  it("opens a local network address in the default browser from the window", () => {
    const backend = new FakeBackend()
    const state = appState({ embedded: true, uiServer: uiServer({ network: true, urls: ['http://192.168.1.20:7529'] }) })
    render(<BrowserAccess state={state} backend={backend} settings={settings} />)

    const link = screen.getByRole('link', { name: 'http://192.168.1.20:7529' })
    expect(fireEvent.click(link)).toBe(false)

    expect(backend.browserOpened).toEqual(['http://192.168.1.20:7529'])
  })

  // A tab is in a browser: the address is a plain link to a new tab.
  it('links a local network address from a browser tab', () => {
    const backend = new FakeBackend()
    const state = appState({ embedded: false, uiServer: uiServer({ network: true, urls: ['http://192.168.1.20:7529'] }) })
    render(<BrowserAccess state={state} backend={backend} settings={settings} />)

    const link = screen.getByRole('link', { name: 'http://192.168.1.20:7529' }) as HTMLAnchorElement
    fireEvent.click(link)

    expect(link.href).toBe('http://192.168.1.20:7529/')
    expect(link.target).toBe('_blank')
    expect(backend.browserOpened).toEqual([])
  })

  // The switch applies at the next start: the addresses follow what this run listens on, not the saved value.
  it('lists no address while this run listens on this PC only', () => {
    browserAccess({ network: false, urls: ['http://192.168.1.20:7529'] })
    expect(screen.queryByText('http://192.168.1.20:7529')).toBeNull()
  })
})
