import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { outputs } from '../gpsFixtures'
import { gpsState, openSection } from './gpsTesting'

afterEach(cleanup)

const inert = (element: HTMLElement) => element.closest('[inert]') !== null
const page = () => screen.getByRole('tabpanel')
const blocks = () => within(page()).queryAllByRole('status')

describe('GPS notices', () => {
  // An operation under way shows no block: the switch waiting is all it needs.
  it('shows no block for an operation under way', async () => {
    const { update } = await openSection(gpsState(outputs.stoppingUsb))
    expect(blocks()).toHaveLength(0)
    update(gpsState({ ...outputs.stoppingUsb, operation: { ...outputs.stoppingUsb.operation!, kind: 'enable' } }))
    expect(blocks()).toHaveLength(0)
  })

  // D8: a method chosen waits for the next connection with the settings' own mark, not a notice; the app reconnects
  // nothing by itself.
  it('marks a method that waits for the next connection as any such setting', async () => {
    const { backend } = await openSection(gpsState(outputs.reconnectNeeded, undefined, { reconnectPending: ['gps.method'] }))
    expect(within(page()).getByTitle('Changed; applies at the next connection')).toBeTruthy()
    expect(blocks()).toHaveLength(0)
    expect(backend.reconnects).toBe(0)
  })

  it('does not put a modal dialog over anything', async () => {
    await openSection(gpsState(outputs.usbStartupMissing))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })
})

describe('GPS warning (USB unavailable)', () => {
  const warnings = () => screen.queryAllByTestId('gps-notice-usb-unavailable')
  const tab = (id: string) => {
    const found = screen.getByTestId(`tab-${id}`)
    expect(found.getAttribute('role')).toBe('tab')
    return found
  }

  // On the GPS page alone, inside it as a section's own warning is; no other section shows it (owner, 2026-11-11).
  it('is on the GPS page, inside it, and on no other section', async () => {
    const { backend } = await openSection(gpsState(outputs.usbStartupMissing))
    expect(warnings()).toHaveLength(1)
    expect(warnings()[0].getAttribute('role')).toBe('alert')
    expect(page().contains(warnings()[0])).toBe(true)

    fireEvent.click(tab('experiments'))
    expect(warnings()).toHaveLength(0)
    fireEvent.click(tab('about'))
    expect(warnings()).toHaveLength(0)
    expect(backend.gpsCommands).toEqual([])
  })

  it('goes with the notice', async () => {
    const { update } = await openSection(gpsState(outputs.usbStartupMissing))
    update(gpsState(outputs.carplayOff))
    expect(warnings()).toHaveLength(0)
  })

  it('leaves the choice of the method open, with no button of its own for CarPlay', async () => {
    await openSection(gpsState(outputs.usbStartupMissing))
    expect(within(warnings()[0]).queryAllByRole('button')).toHaveLength(0)
    const carplay = screen.getByTestId('gps-method-carplay')
    expect(carplay.getAttribute('role')).toBe('radio')
    expect((carplay as HTMLButtonElement).disabled).toBe(false)
  })
})

// D9: an operation holds the switch and nothing else.
describe('the window while an operation runs', () => {
  it('holds the switch only, on every part of the window', async () => {
    const { update } = await openSection(gpsState(outputs.stoppingUsb))
    expect((screen.getByRole('switch', { name: 'Enable signal simulation' }) as HTMLInputElement).disabled).toBe(true)
    expect(inert(screen.getByRole('banner'))).toBe(false)
    expect(inert(screen.getByRole('tab', { name: 'About' }))).toBe(false)
    expect(inert(screen.getByRole('radio', { name: 'CarPlay' }))).toBe(false)
    expect(screen.getByRole('tabpanel').closest('[aria-busy]')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: 'Experiments' }))
    expect(inert(screen.getByRole('button', { name: 'Logs' }))).toBe(false)
    expect(within(page().parentElement!).queryAllByRole('status')).toHaveLength(0)
    update(gpsState(outputs.usbPreparedNoCable))
  })

  it('takes Escape as usual', async () => {
    const { backend } = await openSection(gpsState(outputs.stoppingUsb))
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(backend.shown).toEqual(['carplay'])
  })

  it('gives the switch back when the operation ends', async () => {
    const { update } = await openSection(gpsState(outputs.stoppingUsb))
    update(gpsState(outputs.usbPreparedNoCable))
    expect((screen.getByRole('switch', { name: 'Enable signal simulation' }) as HTMLInputElement).disabled).toBe(false)
  })
})
