import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import { appState, control } from '../testing'

afterEach(cleanup)

const MOVED = ['window.always.on.top', 'window.front.on.call', 'altscreen.always.on.top']

/** The shell on the page of tab [tab], once the settings are in. */
async function page(tab: string, backend: FakeBackend) {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
  fireEvent.click(control(`tab-${tab}`, 'tab'))
  // Each of these pages has a segmented group once the settings are in.
  await screen.findAllByRole('radiogroup')
}

describe('Windows', () => {
  it.each([
    ['app-carplay-on-top', 'window.always.on.top'],
    ['app-carplay-front-on-call', 'window.front.on.call'],
    ['app-altscreen-on-top', 'altscreen.always.on.top'],
  ])('%s saves only its own window preference without reconnecting', async (testId, key) => {
    const backend = new FakeBackend()
    await page('application', backend)
    const toggle = control(testId, 'switch') as HTMLInputElement
    expect(toggle.checked).toBe(false)

    fireEvent.click(toggle)
    await waitFor(() => expect(backend.saved).toEqual([{ [key]: 'true' }]))
    fireEvent.click(toggle)
    await waitFor(() => expect(backend.saved).toEqual([{ [key]: 'true' }, { [key]: 'false' }]))
    expect(backend.reconnects).toBe(0)
  })

  // The windows' switches live on Application alone: no switch left on CarPlay or AltScreen saves one of them.
  it.each(['carplay', 'altscreen'])('the %s page has no window switch left', async (tab) => {
    const backend = new FakeBackend()
    backend.data.values['altscreen.enabled'] = 'true'
    await page(tab, backend)

    for (const toggle of screen.queryAllByRole('switch')) fireEvent.click(toggle)

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(backend.saved.flatMap(Object.keys).filter((key) => MOVED.includes(key))).toEqual([])
  })
})
