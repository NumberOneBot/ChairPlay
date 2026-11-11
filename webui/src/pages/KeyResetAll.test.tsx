// Reset all keys at the end of the Keyboard page: every changed action back to its defaults in one save (owner).
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import { appState, control } from '../testing'

afterEach(cleanup)

async function keyboard(backend: FakeBackend) {
  render(<Shell state={appState()} backend={backend} />)
  await waitFor(() => control('tab-keyboard', 'tab'))
  fireEvent.click(control('tab-keyboard', 'tab'))
  await screen.findByTestId('keys-reset-all')
  return control('keys-reset-all', 'button') as HTMLButtonElement
}

describe('Reset all keys', () => {
  it('waits while every key is the default', async () => {
    const backend = new FakeBackend()
    for (const action of Object.values(backend.bindings!.actions)) action.keys = [...action.defaults]
    expect((await keyboard(backend)).disabled).toBe(true)
  })

  it('saves the defaults of the changed actions alone, in one save', async () => {
    const backend = new FakeBackend()
    const actions = backend.bindings!.actions
    for (const action of Object.values(actions)) action.keys = [...action.defaults]
    actions['key.home'].keys = ['F2']
    actions['key.siri'].keys = []

    fireEvent.click(await keyboard(backend))

    await waitFor(() =>
      expect(backend.controlsSaved).toEqual([
        { 'key.home': actions['key.home'].defaults.join(', '), 'key.siri': actions['key.siri'].defaults.join(', ') },
      ]),
    )
  })
})
