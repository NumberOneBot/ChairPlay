import { act, cleanup, render } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { useKeyboardFocus } from './useKeyboardFocus'

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers() })

// A phone or PC opening http://<this PC's address>:7529 is no secure context: crypto.randomUUID is not there.
it('reports its focus where crypto.randomUUID is missing', () => {
  vi.spyOn(document, 'hasFocus').mockImplementation(() => true)
  vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => bytes.fill(7) })
  const backend = new FakeBackend()
  function Page() { useKeyboardFocus(backend); return null }
  render(<Page />)

  expect(backend.keyboardFocusCalls).toEqual([true])
})

it('renews a focused page lease and releases it on blur and unmount', () => {
  vi.useFakeTimers()
  let focused = true
  vi.spyOn(document, 'hasFocus').mockImplementation(() => focused)
  const backend = new FakeBackend()
  function Page() { useKeyboardFocus(backend); return null }
  const page = render(<Page />)
  expect(backend.keyboardFocusCalls).toEqual([true])
  act(() => { vi.advanceTimersByTime(1000) })
  expect(backend.keyboardFocusCalls).toEqual([true, true, true])
  focused = false
  act(() => { window.dispatchEvent(new Event('blur')) })
  expect(backend.keyboardFocusCalls.at(-1)).toBe(false)
  page.unmount()
  const count = backend.keyboardFocusCalls.length
  act(() => { vi.advanceTimersByTime(5000) })
  expect(backend.keyboardFocusCalls.length).toBe(count)
  expect(backend.keyboardFocusCalls.at(-1)).toBe(false)
})
