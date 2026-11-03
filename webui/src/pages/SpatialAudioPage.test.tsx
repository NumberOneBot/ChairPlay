import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

async function open(mode: string) {
  const backend = new FakeBackend()
  backend.data.values['audio.music.mode'] = mode
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  fireEvent.click(screen.getByRole('tab', { name: 'Audio' }))
  return backend
}

describe('Buffered playback compatibility', () => {
  it('treats a saved spatial preference as buffered without rewriting it on open', async () => {
    const backend = await open('spatial')
    expect(screen.getByRole('radio', { name: 'Buffered' }).getAttribute('aria-checked')).toBe('true')
    expect(backend.saved).toEqual([])
    fireEvent.click(screen.getByRole('radio', { name: 'Standard' }))
    await waitFor(() => expect(backend.saved).toEqual([{ 'audio.music.mode': 'standard' }]))
    expect(backend.reconnects).toBe(0)
  })
})
