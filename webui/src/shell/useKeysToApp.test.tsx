import { cleanup, fireEvent, render } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { useKeysToApp } from './useKeysToApp'

afterEach(cleanup)

it('releases page-owned keys on blur so native background input cannot leave them held', () => {
  const backend = new FakeBackend()
  function Page() { useKeysToApp(backend, true); return null }
  render(<Page />)
  fireEvent.keyDown(window, { code: 'Enter', key: 'Enter' })
  fireEvent.blur(window)
  expect(backend.keys).toEqual(['Enter press everything', 'Enter release everything'])
})

// Over a page the keyboard's track keys reach the app too, by the names controls.properties gives them.
it('hands the track keys on by their file names', () => {
  const backend = new FakeBackend()
  function Page() { useKeysToApp(backend, false); return null }
  render(<Page />)
  for (const code of ['MediaTrackPrevious', 'MediaPlayPause', 'MediaTrackNext']) fireEvent.keyDown(window, { code })
  expect(backend.keys).toEqual(['MediaPrevious press settings', 'MediaPlayPause press settings', 'MediaNext press settings'])
})
