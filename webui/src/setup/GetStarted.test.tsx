import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

/** A first start: nothing set up, the app idle; [setup] and [rest] change what the PC already has. */
function firstStart(setup: Partial<AppState['setup']> = {}, rest: Partial<AppState> = {}): AppState {
  const base = appState({
    phase: 'idle',
    setupDone: false,
    wifi: { ssid: '', security: 'WPA_WPA2', passphraseSet: false },
  })
  return {
    ...base,
    ...rest,
    setup: { ...base.setup, mfiPresent: false, firewall: 'missing', pairedIphone: null, pcWifi: null, ...setup },
  }
}

const everything = {
  mfiPresent: true,
  firewall: 'present',
  pairedIphone: "Kate's iPhone",
  pcWifi: 'ChairPlay-5G',
} as const
const wifiSet = { wifi: { ssid: 'ChairPlay-5G', security: 'WPA_WPA2', passphraseSet: true } }

async function guide(state: AppState, backend = new FakeBackend()) {
  backend.data = { ...backend.data, values: { ...backend.data.values, 'wifi.ssid': state.wifi.ssid } }
  render(<Shell state={state} backend={backend} />)
  await screen.findByRole('heading', { name: 'Get started' })
  return backend
}

/** A step by its title, and whether it is done, open now, or still to do. */
const step = (title: string) =>
  screen.getByText(title, { selector: '.st > span:first-child' }).closest('.step') as HTMLElement
const stateOf = (title: string) => ['done', 'now', 'todo'].find((name) => step(title).classList.contains(name))

describe('Get started', () => {
  it('shows instead of the settings until the setup is done, and Skip to settings leaves it', async () => {
    await guide(firstStart())
    expect(screen.queryByRole('tab', { name: 'Connection' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Skip to settings' }))

    expect(screen.getByRole('tab', { name: 'Connection' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Get started' })).toBeNull()
  })

  it('opens the first step not done, and the rest wait', async () => {
    await guide(firstStart({ mfiPresent: true }))

    expect(screen.queryByText('MFi files', { selector: '.st > span:first-child' })).toBeNull()
    expect(stateOf('Firewall')).toBe('now')
    expect(stateOf('Pair the iPhone')).toBe('todo')
    expect(stateOf('Wi-Fi network')).toBe('todo')
  })

  // With the firewall off there is nothing to let ChairPlay through: the guide is a step shorter.
  it('leaves the firewall step out while the firewall is off', async () => {
    await guide(firstStart({ mfiPresent: true, firewall: 'off' }))

    expect(screen.queryByText('Firewall', { selector: '.st > span:first-child' })).toBeNull()
    expect(stateOf('Pair the iPhone')).toBe('now')
  })

  it('opens the MFi folder', async () => {
    const backend = await guide(firstStart())

    fireEvent.click(within(step('MFi files')).getByRole('button', { name: 'Open' }))

    expect(backend.opened).toEqual(['mfi-folder'])
  })

  it('reacts to MFi files appearing or disappearing without reopening the guide', async () => {
    const backend = new FakeBackend()
    const ready = firstStart({ ...everything }, wifiSet)
    const { rerender } = render(<Shell state={ready} backend={backend} />)
    await screen.findByRole('heading', { name: 'Get started' })
    const connect = () => screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement
    expect(screen.queryByText('MFi files', { selector: '.st > span:first-child' })).toBeNull()
    expect(connect().disabled).toBe(false)

    rerender(<Shell state={{ ...ready, setup: { ...ready.setup, mfiPresent: false } }} backend={backend} />)
    expect(stateOf('MFi files')).toBe('now')
    expect(connect().disabled).toBe(true)

    rerender(<Shell state={ready} backend={backend} />)
    expect(screen.queryByText('MFi files', { selector: '.st > span:first-child' })).toBeNull()
    expect(connect().disabled).toBe(false)
  })

  it('adds the firewall rule, or opens the rules when one blocks ChairPlay', async () => {
    const backend = await guide(firstStart({ mfiPresent: true }))
    fireEvent.click(within(step('Firewall')).getByRole('button', { name: 'Add the rule' }))
    await waitFor(() => expect(backend.firewallRules).toBe(1))
    cleanup()

    const blocked = await guide(firstStart({ mfiPresent: true, firewall: 'blocked' }))
    fireEvent.click(within(step('Firewall')).getByRole('button', { name: "Open the firewall's rules" }))
    expect(blocked.opened).toEqual(['firewall-rules'])
  })

  it('waits for a paired iPhone, and is done once Windows lists one', async () => {
    const backend = await guide(firstStart({ mfiPresent: true, firewall: 'present' }))
    expect(stateOf('Pair the iPhone')).toBe('now')
    fireEvent.click(within(step('Pair the iPhone')).getByRole('button', { name: 'Open Bluetooth settings' }))
    expect(backend.opened).toEqual(['bluetooth'])
    cleanup()

    await guide(firstStart({ ...everything }))
    expect(stateOf('Pair the iPhone')).toBe('done')
  })
})

describe('Get started, the Wi-Fi step', () => {
  // The network starts as the one this PC is on; saving the password saves it with the network.
  it('fills in the network this PC is on and saves it with the password', async () => {
    const backend = await guide(firstStart({ ...everything }))
    expect(stateOf('Wi-Fi network')).toBe('now')
    const network = within(step('Wi-Fi network')).getByRole('textbox', { name: 'Network' }) as HTMLInputElement
    expect(network.value).toBe('ChairPlay-5G')

    const password = within(step('Wi-Fi network')).getByLabelText('Password')
    fireEvent.change(password, { target: { value: 'secret123' } })
    fireEvent.keyDown(password, { key: 'Enter' })

    await waitFor(() =>
      expect(backend.saved).toEqual([{ 'wifi.ssid': 'ChairPlay-5G', 'wifi.passphrase': 'secret123' }]),
    )
  })

  it('keeps the step open while the network is not the one this PC is on', async () => {
    await guide(firstStart({ ...everything }, { wifi: { ssid: 'Office', security: 'WPA_WPA2', passphraseSet: true } }))

    expect(stateOf('Wi-Fi network')).toBe('now')
  })

  // The join timed out: a Wi-Fi problem, most likely the password, though the error names Bluetooth.
  it('opens again when the iPhone did not join, and Connect waits for the password', async () => {
    const error = { message: 'Bluetooth control ended TIMEOUT before the iPhone joined over Wi-Fi', at: '14:02:11' }
    await guide(firstStart({ ...everything }, { ...wifiSet, error }))

    expect(stateOf('Wi-Fi network')).toBe('now')
    expect(stateOf('Pair the iPhone')).toBe('done')
    const connect = screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement
    expect(connect.disabled).toBe(true)
  })
})

describe('Get started, all done', () => {
  it('turns Connect on once every step is done', async () => {
    const backend = await guide(firstStart({ ...everything }, wifiSet))

    const connect = screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement
    expect(connect.disabled).toBe(false)
    fireEvent.click(connect)
    expect(backend.connects).toBe(1)
  })

  it('keeps Connect off while a step is open', async () => {
    await guide(firstStart({ mfiPresent: true, firewall: 'present', pcWifi: 'ChairPlay-5G' }, wifiSet))

    const connect = screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement
    expect(connect.disabled).toBe(true)
  })
})

describe('Get started after Forget', () => {
  // Skipped once, it still comes back when Forget this iPhone undoes the setup.
  it('opens again when the setup is undone, even after Skip to settings', async () => {
    const backend = new FakeBackend()
    const state = firstStart({ ...everything }, wifiSet)
    const { rerender } = render(<Shell state={state} backend={backend} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Skip to settings' }))

    rerender(<Shell state={{ ...state, setupDone: true }} backend={backend} />)
    rerender(<Shell state={{ ...state, setupDone: false }} backend={backend} />)

    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeTruthy()
  })
})

describe('Add the rule', () => {
  // Windows' consent prompt can take a while: a second click must not ask again meanwhile.
  it('waits for the consent before it can be pressed again', async () => {
    const backend = new FakeBackend()
    let answer: (added: boolean) => void = () => {}
    backend.addFirewallRule = () => {
      backend.firewallRules++
      return new Promise<boolean>((resolve) => (answer = resolve))
    }
    await guide(firstStart({ mfiPresent: true }), backend)
    const add = () => within(step('Firewall')).getByRole('button', { name: 'Add the rule' }) as HTMLButtonElement

    fireEvent.click(add())
    fireEvent.click(add())

    expect(backend.firewallRules).toBe(1)
    expect(add().disabled).toBe(true)
    answer(true)
    await waitFor(() => expect(add().disabled).toBe(false))
  })
})

describe('Show again', () => {
  it('opens Get started from the Connection page once the setup is done', async () => {
    render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={new FakeBackend()} />)
    await screen.findByRole('textbox', { name: 'Network' })

    fireEvent.click(screen.getByRole('button', { name: 'Show again' }))

    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Skip to settings' }))
    expect(screen.getByRole('tab', { name: 'Connection' })).toBeTruthy()
  })
})
