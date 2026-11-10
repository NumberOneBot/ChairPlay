import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { UsbSetupView } from '../gpsApi'
import { closedSetup, outputs, setups } from '../gpsFixtures'
import { gpsState, openSection } from './gpsTesting'

afterEach(cleanup)

/** The GPS page with [setup] open, from the app's state. */
const wizard = (setup: UsbSetupView, output = outputs.usbUnprepared) => openSection(gpsState(output, setup))
const button = (name: string) => screen.queryByRole('button', { name })
const click = (name: string) => fireEvent.click(screen.getByRole('button', { name }))

describe('USB setup', () => {
  it('takes the page of the GPS section, and is a page of its own with no switch', async () => {
    await wizard(setups.connect)
    expect(screen.queryByRole('switch', { name: 'Enable signal simulation' })).toBeNull()
    expect(screen.queryByRole('radiogroup', { name: 'GPS method' })).toBeNull()
  })

  // The header is one button with the arrow: it cancels, as Cancel does.
  it('cancels from its header and from Cancel', async () => {
    const { backend } = await wizard(setups.connect)
    fireEvent.click(screen.getAllByRole('button').find((each) => each.textContent?.includes('USB GPS setup'))!)
    click('Cancel')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 cancel', 'usb-setup 7 cancel']))
  })

  it('opens the Store for the drivers', async () => {
    const { backend } = await wizard(setups.drivers)
    click('Get Apple Devices')
    await waitFor(() => expect(backend.opened).toEqual(['apple-devices']))
  })

  it('asks the app to look again when Apple Devices is not running', async () => {
    const { backend } = await wizard(setups.appleDevicesClosed)
    expect(button('Get Apple Devices')).toBeNull()
    click('Check again')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 retry usb']))
  })

  it('waits on the phone for the first steps, with nothing to press but Cancel', async () => {
    for (const setup of [setups.connect, setups.trust, setups.developerRevealed, setups.developerLinkLost]) {
      await wizard(setup)
      expect(screen.getAllByRole('button').filter((each) => each.closest('.step'))).toHaveLength(0)
      cleanup()
    }
  })

  it('shows Developer Mode first and reveals it on the button, nothing else', async () => {
    const { backend } = await wizard(setups.developerHidden)
    click('Show Developer Mode')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 reveal']))
  })

  it('downloads, with the progress as the app reports it', async () => {
    const { backend, update } = await wizard(setups.toolsMissing)
    expect(screen.queryByRole('progressbar')).toBeNull()
    click('Download')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 tools']))

    update(gpsState(outputs.usbUnprepared, setups.downloading))
    const bar = screen.getByRole('progressbar') as HTMLProgressElement
    expect([bar.value, bar.max]).toEqual([6_300_000, 15_900_000])
    expect(button('Download')).toBeNull()
  })

  it('connects a package that is already downloaded, without downloading it again', async () => {
    const { backend } = await wizard(setups.cached)
    expect(button('Download')).toBeNull()
    click('Connect')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 tools']))
  })

  it('does not show the download progress as the progress of connecting', async () => {
    await wizard({ ...setups.connecting, download: { receivedBytes: 15_900_000, totalBytes: 15_900_000 } })
    expect(screen.queryByRole('progressbar')).toBeNull()
  })

  // The error and the wait of one operation are never shown together; Try again asks for the same step again.
  it('shows an error alone, and tries the same step again', async () => {
    const { backend } = await wizard(setups.downloadError)
    expect(screen.getByRole('alert')).toBeTruthy()
    expect(screen.queryByRole('progressbar')).toBeNull()
    click('Try again')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 retry tools']))
  })

  it('shows the error of several iPhones on the first step', async () => {
    const { backend } = await wizard(setups.several)
    click('Try again')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 retry usb']))
  })

  it('shows several iPhones as a state of the step as well as an error', async () => {
    await wizard({ ...setups.connect, steps: { ...setups.connect.steps, usb: { status: 'waiting', detail: 'several-devices', error: null } } })
    expect(screen.queryByText(/Connect a data-capable/)).toBeNull()
  })

  it('shows the operation above the setup, readable', async () => {
    await wizard(setups.readyEnable, outputs.switchingToUsb)
    const status = screen.getAllByRole('status').find((each) => each.closest('.usb-setup') === null && each.textContent)!
    expect(status.closest('[inert]')).toBeNull()
  })

  it('does not ask a stale or missing operation for anything', async () => {
    const { backend } = await wizard({ ...setups.developerHidden, operationId: null })
    expect((screen.getByRole('button', { name: 'Show Developer Mode' }) as HTMLButtonElement).disabled).toBe(true)
    click('Cancel')
    expect(backend.gpsCommands).toEqual([])
  })
})

describe('USB setup, ready', () => {
  it('enables from the final action when it was opened to enable', async () => {
    const { backend } = await wizard(setups.readyEnable)
    expect(button('Done')).toBeNull()
    click('Enable USB GPS')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 enable']))
  })

  it('only finishes when it was opened to prepare, and starts nothing', async () => {
    const { backend } = await wizard(setups.ready)
    expect(button('Enable USB GPS')).toBeNull()
    click('Done')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 done']))
    expect(backend.saved).toEqual([])
  })

  // F-12/F-14: with the simulation on, the final action starts USB GPS; Done would start nothing.
  it('enables from the final action whenever the simulation is on', async () => {
    const { backend } = await wizard(setups.ready, { ...outputs.usbUnprepared, enabledIntent: true })
    expect(button('Done')).toBeNull()
    click('Enable USB GPS')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 enable']))
  })

  it('has no final action before it is ready', async () => {
    await wizard(setups.cached)
    expect(button('Enable USB GPS')).toBeNull()
    expect(button('Done')).toBeNull()
  })

  // The Windows location is a warning, never a veto (V43).
  it('keeps Enable available with the Windows warning, and with no position to start from', async () => {
    for (const setup of [setups.readyWindowsWarning, setups.readyNoPosition]) {
      const { backend } = await wizard(setup)
      expect((screen.getByRole('button', { name: 'Enable USB GPS' }) as HTMLButtonElement).disabled).toBe(false)
      click('Enable USB GPS')
      await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 enable']))
      cleanup()
    }
    await wizard(setups.readyWindowsWarning)
    expect(screen.getByRole('alert')).toBeTruthy()
    cleanup()
    await wizard(setups.readyEnable)
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('USB setup, leaving it', () => {
  // The app closes the setup; a download answering late finds the GPS page and no wizard to open again (V17).
  it('goes back to the page on the app closing it, and a late state does not open it again', async () => {
    const { backend, update } = await wizard(setups.downloading)
    click('Cancel')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 cancel']))

    update(gpsState(outputs.usbUnprepared, { ...closedSetup, download: { receivedBytes: 9_000_000, totalBytes: 15_900_000 } }))
    expect(screen.getByRole('switch', { name: 'Enable signal simulation' })).toBeTruthy()
    expect(screen.queryByRole('progressbar')).toBeNull()
    expect(backend.gpsCommands).toEqual(['usb-setup 7 cancel'])
  })

  // V56: opening the setup from the switch leaves the saved switch off until the final action.
  it('leaves the switch off after Cancel of a setup the switch opened', async () => {
    const { backend, update } = await wizard({ ...setups.connect, intent: 'enable' })
    click('Cancel')
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup 7 cancel']))
    update(gpsState(outputs.usbUnprepared, closedSetup))
    expect((screen.getByRole('switch', { name: 'Enable signal simulation' }) as HTMLInputElement).checked).toBe(false)
    expect(backend.saved).toEqual([])
  })

  it('holds the setup too while an operation runs', async () => {
    await wizard(setups.readyEnable, outputs.switchingToUsb)
    expect(screen.getByRole('button', { name: 'Enable USB GPS' }).closest('[inert]')).not.toBeNull()
  })
})
