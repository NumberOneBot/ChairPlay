import { cleanup, fireEvent, render } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { setPlatform, WINDOWS } from '../i18n/platform'
import { absent, control } from '../testing'
import { SystemPageButton } from './SystemPageButton'

afterEach(() => {
  cleanup()
  setPlatform(WINDOWS)
})

// A card's button to the system's own page shows only where the system has that page (owner, 2026-11-24).
it('opens the system page it names', () => {
  const backend = new FakeBackend()
  render(<SystemPageButton place="bluetooth" backend={backend} title="">x</SystemPageButton>)

  fireEvent.click(control('open-bluetooth', 'button'))

  expect(backend.opened).toEqual(['bluetooth'])
})

it('is not there where the system has no such page', () => {
  setPlatform({ ...WINDOWS, platform: 'linux', systemPages: ['wifi'] })
  render(<>
    <SystemPageButton place="bluetooth" backend={new FakeBackend()} title="">x</SystemPageButton>
    <SystemPageButton place="wifi" backend={new FakeBackend()} title="">y</SystemPageButton>
  </>)

  expect(absent('open-bluetooth')).toBe(true)
  expect(absent('open-wifi')).toBe(false)
})
