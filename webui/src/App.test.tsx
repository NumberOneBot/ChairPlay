import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AppState } from './api'
import { App } from './App'
import { FakeBackend } from './fakeBackend'
import { appState } from './testing'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  history.replaceState(null, '', '/')
})

/**
 * Opens the app at [address], its event stream sending [state]; answers the requests the page makes, and lists the
 * actions it asks for (not where the keyboard is, which every page reports, nor the setup checks Connection asks for).
 */
function open(address: string, state: AppState) {
  const posts: string[] = []
  const settings = new FakeBackend().data
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    if (init.method === 'POST' && url !== '/api/keyboard-focus' && url !== '/api/setup-check') posts.push(url)
    return new Response(JSON.stringify(url === '/api/settings' ? settings : {}), { status: 200 })
  })
  let source: { onmessage: ((event: MessageEvent) => void) | null } | undefined
  vi.stubGlobal('EventSource', class {
    onmessage: ((event: MessageEvent) => void) | null = null
    onerror: (() => void) | null = null
    constructor() {
      source = this
    }
    close() {}
  })
  history.replaceState(null, '', address)
  render(<App />)
  act(() => source!.onmessage!(new MessageEvent('message', { data: JSON.stringify(state) })))
  return posts
}

// The app's state says what its window shows; ChairPlay's window opens the page at ?window, a browser tab without it.
describe('the window and a browser tab', () => {
  const live = { phase: 'active', canShowCarPlay: true, embedded: true } as const

  it('the window waits black while the app connects on its own; a tab shows the settings', () => {
    open('/?window', appState({ ...live, waiting: true }))
    expect(screen.queryByRole('button', { name: 'CarPlay' })).toBeNull()
    cleanup()

    open('/', appState({ ...live, waiting: true }))
    expect(screen.getByRole('button', { name: 'CarPlay' })).toBeTruthy()
  })

  it('Esc shows CarPlay from the window, not from a tab', () => {
    const fromWindow = open('/?window', appState(live))
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(fromWindow).toEqual(['/api/screen'])
    cleanup()

    const fromTab = open('/', appState(live))
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(fromTab).toEqual([])
  })

  it('Open asks the app to open the browser, from the window only', async () => {
    const fromWindow = open('/?window', appState(live))
    fireEvent.click(await screen.findByRole('button', { name: 'Open' }))
    expect(fromWindow).toEqual(['/api/open-in-browser'])
    cleanup()

    open('/', appState(live))
    await screen.findByRole('switch', { name: 'Allow access from the local network' })
    expect(screen.queryByRole('button', { name: 'Open' })).toBeNull()
  })
})
