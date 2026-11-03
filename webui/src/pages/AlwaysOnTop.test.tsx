import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

describe('Always on top', () => {
  it.each([['CarPlay', 'window.always.on.top'], ['AltScreen', 'altscreen.always.on.top']])(
    'saves only the %s window preference without reconnecting', async (tab, key) => {
      const backend = new FakeBackend()
      backend.data.values['altscreen.enabled'] = 'true'
      render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
      await screen.findByRole('textbox', { name: 'Network' })
      fireEvent.click(screen.getByRole('tab', { name: tab }))
      const toggle = screen.getByRole('switch', { name: 'Always on top' }) as HTMLInputElement
      expect(toggle.checked).toBe(false)
      fireEvent.click(toggle)
      await waitFor(() => expect(backend.saved).toEqual([{ [key]: 'true' }]))
      fireEvent.click(toggle)
      await waitFor(() => expect(backend.saved).toEqual([{ [key]: 'true' }, { [key]: 'false' }]))
      expect(backend.reconnects).toBe(0)
    },
  )
})
