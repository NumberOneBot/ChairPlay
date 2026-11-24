import { cleanup, render } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { absent } from '../testing'
import { ALL_FEATURES, setPlatform, supports, WhenSupported, WINDOWS } from './platform'
import { TEXTS, t, type TextKey } from './texts'

afterEach(() => {
  cleanup()
  setPlatform(WINDOWS)
})

// Each text is the system's own when it has one, the shared one otherwise (owner, 2026-11-24); no test reads the words.
describe('texts', () => {
  it('picks the platform’s own variant, and the shared one for a platform without its own', () => {
    const entry = TEXTS['audio.systemDefault']
    setPlatform({ ...WINDOWS, platform: 'windows', features: [] })
    expect(t('audio.systemDefault')).toBe(entry.windows)
    setPlatform({ ...WINDOWS, platform: 'linux', features: [] })
    expect(t('audio.systemDefault')).toBe(entry.default)
  })

  it('fills in its parameters', () => {
    const text = t('pair.forgotten', { name: 'Alex’s iPhone' })

    expect(text).toContain('Alex’s iPhone')
    expect(text).not.toContain('{')
  })

  it('has a text for every platform in every entry', () => {
    for (const platform of ['windows', 'linux', 'macos'] as const) {
      setPlatform({ ...WINDOWS, platform, features: [] })
      for (const key of Object.keys(TEXTS) as TextKey[]) expect(t(key), `${key} on ${platform}`).not.toBe('')
    }
  })
})

describe('features', () => {
  it('are there until the app says which the platform has', () => {
    expect(ALL_FEATURES.every(supports)).toBe(true)
  })

  it('show a part only where the platform has its feature', () => {
    setPlatform({ ...WINDOWS, platform: 'linux', features: ['gameBridge'] })
    render(<>
      <WhenSupported feature="gameBridge"><span data-testid="bridge" /></WhenSupported>
      <WhenSupported feature="usbGps"><span data-testid="usb" /></WhenSupported>
    </>)

    expect(absent('bridge')).toBe(false)
    expect(absent('usb')).toBe(true)
  })
})
