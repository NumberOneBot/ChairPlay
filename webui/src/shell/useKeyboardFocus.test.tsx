import { act, cleanup, render } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { useKeyboardFocus } from './useKeyboardFocus'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
  window.history.replaceState(null, '', '/')
})

let focused = true
function page(global = true) {
  vi.useFakeTimers()
  vi.spyOn(document, 'hasFocus').mockImplementation(() => focused)
  const backend = new FakeBackend()
  function Page() { useKeyboardFocus(backend, global); return null }
  return { backend, view: render(<Page />) }
}
const fire = (target: EventTarget, type: string) => act(() => { target.dispatchEvent(new Event(type)) })
const wait = (ms: number) => act(() => { vi.advanceTimersByTime(ms) })

// A phone or PC opening http://<this PC's address>:7529 is no secure context: crypto.randomUUID is not there.
it('reports its focus where crypto.randomUUID is missing', () => {
  focused = true
  vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => bytes.fill(7) })
  const { backend } = page()
  expect(backend.keyboardFocusCalls).toEqual([true])
})

it('renews a focused page lease and releases it on blur and unmount', () => {
  focused = true
  const { backend, view } = page()
  expect(backend.keyboardFocusCalls).toEqual([true])
  wait(2000)
  expect(backend.keyboardFocusCalls).toEqual([true, true, true])
  focused = false
  fire(window, 'blur')
  expect(backend.keyboardFocusCalls).toEqual([true, true, true, false])
  focused = true
  fire(window, 'focus')
  expect(backend.keyboardFocusCalls.at(-1)).toBe(true)
  view.unmount()
  const count = backend.keyboardFocusCalls.length
  wait(5000)
  expect(backend.keyboardFocusCalls.length).toBe(count)
  expect(backend.keyboardFocusCalls.at(-1)).toBe(false)
})

it('sends nothing while the page has no focus', () => {
  focused = false
  const { backend } = page()
  wait(10000)
  expect(backend.keyboardFocusCalls).toEqual([])
})

it('stops renewing when the page is hidden', () => {
  focused = true
  const { backend } = page()
  focused = false
  fire(document, 'visibilitychange')
  wait(10000)
  expect(backend.keyboardFocusCalls).toEqual([true, false])
})

it('sends nothing while global shortcuts are off', () => {
  focused = true
  const { backend } = page(false)
  fire(window, 'focus')
  fire(window, 'blur')
  wait(10000)
  expect(backend.keyboardFocusCalls).toEqual([])
})

// The app's own window is the app's foreground already: its keys never count as pressed in the background.
it('sends nothing from the app window page', () => {
  focused = true
  window.history.replaceState(null, '', '/?window')
  const { backend } = page()
  fire(window, 'focus')
  wait(10000)
  expect(backend.keyboardFocusCalls).toEqual([])
})
