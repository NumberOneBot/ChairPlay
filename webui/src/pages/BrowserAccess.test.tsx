import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { appState } from '../testing'
import { BrowserAccess } from './BrowserAccess'

afterEach(cleanup)

describe('Browser access', () => {
  it('says when the port in the settings was taken', () => {
    render(<BrowserAccess state={appState({ uiServer: { port: 51234, configuredPort: 7529 } })} />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('says nothing while the port is the one asked for', () => {
    render(<BrowserAccess state={appState({ uiServer: { port: 7529, configuredPort: 7529 } })} />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  // CHAIRPLAY_UI_PORT=0 asks for any free port, so whichever it got is the one asked for.
  it('says nothing when any free port was asked for', () => {
    render(<BrowserAccess state={appState({ uiServer: { port: 51234, configuredPort: 0 } })} />)
    expect(screen.queryByRole('alert')).toBeNull()
  })
})
