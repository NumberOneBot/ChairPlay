import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AppState } from '../api'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

const live = { phase: 'active', canShowCarPlay: true } as const

/** The shell, which opens on Connection, once the settings are in. */
async function connection(state: AppState = appState(live), backend = new FakeBackend()) {
  render(<Shell state={state} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  return backend
}

const textbox = (name: string) => screen.getByRole('textbox', { name }) as HTMLInputElement
const password = () => screen.getByLabelText('Password') as HTMLInputElement

describe('Connection page, Wi-Fi', () => {
  it('shows the network and its security', async () => {
    await connection()

    expect(textbox('Network').value).toBe('Home')
    expect((screen.getByRole('combobox', { name: 'Security' }) as HTMLSelectElement).value).toBe('WPA_WPA2')
  })

  it('saves the network on Enter', async () => {
    const backend = await connection()

    fireEvent.change(textbox('Network'), { target: { value: 'Car' } })
    fireEvent.keyDown(textbox('Network'), { key: 'Enter' })

    await waitFor(() => expect(backend.saved).toEqual([{ 'wifi.ssid': 'Car' }]))
  })

  it('saves the security as soon as it is chosen', async () => {
    const backend = await connection()

    fireEvent.change(screen.getByRole('combobox', { name: 'Security' }), { target: { value: 'WPA3_ONLY' } })

    await waitFor(() => expect(backend.saved).toEqual([{ 'wifi.security': 'WPA3_ONLY' }]))
  })

  // The saved password never reaches the page: the field stays empty, and saves what is typed.
  it('saves a typed password and empties the field', async () => {
    const backend = await connection()
    expect(password().value).toBe('')

    fireEvent.change(password(), { target: { value: 'secret123' } })
    fireEvent.keyDown(password(), { key: 'Enter' })

    await waitFor(() => expect(backend.saved).toEqual([{ 'wifi.passphrase': 'secret123' }]))
    await waitFor(() => expect(password().value).toBe(''))
  })

  it('shows the saved password with the eye, asking the app for it', async () => {
    await connection()

    fireEvent.click(screen.getByRole('button', { name: 'Show the password' }))

    await waitFor(() => expect(password().value).toBe('secret123'))
    expect(password().type).toBe('text')
  })

  it('keeps a refused password, marked', async () => {
    const backend = new FakeBackend()
    backend.nextOutcome = { ok: false, errors: { 'wifi.passphrase': 'A WPA password has 8 to 63 characters' } }
    await connection(appState(live), backend)

    fireEvent.change(password(), { target: { value: 'short' } })
    fireEvent.keyDown(password(), { key: 'Enter' })

    await waitFor(() => expect(document.querySelector('.errtext')).not.toBeNull())
    expect(password().value).toBe('short')
  })
})

describe('Connection page, iPhone', () => {
  it('saves the switches at once', async () => {
    const backend = await connection()

    fireEvent.click(screen.getByRole('switch', { name: 'Connect on startup' }))
    fireEvent.click(screen.getByRole('switch', { name: 'Show CarPlay on connect' }))

    await waitFor(() => expect(backend.saved).toEqual([{ 'ui.autoconnect': 'false' }, { 'ui.autoshow': 'false' }]))
  })

  // Only a changed setting that waits is marked: the untouched ones carry nothing.
  it('marks a changed setting that waits for a restart or the next connection, and nothing else', async () => {
    const rowOf = (name: string) => screen.getByRole('switch', { name }).closest('.row') as HTMLElement
    await connection()
    expect(screen.queryAllByTitle(/^Changed;/)).toEqual([])
    cleanup()

    await connection(appState({ ...live, restartPending: ['ui.autoconnect'], reconnectPending: ['wifi.ssid'] }))

    expect(within(rowOf('Connect on startup')).getByTitle('Changed; applies after restart')).toBeTruthy()
    const network = textbox('Network').closest('.row') as HTMLElement
    expect(within(network).getByTitle('Changed; applies at the next connection')).toBeTruthy()
    expect(screen.queryAllByTitle(/^Changed;/)).toHaveLength(2)
  })

  // A restart is not a reconnection: its mark is the app's restart sign, never the one Reconnect is mistaken for.
  it('marks a setting waiting for a restart apart from one waiting for the next connection', async () => {
    const rowOf = (name: string) => screen.getByRole('switch', { name }).closest('.row') as HTMLElement
    await connection(appState({ ...live, restartPending: ['ui.autoconnect'], reconnectPending: ['wifi.ssid'] }))
    const network = textbox('Network').closest('.row') as HTMLElement

    expect(within(rowOf('Connect on startup')).getByTestId('restart-mark')).toBeTruthy()
    expect(within(rowOf('Connect on startup')).queryByTestId('reconnect-mark')).toBeNull()
    expect(within(network).getByTestId('reconnect-mark')).toBeTruthy()
    expect(within(network).queryByTestId('restart-mark')).toBeNull()
  })

  it('saves the name, the address, and the time to join', async () => {
    const backend = await connection()

    for (const [name, value] of [
      ['Name on iPhone', 'Car'],
      ['Bluetooth address', '01:23:45:67:89:AB'],
      ['Time to join Wi-Fi', '45'],
    ]) {
      fireEvent.change(textbox(name), { target: { value } })
      fireEvent.blur(textbox(name))
    }

    await waitFor(() =>
      expect(backend.saved).toEqual([
        { 'device.name': 'Car' },
        { 'iphone.address': '01:23:45:67:89:AB' },
        { 'iphone.join.seconds': '45' },
      ]),
    )
  })
})

describe('Connection page, the paired iPhone', () => {
  it('names the paired iPhone and forgets it', async () => {
    const backend = await connection()

    const row = screen.getByText('Paired iPhone', { selector: 'label' }).closest('.row') as HTMLElement
    expect(row.textContent).toContain("Kate's iPhone")
    fireEvent.click(within(row).getByRole('button', { name: 'Forget' }))

    await waitFor(() => expect(backend.forgets).toBe(1))
  })

  // Windows lists no paired iPhone: the message opens Bluetooth settings to pair one.
  it('opens Bluetooth settings from the message when none is paired', async () => {
    const backend = await connection(appState({ ...live, setup: { ...appState().setup, pairedIphone: null } }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Open Bluetooth settings' }))

    expect(backend.opened).toEqual(['bluetooth'])
  })

  it('offers Get started from the message', async () => {
    await connection(appState({ ...live, setup: { ...appState().setup, pairedIphone: null } }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Get started' }))

    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeTruthy()
  })
})

describe('Connection page, messages', () => {
  const setup = (changes: Partial<AppState['setup']>) => ({ ...appState().setup, ...changes })

  it('shows none while nothing is wrong', async () => {
    await connection()

    expect(screen.queryAllByRole('alert')).toEqual([])
  })

  it('opens the MFi folder from the message when the files are missing', async () => {
    const backend = await connection(appState({ ...live, setup: setup({ mfiPresent: false }) }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Open' }))

    expect(backend.opened).toEqual(['mfi-folder'])
  })

  it('gives the firewall command to copy when no rule lets the iPhone in', async () => {
    const writeText = vi.fn(async () => {})
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await connection(appState({ ...live, setup: setup({ firewall: 'missing' }) }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Copy' }))

    expect(writeText).toHaveBeenCalledWith(appState().setup.firewallCommands)
  })

  // Windows asks for the administrator's consent; the message goes once the live checks find the rule.
  it('adds the firewall rule with the consent Windows asks for', async () => {
    const backend = await connection(appState({ ...live, setup: setup({ firewall: 'missing' }) }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Add the rule' }))

    await waitFor(() => expect(backend.firewallRules).toBe(1))
  })

  it('shows a failure when the rule was not added', async () => {
    const backend = new FakeBackend()
    backend.consent = false
    await connection(appState({ ...live, setup: setup({ firewall: 'missing' }) }), backend)

    const message = screen.getByRole('alert')
    fireEvent.click(within(message).getByRole('button', { name: 'Add the rule' }))

    await waitFor(() => expect(message.querySelector('.errtext')).not.toBeNull())
  })

  // With ui.network on, a rule added before the switch lets the iPhone in but not another device to the settings.
  it("adds the rule again when it does not open the settings' port", async () => {
    const backend = await connection(appState({ ...live, setup: setup({ firewall: 'partial' }) }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Add the rule' }))

    await waitFor(() => expect(backend.firewallRules).toBe(1))
  })

  it('says nothing of the firewall while it is off', async () => {
    await connection(appState({ ...live, setup: setup({ firewall: 'off' }) }))

    expect(screen.queryAllByRole('alert')).toEqual([])
  })

  it("opens the firewall's rules when one blocks ChairPlay", async () => {
    const backend = await connection(appState({ ...live, setup: setup({ firewall: 'blocked' }) }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: "Open the firewall's rules" }))

    expect(backend.opened).toEqual(['firewall-rules'])
  })

  it('opens Bluetooth settings from the last error for an iPhone that did not answer', async () => {
    const error = { message: 'RFCOMM connect failed: 10060', at: '14:02:11' }
    const backend = await connection(appState({ phase: 'retrying', error }))

    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Open Bluetooth settings' }))

    expect(backend.opened).toEqual(['bluetooth'])
  })

  // No paired iPhone has its own message, which says what to do; the error it causes would only repeat it.
  it('shows only the pairing message when no iPhone is paired', async () => {
    const error = { message: 'Bluetooth device 98:0D:AF:F1:12:48 is not paired with this PC', at: '14:02:11' }
    await connection(appState({ phase: 'retrying', error, setup: setup({ pairedIphone: null }) }))

    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  it('offers no Bluetooth settings for a join that timed out', async () => {
    const error = { message: 'Bluetooth control ended TIMEOUT before the iPhone joined over Wi-Fi', at: '14:02:11' }
    await connection(appState({ phase: 'retrying', error }))

    expect(within(screen.getByRole('alert')).queryByRole('button', { name: 'Open Bluetooth settings' })).toBeNull()
  })

  it('drops the last error once a session is active', async () => {
    await connection(appState({ ...live, error: { message: 'RFCOMM connect failed', at: '14:02:11' } }))

    expect(screen.queryAllByRole('alert')).toEqual([])
  })
})
