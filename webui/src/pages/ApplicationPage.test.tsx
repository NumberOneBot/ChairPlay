import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
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
  it('has Application and About as tabs, and no Experiments', async () => {
    await application()

    expect(control('tab-about', 'tab')).toBeTruthy()
    expect(absent('tab-experiments')).toBe(true)
    expect(control('tab-application', 'tab').getAttribute('aria-selected')).toBe('true')
  })

  it('shows slate until a tint is chosen, and saves the one chosen', async () => {
    const backend = await application()
    expect(control('app-tint-slate', 'radio').getAttribute('aria-checked')).toBe('true')

    fireEvent.click(control('app-tint-black', 'radio'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'ui.tint': 'black' }]))
  })

  it('shows the saved tint', async () => {
    const backend = new FakeBackend()
    backend.data.values['ui.tint'] = 'teal'
    await application(backend)

    expect(control('app-tint-teal', 'radio').getAttribute('aria-checked')).toBe('true')
  })

  it('offers no warm tint', async () => {
    await application()

    expect(absent('app-tint-warm')).toBe(true)
  })
})
