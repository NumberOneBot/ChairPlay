import { cleanup, fireEvent, render } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { FakeBackend } from '../fakeBackend'
import { absent, appState, control } from '../testing'
import { Shell } from './Shell'

afterEach(cleanup)

describe('Support card', () => {
  it('shows on every settings page while due, and not otherwise', () => {
    const { rerender } = render(<Shell state={appState({ supportDue: true })} backend={new FakeBackend()} />)
    control('support-card', 'status')
    fireEvent.click(control('tab-application', 'tab'))
    control('support-card', 'status')

    rerender(<Shell state={appState({ supportDue: false })} backend={new FakeBackend()} />)
    expect(absent('support-card')).toBe(true)
  })

  it('Buy me a coffee opens the page and stores the support', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ supportDue: true })} backend={backend} />)
    fireEvent.click(control('support-coffee', 'button'))
    expect(backend.opened).toEqual(['buy-me-a-coffee'])
    expect(backend.openedFrom).toEqual(['support-card'])
    expect(backend.supports).toEqual(['done'])
  })

  it('I already did stores the support, and Later asks again later; neither opens a page', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ supportDue: true })} backend={backend} />)
    fireEvent.click(control('support-done', 'button'))
    fireEvent.click(control('support-later', 'button'))
    expect(backend.supports).toEqual(['done', 'later'])
    expect(backend.opened).toEqual([])
  })
})
