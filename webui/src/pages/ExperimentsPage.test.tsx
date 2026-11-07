import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

const live = { phase: 'active', canShowCarPlay: true } as const

async function experiments(state: AppState = appState(live), backend = new FakeBackend()) {
  render(<Shell state={state} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  fireEvent.click(screen.getByRole('tab', { name: 'Experiments' }))
  return backend
}

/** A backend whose settings have the AltScreen on, which the demos play on. */
function altscreenOn() {
  const backend = new FakeBackend()
  backend.data = { ...backend.data, values: { ...backend.data.values, 'altscreen.enabled': 'true' } }
  return backend
}

const button = (row: string) =>
  screen.getByText(row, { selector: 'label' }).parentElement!.querySelector('button') as HTMLButtonElement

describe('Experiments page', () => {
  // The HUD's demos play on the AltScreen, for watching its maneuvers and lanes without an iPhone.
  it('plays the HUD demos on the AltScreen', async () => {
    const backend = await experiments(appState(live), altscreenOn())

    fireEvent.click(button('Maneuvers'))
    fireEvent.click(button('Lane changes'))

    await waitFor(() => expect(backend.demos).toEqual(['maneuvers', 'lanes']))
  })

  it('stops the demo playing', async () => {
    const backend = await experiments(appState({ ...live, demo: { playing: 'lanes', available: true } }), altscreenOn())

    fireEvent.click(button('Lane changes'))

    await waitFor(() => expect(backend.demos).toEqual([null]))
  })

  // Unticked on its page, the AltScreen is off, though this connection still has it: no demo to start; one playing
  // can still be stopped.
  it('starts no demo while the AltScreen is turned off', async () => {
    const backend = await experiments(appState({ ...live, demo: { playing: 'lanes', available: true } }))

    expect(button('Maneuvers').disabled).toBe(true)
    expect(button('Lane changes').disabled).toBe(false)
    fireEvent.click(button('Lane changes'))

    await waitFor(() => expect(backend.demos).toEqual([null]))
  })

  // The iPhone's route, song, call, and battery messages to a file in logs\, from the next start.
  it('turns the CarPlay data log on', async () => {
    const backend = await experiments()

    fireEvent.click(screen.getByRole('switch', { name: "Write the iPhone's messages to a file" }))

    await waitFor(() => expect(backend.saved).toEqual([{ 'log.carplay.data': 'true' }]))
  })

  // How much the app's log in the logs folder tells, at once; Trace adds DiPlay's packet-by-packet lines.
  it('sets the log level', async () => {
    const backend = await experiments()
    const level = screen.getByRole('radiogroup', { name: 'Log level' })

    expect(within(level).getByRole('radio', { name: 'Info' }).getAttribute('aria-checked')).toBe('true')
    fireEvent.click(within(level).getByRole('radio', { name: 'Trace' }))

    await waitFor(() => expect(backend.saved).toEqual([{ 'log.level': 'trace' }]))
  })

  it('opens the logs and screenshots folders', async () => {
    const backend = await experiments()

    fireEvent.click(screen.getByRole('button', { name: 'Logs' }))
    fireEvent.click(screen.getByRole('button', { name: 'Screenshots' }))

    expect(backend.opened).toEqual(['logs', 'screenshots'])
  })

  // Under each folder button, what the folder holds; an empty or missing one shows nothing.
  it("shows a folder's size, none for an empty one, and asks again", async () => {
    const backend = new FakeBackend()
    backend.sizes = { logs: 1536, screenshots: 0 }
    await experiments(appState(live), backend)

    expect(await screen.findByText('1.50 KB')).toBeTruthy()
    expect(screen.queryByText('0 bytes')).toBeNull()
    backend.sizes = { logs: 1536, screenshots: 2048 }
    expect(await screen.findByText('2.00 KB', {}, { timeout: 8000 })).toBeTruthy()
  }, 10000)

  it('saves sharing the CarPlay picture', async () => {
    const backend = await experiments()

    fireEvent.click(screen.getByRole('switch', { name: 'Share the CarPlay picture' }))

    await waitFor(() => expect(backend.saved).toEqual([{ 'display.share': 'true' }]))
  })

  it('saves driving the GPS car from other apps', async () => {
    const backend = new FakeBackend()
    backend.data = { ...backend.data, values: { ...backend.data.values, 'gps.enabled': 'true' } }
    await experiments(appState(live), backend)

    fireEvent.click(screen.getByRole('switch', { name: 'Drive the GPS car from other apps' }))

    await waitFor(() => expect(backend.saved).toEqual([{ 'gps.external': 'true' }]))
  })

  it('the GPS off disables driving from other apps', async () => {
    const backend = new FakeBackend()
    backend.data = { ...backend.data, values: { ...backend.data.values, 'gps.enabled': 'false' } }
    await experiments(appState(live), backend)

    const drive = screen.getByRole('switch', { name: 'Drive the GPS car from other apps' }) as HTMLInputElement
    expect(drive.disabled).toBe(true)
  })

  it('waits for the AltScreen', async () => {
    await experiments(appState({ ...live, demo: { playing: null, available: false } }))

    expect(button('Maneuvers').disabled).toBe(true)
  })
})
