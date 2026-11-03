import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { Shell } from '../shell/Shell'
import { FakeBackend } from '../fakeBackend'
import { appState } from '../testing'

afterEach(cleanup)

it('saves the global switch immediately and preserves the shared bindings', async () => {
  const backend = new FakeBackend()
  const before = structuredClone(backend.bindings!.actions)
  render(<Shell state={appState()} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  fireEvent.click(screen.getByRole('tab', { name: 'Keyboard' }))
  const toggle = await screen.findByRole('switch', { name: 'Global shortcuts' })
  fireEvent.click(toggle)
  await waitFor(() => expect(backend.controlsSaved).toEqual([{ 'global.enabled': 'true' }]))
  expect(backend.bindings!.actions).toEqual(before)
  fireEvent.click(toggle)
  await waitFor(() => expect(backend.controlsSaved.at(-1)).toEqual({ 'global.enabled': 'false' }))
  expect(backend.bindings!.actions).toEqual(before)
  expect(backend.reconnects).toBe(0)
})
