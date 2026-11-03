import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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
