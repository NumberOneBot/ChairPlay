import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import { absent, appState, control } from '../testing'

afterEach(cleanup)

/** The shell on the Application page, once the settings are in. */
async function application(backend = new FakeBackend()) {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
  fireEvent.click(control('tab-application', 'tab'))
  await screen.findByTestId('app-tint-black')
  return backend
}

describe('Application page', () => {
  it('takes the About page place in the navigation', async () => {
    await application()

    expect(absent('tab-about')).toBe(true)
    expect(control('tab-application', 'tab').getAttribute('aria-selected')).toBe('true')
  })

  it('shows black until a tint is chosen, and saves the one chosen', async () => {
    const backend = await application()
    expect(control('app-tint-black', 'radio').getAttribute('aria-checked')).toBe('true')

    fireEvent.click(control('app-tint-slate', 'radio'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'ui.tint': 'slate' }]))
  })

  it('shows the saved tint', async () => {
    const backend = new FakeBackend()
    backend.data.values['ui.tint'] = 'warm'
    await application(backend)

    expect(control('app-tint-warm', 'radio').getAttribute('aria-checked')).toBe('true')
  })

  // Which build this is, the update checks, and help never wait for the settings; the update switch does.
  it('shows the build, the update checks, and help before the settings are in', () => {
    const backend = new FakeBackend()
    backend.settings = () => new Promise(() => {})
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
    fireEvent.click(control('tab-application', 'tab'))

    expect((control('app-updates-at-start', 'switch') as HTMLInputElement).disabled).toBe(true)
    expect((control('app-updates-check', 'button') as HTMLButtonElement).disabled).toBe(false)
    expect(control('app-copy-details', 'button')).toBeTruthy()
    expect(absent('app-tint-black')).toBe(true)
  })

  it('defaults startup checks on and saves the preference independently of manual checks', async () => {
    const backend = await application()
    const toggle = control('app-updates-at-start', 'switch') as HTMLInputElement
    expect(toggle.checked).toBe(true)
    fireEvent.click(toggle)
    await waitFor(() => expect(backend.saved).toContainEqual({ 'updates.check.on.start': 'false' }))
    expect(toggle.checked).toBe(false)
    fireEvent.click(control('app-updates-check', 'button'))
    await waitFor(() => expect(backend.updateChecks).toBe(1))
  })

  it('holds manual checks until the dialog closes and allows another check afterward', async () => {
    const backend = new FakeBackend()
    let finish!: (ok: boolean) => void
    const check = vi.spyOn(backend, 'checkUpdates').mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    await application(backend)
    const button = control('app-updates-check', 'button') as HTMLButtonElement
    fireEvent.click(button)
    expect(button.disabled).toBe(true)
    fireEvent.click(button)
    expect(check).toHaveBeenCalledTimes(1)
    finish(true)
    await waitFor(() => expect(button.disabled).toBe(false))
    check.mockResolvedValue(true)
    fireEvent.click(button)
    await waitFor(() => expect(check).toHaveBeenCalledTimes(2))
  })

  it('links the credits and the support pages', async () => {
    const backend = await application()
    const places = [
      'author',
      'diplay',
      'xcertplay',
      'livi',
      'rgi',
      'notices',
      'issues',
      'discussions',
      'pull-requests',
      'source',
      'releases',
    ] as const

    for (const place of places) fireEvent.click(control(`app-open-${place}`, 'button'))

    expect(backend.opened).toEqual(places)
  })

  it('copies the details a report needs', async () => {
    const writeText = vi.fn(async () => {})
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await application()

    fireEvent.click(control('app-copy-details', 'button'))

    expect(writeText).toHaveBeenCalledWith(appState().about.details)
    vi.unstubAllGlobals()
  })

  it('opens Get started again', async () => {
    await application()

    fireEvent.click(control('app-show-guide', 'button'))

    expect(control('setup-skip', 'button')).toBeTruthy()
  })
})
