import { cleanup, fireEvent, render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { Shell } from '../shell/Shell'
import { absent, appState, control } from '../testing'

afterEach(cleanup)

/** The shell on the About page, once the settings are in. */
async function about(backend = new FakeBackend()) {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
  fireEvent.click(control('tab-about', 'tab'))
  await waitFor(() => expect((control('app-updates-at-start', 'switch') as HTMLInputElement).disabled).toBe(false))
  return backend
}

describe('About page, a newer release', () => {
  // The release the last check found stays offered on About after its dialog is gone, while it is newer than this
  // build; Download opens the latest release's page (owner, 2026-11-12).
  it('offers the release the last check found, and opens the latest release', async () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true, updateAvailable: '1.1.1' })} backend={backend} />)
    fireEvent.click(control('tab-about', 'tab'))

    fireEvent.click(control('app-update-download', 'button'))

    expect(backend.opened).toEqual(['latest-release'])
  })

  it('offers nothing while no newer release was found', () => {
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={new FakeBackend()} />)
    fireEvent.click(control('tab-about', 'tab'))

    expect(absent('app-update-download')).toBe(true)
  })
})

describe('About page', () => {
  // Which build this is, the update checks, and help never wait for the settings; the update switch does.
  it('shows the build, the update checks, and help before the settings are in', () => {
    const backend = new FakeBackend()
    backend.settings = () => new Promise(() => {})
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
    fireEvent.click(control('tab-about', 'tab'))

    expect((control('app-updates-at-start', 'switch') as HTMLInputElement).disabled).toBe(true)
    expect((control('app-updates-check', 'button') as HTMLButtonElement).disabled).toBe(false)
    expect(control('app-copy-details', 'button')).toBeTruthy()
    expect(absent('app-tint-black')).toBe(true)
  })

  it('defaults startup checks on and saves the preference independently of manual checks', async () => {
    const backend = await about()
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
    await about(backend)
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
    const backend = await about()
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
    await about()

    fireEvent.click(control('app-copy-details', 'button'))

    expect(writeText).toHaveBeenCalledWith(appState().about.details)
    vi.unstubAllGlobals()
  })

  it('opens Get started again', async () => {
    await about()

    fireEvent.click(control('app-show-guide', 'button'))

    expect(control('setup-skip', 'button')).toBeTruthy()
  })
})
