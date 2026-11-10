import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { gpsOutput, outputs, setups } from '../gpsFixtures'
import { gpsBackend, gpsState, openSection } from './gpsTesting'

afterEach(cleanup)

const enable = () => screen.getByRole('switch', { name: 'Enable signal simulation' }) as HTMLInputElement
const method = (name: string) => screen.getByRole('radio', { name })
const inert = (element: HTMLElement) => element.closest('[inert]') !== null

describe('GPS page, the switch and the method', () => {
  it('sends the switch as a command, either way, and saves nothing', async () => {
    const { backend, update } = await openSection(gpsState(outputs.carplayOff))
    fireEvent.click(enable())
    update(gpsState(outputs.carplayOn))
    expect(enable().checked).toBe(true)
    fireEvent.click(enable())

    await waitFor(() => expect(backend.gpsCommands).toEqual(['gps-enabled true', 'gps-enabled false']))
    expect(backend.saved).toEqual([])
  })

  it('sends the chosen method with the generation the page saw, and nothing for the one chosen', async () => {
    const { backend } = await openSection(gpsState(outputs.reconnectNeeded))
    fireEvent.click(method('CarPlay'))
    fireEvent.click(method('USB'))

    await waitFor(() => expect(backend.gpsCommands).toEqual(['gps-method usb 2']))
    expect(backend.saved).toEqual([])
  })

  // While the switch runs the page is what it will be when it ends: the method chosen is the one lit.
  it('shows the method being switched to as the chosen one', async () => {
    await openSection(gpsState(outputs.switchingToUsb))
    expect(method('USB').getAttribute('aria-checked')).toBe('true')
    expect(method('CarPlay').getAttribute('aria-checked')).toBe('false')
  })

  it('has no rate or satellites to choose', async () => {
    await openSection(gpsState(outputs.carplayOff))
    expect(screen.queryByRole('radiogroup', { name: /updates|rate/i })).toBeNull()
    expect(screen.queryByRole('switch', { name: /satellites/i })).toBeNull()
  })

  // The saved switch is kept while the phone is away: checked, disabled, until it is back (P-06, UI-24, UI-25a).
  it('holds the switch checked while the cable is missing, and frees it when the phone is back', async () => {
    const { update } = await openSection(gpsState(outputs.usbStartupMissing))
    expect(enable().checked).toBe(true)
    expect(enable().disabled).toBe(true)
    // The method stays choosable.
    expect((method('CarPlay') as HTMLButtonElement).disabled).toBe(false)

    update(gpsState(outputs.usbOn))
    expect(enable().checked).toBe(true)
    expect(enable().disabled).toBe(false)
  })

  it('leaves the switch to turn off while only a position is missing', async () => {
    const { backend } = await openSection(gpsState(outputs.usbNoPosition))
    expect(enable().disabled).toBe(false)
    fireEvent.click(enable())
    await waitFor(() => expect(backend.gpsCommands).toEqual(['gps-enabled false']))
  })

  it('shows the setup when the app opens it for the switch', async () => {
    const backend = gpsBackend()
    backend.gpsOutcome = 'setup-opened'
    const { update } = await openSection(gpsState(outputs.usbUnprepared), backend)
    fireEvent.click(enable())
    await waitFor(() => expect(backend.gpsCommands).toEqual(['gps-enabled true']))
    expect(screen.queryByRole('button', { name: 'Cancel' })).toBeNull()

    update(gpsState(outputs.usbUnprepared, { ...setups.connect, intent: 'enable' }))
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy()
    expect(screen.queryByRole('switch', { name: 'Enable signal simulation' })).toBeNull()
  })
})

describe('GPS page, what each method shows', () => {
  it('shows the CarPlay options for CarPlay only, and the USB parts for USB only', async () => {
    const { update } = await openSection(gpsState(outputs.carplayOn))
    expect(screen.getByRole('switch', { name: 'Send the vehicle speed' })).toBeTruthy()
    expect(screen.getByRole('textbox', { name: 'Heading' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'USB setup' })).toBeNull()
    expect(screen.queryByRole('radiogroup', { name: 'City presets' })).toBeNull()

    update(gpsState(outputs.usbOn))
    expect(screen.queryByRole('switch', { name: 'Send the vehicle speed' })).toBeNull()
    expect(screen.queryByRole('switch', { name: 'Send the heading' })).toBeNull()
    expect(screen.queryByRole('textbox', { name: 'Heading' })).toBeNull()
    expect(screen.getByRole('button', { name: 'USB setup' })).toBeTruthy()
    expect(screen.getByRole('radiogroup', { name: 'City presets' })).toBeTruthy()
  })

  it('opens the setup for preparing from its button', async () => {
    const { backend } = await openSection(gpsState(outputs.usbUnprepared))
    fireEvent.click(screen.getByRole('button', { name: 'USB setup' }))
    await waitFor(() => expect(backend.gpsCommands).toEqual(['usb-setup open prepare']))
  })

  it('puts the car in a city with the presets, for USB', async () => {
    const { backend } = await openSection(gpsState(outputs.usbOn))
    fireEvent.click(screen.getByRole('radio', { name: 'PAR' }))
    await waitFor(() => expect(backend.saved).toHaveLength(1))
    expect(Object.keys(backend.saved[0]).sort()).toEqual(['gps.heading', 'gps.start'])
  })
})

describe('GPS page, what waits', () => {
  it('holds the position and the car while the simulation is off', async () => {
    await openSection(gpsState(outputs.carplayOff))
    expect(inert(screen.getByRole('textbox', { name: 'latitude' }))).toBe(true)
    expect(inert(screen.getByRole('textbox', { name: 'Power' }))).toBe(true)
    expect(inert(enable())).toBe(false)
  })

  it('holds the position and the car for a USB phone that is not there', async () => {
    await openSection(gpsState(outputs.usbStartupMissing))
    expect(inert(screen.getByRole('textbox', { name: 'latitude' }))).toBe(true)
    expect(inert(screen.getByRole('textbox', { name: 'Power' }))).toBe(true)
  })

  // Waiting for a position: the position is how it ends, so it stays open; the car waits for it.
  it('leaves the position open while one is awaited, and the car waits', async () => {
    const { backend } = await openSection(gpsState(outputs.usbNoPosition))
    expect(inert(screen.getByRole('textbox', { name: 'latitude' }))).toBe(false)
    expect(inert(screen.getByRole('button', { name: 'Use Windows location' }))).toBe(false)
    expect(inert(screen.getByRole('radio', { name: 'PAR' }))).toBe(false)
    expect(inert(screen.getByRole('textbox', { name: 'Power' }))).toBe(true)

    const latitude = screen.getByRole('textbox', { name: 'latitude' })
    fireEvent.change(latitude, { target: { value: '10' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'longitude' }), { target: { value: '20' } })
    fireEvent.blur(screen.getByRole('textbox', { name: 'longitude' }))
    await waitFor(() => expect(backend.saved).toEqual([{ 'gps.start': '10, 20', 'gps.heading': '90' }]))
  })

  it('keeps the position of the other method when the method changes', async () => {
    const { update } = await openSection(gpsState(outputs.carplayOn))
    const before = (screen.getByRole('textbox', { name: 'latitude' }) as HTMLInputElement).value
    update(gpsState(gpsOutput({ ...outputs.carplayOn, selected: 'usb' })))
    expect((screen.getByRole('textbox', { name: 'latitude' }) as HTMLInputElement).value).toBe(before)
  })
})
