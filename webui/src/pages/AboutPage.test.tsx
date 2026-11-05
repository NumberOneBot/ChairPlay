import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

async function about() {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={new FakeBackend()} />)
  await screen.findByRole('textbox', { name: 'Network' })
  fireEvent.click(screen.getByRole('tab', { name: 'About' }))
}

describe('About page', () => {
  it('defaults startup checks on and saves the preference independently of manual checks', async () => {
    const backend = new FakeBackend()
    render(<Shell state={appState()} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })
    fireEvent.click(screen.getByRole('tab', { name: 'About' }))
    const toggle = screen.getByRole('switch', { name: 'Check for updates at startup' }) as HTMLInputElement
    expect(toggle.checked).toBe(true)
    fireEvent.click(toggle)
    await waitFor(() => expect(backend.saved).toContainEqual({ 'updates.check.on.start': 'false' }))
    expect(toggle.checked).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Check now' }))
    await waitFor(() => expect(backend.updateChecks).toBe(1))
  })

  it('holds manual checks until the dialog closes and allows another check afterward', async () => {
    const backend = new FakeBackend()
    let finish!: (ok: boolean) => void
    const check = vi.spyOn(backend, 'checkUpdates').mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    render(<Shell state={appState()} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })
    fireEvent.click(screen.getByRole('tab', { name: 'About' }))
    const button = screen.getByRole('button', { name: 'Check now' }) as HTMLButtonElement
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
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })
    fireEvent.click(screen.getByRole('tab', { name: 'About' }))

    for (const name of [
      'Oleksiy Strelets',
      'DiPlay',
      'xcertplay',
      'LIVI',
      'mib2q-carplay-rgi',
      'Third-party notices',
      'New issue on GitHub',
      'Discussions',
      'Pull requests',
      'github.com/NumberOneBot/ChairPlay',
      'Releases',
    ]) {
      fireEvent.click(screen.getByRole('button', { name }))
    }

    expect(backend.opened).toEqual([
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
    ])
  })

  it('copies the details a report needs', async () => {
    const writeText = vi.fn(async () => {})
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await about()

    fireEvent.click(screen.getByRole('button', { name: 'Copy details' }))

    expect(writeText).toHaveBeenCalledWith(appState().about.details)
    vi.unstubAllGlobals()
  })

  it('opens Get started again', async () => {
    await about()

    fireEvent.click(screen.getByRole('button', { name: 'Show again' }))

    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeTruthy()
  })
})
