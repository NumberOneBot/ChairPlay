import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { PageLock } from '../api'
import { FakeBackend } from '../fakeBackend'
import { appState } from '../testing'
import { Shell } from './Shell'

afterEach(cleanup)

const lock = (lock: Partial<PageLock>): PageLock => ({ mine: false, holder: null, shared: false, handOver: false, ...lock })
const live = { phase: 'active', canShowCarPlay: true } as const

describe('Where the settings are open', () => {
  it('a tab without the settings shows only a notice', () => {
    for (const holder of ['window', 'tab'] as const) {
      render(<Shell state={appState({ ...live, embedded: false, lock: lock({ holder }) })} backend={new FakeBackend()} />)

      expect(screen.queryByRole('tablist'), holder).toBeNull()
      expect(screen.queryByRole('button', { name: 'Connect' })).toBeNull()
      expect(screen.queryByRole('button', { name: 'CarPlay' })).toBeNull()
      expect(screen.getByRole('status')).toBeTruthy()
      cleanup()
    }
  })

  it('a tab that holds them, and a shared page, show the settings', () => {
    for (const held of [lock({ mine: true, holder: 'tab' }), lock({ holder: 'window', shared: true })]) {
      render(<Shell state={appState({ ...live, embedded: false, lock: held })} backend={new FakeBackend()} />)

      expect(screen.getByRole('tablist')).toBeTruthy()
      cleanup()
    }
  })

  it('the window shows the waiting notice while a tab holds them, or during a hand-over', () => {
    for (const elsewhere of [lock({ holder: 'tab' }), lock({ holder: null, handOver: true })]) {
      render(<Shell state={appState({ ...live, embedded: true, lock: elsewhere })} backend={new FakeBackend()} />)

      expect(screen.queryByRole('tablist')).toBeNull()
      expect((screen.getByRole('button', { name: 'Settings' }) as HTMLButtonElement).disabled).toBe(true)
      cleanup()
    }
  })

  it('Esc still shows CarPlay from the window\'s notice', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ ...live, embedded: true, lock: lock({ holder: 'tab' }) })} backend={backend} />)

    fireEvent.keyDown(window, { key: 'Escape' })

    expect(backend.shown).toEqual(['carplay'])
  })

  it('the window behind CarPlay with no holder keeps its settings', () => {
    const state = appState({ ...live, embedded: true, screen: 'carplay', lock: lock({ holder: null }) })
    render(<Shell state={state} backend={new FakeBackend()} />)

    expect(screen.getByRole('tablist')).toBeTruthy()
  })
})
