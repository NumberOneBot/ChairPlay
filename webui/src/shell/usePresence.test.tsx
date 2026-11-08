import { act, cleanup, render } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { usePresence } from './usePresence'

function Page({ backend, page }: { backend: FakeBackend; page: string }) {
  usePresence(backend, page)
  return null
}

function hide() {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
  act(() => void document.dispatchEvent(new Event('visibilitychange')))
}

beforeEach(() => vi.useFakeTimers())

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  Reflect.deleteProperty(document, 'visibilityState')
})

describe('usePresence', () => {
  it('a visible tab reports itself at once and every two seconds', async () => {
    const backend = new FakeBackend()
    render(<Page backend={backend} page="abc" />)
    expect(backend.presences).toEqual(['visible'])

    await act(() => vi.advanceTimersByTimeAsync(4000))
    expect(backend.presences).toEqual(['visible', 'visible', 'visible'])
  })

  it('a hidden tab says so once and stops', async () => {
    const backend = new FakeBackend()
    render(<Page backend={backend} page="abc" />)

    hide()
    expect(backend.presences).toEqual(['visible', 'hidden'])
    await act(() => vi.advanceTimersByTimeAsync(4000))
    expect(backend.presences).toEqual(['visible', 'hidden'])
  })

  it('a tab going away says so', () => {
    const backend = new FakeBackend()
    render(<Page backend={backend} page="abc" />)

    act(() => void window.dispatchEvent(new Event('pagehide')))
    expect(backend.presences).toEqual(['visible', 'closing'])
  })

  // Kept in the back/forward cache, the tab may come back; being hidden has already let the settings go.
  it('a tab kept for going back does not say it closes', () => {
    const backend = new FakeBackend()
    render(<Page backend={backend} page="abc" />)

    act(() => void window.dispatchEvent(Object.assign(new Event('pagehide'), { persisted: true })))
    expect(backend.presences).toEqual(['visible'])
  })

  // Back from that cache, it reports itself again rather than wait for its next heartbeat.
  it('a tab back from going back reports itself at once', () => {
    const backend = new FakeBackend()
    render(<Page backend={backend} page="abc" />)

    act(() => void window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true })))
    expect(backend.presences).toEqual(['visible', 'visible'])
  })

  it('the window, the controls window and a shared page never report', async () => {
    for (const page of ['window', 'controls', 'shared']) {
      const backend = new FakeBackend()
      render(<Page backend={backend} page={page} />)
      hide()
      act(() => void window.dispatchEvent(new Event('pagehide')))
      await act(() => vi.advanceTimersByTimeAsync(4000))
      expect(backend.presences).toEqual([])
      cleanup()
      Reflect.deleteProperty(document, 'visibilityState')
    }
  })
})
