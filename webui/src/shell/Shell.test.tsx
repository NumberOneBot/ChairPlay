import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'
import { sectionOf } from './sections'
import { Shell } from './Shell'

afterEach(cleanup)

const button = (name: string) => screen.getByRole('button', { name }) as HTMLButtonElement
const live = { phase: 'active', canShowCarPlay: true } as const

describe('Waiting screen', () => {
  // Connecting on its own, the app shows black, not the settings.
  it('shows the waiting screen instead of the settings while the app connects on its own', () => {
    render(<Shell state={appState({ phase: 'calling', waiting: true })} backend={new FakeBackend()} />)

    expect(screen.queryByRole('tablist')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Connect' })).toBeNull()
  })

  it('opens the settings when asked', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'starting', waiting: true })} backend={backend} />)

    fireEvent.click(button('Settings'))

    expect(backend.shown).toEqual(['settings'])
  })
})

describe('Shell toolbar', () => {
  it('before the Wi-Fi is set, Connect and the windows are off', () => {
    render(<Shell state={appState({ phase: 'setup' })} backend={new FakeBackend()} />)

    expect(button('Connect').disabled).toBe(true)
    for (const name of ['CarPlay', 'AltScreen', 'Controls']) {
      expect(button(name).disabled).toBe(true)
    }
    expect(screen.queryByRole('button', { name: 'Reconnect' })).toBeNull()
  })

  it('connects from idle', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'idle' })} backend={backend} />)

    fireEvent.click(button('Connect'))

    expect(backend.connects).toBe(1)
    expect(screen.queryByRole('button', { name: 'Disconnect' })).toBeNull()
  })

  // Reconnect and Disconnect take Connect's place from the bring-up's start, so a stuck one can be ended.
  it('reconnects or disconnects while connecting and connected', () => {
    for (const phase of ['starting', 'calling', 'joining', 'retrying', 'active'] as const) {
      const backend = new FakeBackend()
      render(<Shell state={appState({ phase })} backend={backend} />)

      expect(screen.queryByRole('button', { name: 'Connect' })).toBeNull()
      fireEvent.click(button('Reconnect'))
      fireEvent.click(button('Disconnect'))

      expect([backend.reconnects, backend.disconnects]).toEqual([1, 1])
      cleanup()
    }
  })

  it('shows CarPlay with a session, and not without one', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState(live)} backend={backend} />)

    fireEvent.click(button('CarPlay'))
    expect(backend.shown).toEqual(['carplay'])
    cleanup()

    render(<Shell state={appState({ phase: 'calling' })} backend={backend} />)
    expect(button('CarPlay').disabled).toBe(true)
  })

  // A tab never shows CarPlay itself: the app shows it in ChairPlay's window.
  it('shows CarPlay in the window from a browser tab', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ ...live, embedded: false })} backend={backend} />)

    expect(button('CarPlay').disabled).toBe(false)
    fireEvent.click(button('CarPlay'))

    expect(backend.shown).toEqual(['carplay'])
  })

  it('shows and hides the windows, pressed while shown', () => {
    const backend = new FakeBackend()
    const windows = { altscreen: { available: true, visible: true }, controls: { available: true, visible: false } }
    render(<Shell state={appState({ ...live, windows })} backend={backend} />)

    expect(button('AltScreen').getAttribute('aria-pressed')).toBe('true')
    expect(button('Controls').getAttribute('aria-pressed')).toBe('false')
    fireEvent.click(button('AltScreen'))
    fireEvent.click(button('Controls'))

    expect(backend.windows).toEqual([
      { name: 'altscreen', visible: false },
      { name: 'controls', visible: true },
    ])
  })

  it('keeps the AltScreen button off while the AltScreen is turned off', () => {
    const windows = { altscreen: { available: false, visible: false }, controls: { available: true, visible: true } }
    render(<Shell state={appState({ ...live, windows })} backend={new FakeBackend()} />)

    expect(button('AltScreen').disabled).toBe(true)
  })

  it('says in the toolbar when changes wait for a restart', () => {
    const state = appState({ ...live, restartPending: ['altscreen.enabled', 'display.width', 'display.height'] })
    render(<Shell state={state} backend={new FakeBackend()} />)

    expect(screen.getByRole('img', { name: 'Restart needed' })).toBeTruthy()
    // The same sign as a setting's that waits for a restart, not the Reconnect button's.
    expect(within(screen.getByRole('banner')).getByTestId('restart-mark')).toBeTruthy()
  })

  it('puts the settings shown on Experiments on Experiments, whatever their key starts with', () => {
    expect(sectionOf('altscreen.card.area')).toBe('altscreen')
    expect(sectionOf('log.level')).toBe('experiments')
  })

  // Typing into the iPhone is set beside the key bindings, so the bar's list of what waits names that page.
  it('puts the keyboard text input on Keyboard', () => {
    expect(sectionOf('keyboard.text.input')).toBe('keyboard')
  })

  it('has no Restart needed while nothing waits for one', () => {
    render(<Shell state={appState(live)} backend={new FakeBackend()} />)

    expect(screen.queryByRole('img', { name: 'Restart needed' })).toBeNull()
    expect(screen.queryByRole('img', { name: 'Reconnect needed' })).toBeNull()
  })

  // A change for the next connection is marked in the bar too, as at its field: ⟳, not the restart's sign.
  it('says in the toolbar when changes wait for the next connection', () => {
    render(<Shell state={appState({ ...live, reconnectPending: ['display.physical.width.mm'] })} backend={new FakeBackend()} />)

    const bar = within(screen.getByRole('banner'))
    expect(bar.getByRole('img', { name: 'Reconnect needed' })).toBeTruthy()
    expect(bar.getByTestId('reconnect-mark')).toBeTruthy()
    expect(bar.queryByTestId('restart-mark')).toBeNull()
  })
})

describe('Shell', () => {
  // The connection reads the CarPlay and AltScreen pages as it is made: changed meanwhile, who knows which value it
  // took. While it connects they wait, off, with no message; the pages that apply at once, and Connection, stay live.
  it('holds the pages the connection reads while it connects', async () => {
    for (const phase of ['starting', 'calling', 'joining', 'retrying'] as const) {
      render(<Shell state={appState({ phase, canShowCarPlay: true })} backend={new FakeBackend()} />)
      await screen.findByRole('textbox', { name: 'Network' })
      expect(document.querySelector('[inert]')).toBeNull()

      for (const name of ['CarPlay', 'AltScreen']) {
        fireEvent.click(screen.getByRole('tab', { name }))
        const held = screen.getByRole('tabpanel').querySelector('[inert]')
        expect(held, `${phase} ${name}`).not.toBeNull()
        expect(
          held!.contains(screen.getByRole('radiogroup', { name: name === 'CarPlay' ? 'Frame rate' : 'Area' })),
        ).toBe(true)
        expect(screen.queryByRole('alert')).toBeNull()
      }
      fireEvent.click(screen.getByRole('tab', { name: 'Audio' }))
      expect(document.querySelector('[inert]')).toBeNull()
      cleanup()
    }
  })

  it('lets the pages the connection reads change once it is made, or when there is none', async () => {
    for (const phase of ['active', 'idle'] as const) {
      render(<Shell state={appState({ phase, canShowCarPlay: phase === 'active' })} backend={new FakeBackend()} />)
      await screen.findByRole('textbox', { name: 'Network' })
      fireEvent.click(screen.getByRole('tab', { name: 'CarPlay' }))

      expect(document.querySelector('[inert]')).toBeNull()
      cleanup()
    }
  })

  it('opens on Connection and switches pages from the section list', () => {
    render(<Shell state={appState(live)} backend={new FakeBackend()} />)

    expect(screen.getByRole('tab', { name: 'Connection' }).getAttribute('aria-selected')).toBe('true')

    fireEvent.click(screen.getByRole('tab', { name: 'Audio' }))

    expect(screen.getByRole('tab', { name: 'Audio' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByRole('tab', { name: 'Connection' }).getAttribute('aria-selected')).toBe('false')
  })

  it('shows CarPlay on Escape when it can', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState(live)} backend={backend} />)

    fireEvent.keyDown(window, { key: 'Escape' })

    expect(backend.shown).toEqual(['carplay'])
  })

  // The settings have the keyboard while they show: the app's own keys (F11, F12 and such) still reach it, as pressed
  // on the window, and the app keeps the arrows, Enter and the like to the page. What is typed in a field stays there.
  it("hands the app's keys on, not what is typed in a field", async () => {
    const backend = new FakeBackend()
    render(<Shell state={appState(live)} backend={backend} />)

    fireEvent.keyDown(window, { code: 'F11', key: 'F11' })
    fireEvent.keyDown(window, { code: 'F11', key: 'F11', repeat: true })
    fireEvent.keyUp(window, { code: 'F11', key: 'F11' })
    fireEvent.keyDown(window, { code: 'Equal', key: '=', shiftKey: true })
    fireEvent.keyDown(window, { code: 'KeyF', key: 'f' })
    fireEvent.keyDown(window, { code: 'ShiftLeft', key: 'Shift', shiftKey: true })
    const field = await screen.findByRole('textbox', { name: 'Network' })
    fireEvent.keyDown(field, { code: 'Minus', key: '-' })
    fireEvent.keyDown(field, { code: 'KeyF', key: 'f' })
    fireEvent.keyDown(field, { code: 'F12', key: 'F12' })

    expect(backend.keys).toEqual([
      'F11 press settings',
      'F11 repeat settings',
      'F11 release settings',
      'Shift+Equal press settings',
      'F press settings',
      'F12 press settings',
    ])
  })

  it('leaves Escape alone without a session, or when a page used it', () => {
    const backend = new FakeBackend()
    render(<Shell state={appState({ phase: 'calling' })} backend={backend} />)
    fireEvent.keyDown(window, { key: 'Escape' })
    cleanup()

    render(<Shell state={appState(live)} backend={backend} />)
    const used = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })
    used.preventDefault()
    window.dispatchEvent(used)

    expect(backend.shown).toEqual([])
  })
})
