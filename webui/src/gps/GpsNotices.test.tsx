import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { GpsNotice } from '../gpsApi'
import { gpsOutput, outputs } from '../gpsFixtures'
import { gpsState, openSection } from './gpsTesting'

afterEach(cleanup)

const inert = (element: HTMLElement) => element.closest('[inert]') !== null
const page = () => screen.getByRole('tabpanel')
const blocks = () => within(page()).queryAllByRole('status')
const alerts = () => within(page()).queryAllByRole('alert')

/** A notice of [code] that stays, or goes at [expiresAt]. */
function notice(code: GpsNotice['code'], tone: GpsNotice['tone'], expiresAt: number | null = null): GpsNotice {
  return { id: 1, code, tone, args: {}, expiresAt }
}

describe('GPS notices', () => {
  // One status line for the operation under way, whatever its kind; none without one.
  it('shows the operation under way as one status line, and none without one', async () => {
    const { update } = await openSection(gpsState(outputs.stoppingUsb))
    expect(blocks()).toHaveLength(1)
    const first = blocks()[0].textContent
    update(gpsState({ ...outputs.stoppingUsb, operation: { ...outputs.stoppingUsb.operation!, kind: 'enable' } }))
    expect(blocks()).toHaveLength(1)
    expect(blocks()[0].textContent).not.toBe(first)
    update(gpsState(outputs.carplayOff))
    expect(blocks()).toHaveLength(0)
  })

  it('shows no stage: the status line is the same at every stage', async () => {
    const operation = outputs.stoppingUsb.operation!
    const { update } = await openSection(gpsState(outputs.stoppingUsb))
    const first = blocks()[0].textContent
    update(gpsState({ ...outputs.stoppingUsb, operation: { ...operation, stage: 'clear' } }))
    expect(blocks()[0].textContent).toBe(first)
  })

  // Reset command sent goes at its time (10 seconds in the app).
  it('drops the reset result at its time', async () => {
    const output = gpsOutput({
      selected: 'usb', usbPrepared: true,
      notices: [{ ...notice('reset-sent', 'info', Date.now() + 400), id: 1 }],
    })
    await openSection(gpsState(output))
    expect(blocks()).toHaveLength(1)
    await waitFor(() => expect(blocks()).toHaveLength(0))
  })

  it('does not show a reset result that has already gone', async () => {
    await openSection(gpsState(gpsOutput({ notices: [notice('reset-sent', 'info', Date.now() - 1)] })))
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

  it('warns of Windows location unavailable', async () => {
    await openSection(gpsState(outputs.windowsUnavailable))
    expect(alerts()).toHaveLength(1)
  })

  it('does not put a modal dialog over anything', async () => {
    await openSection(gpsState(outputs.usbStartupMissing))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })
})

describe('GPS warning over the window (USB unavailable)', () => {
  // On every section, outside the scrolling page, so the next section's content does not hide it (UI-23, V35).
  it('is on every section, outside the page that scrolls', async () => {
    const { backend } = await openSection(gpsState(outputs.usbStartupMissing))
    expect(page().contains(screen.getByRole('alert'))).toBe(false)

    fireEvent.click(screen.getByRole('tab', { name: 'Experiments' }))
    expect(screen.getByRole('alert')).toBeTruthy()
    fireEvent.click(screen.getByRole('tab', { name: 'About' }))
    expect(screen.getByRole('alert')).toBeTruthy()
    expect(backend.gpsCommands).toEqual([])
  })

  it('goes with the notice, and is not on the GPS page twice', async () => {
    const { update } = await openSection(gpsState(outputs.usbStartupMissing))
    expect(screen.getAllByRole('alert')).toHaveLength(1)
    update(gpsState(outputs.carplayOff))
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('leaves the choice of the method open, with no button of its own for CarPlay', async () => {
    await openSection(gpsState(outputs.usbStartupMissing))
    expect(within(screen.getByRole('alert')).queryAllByRole('button')).toHaveLength(0)
    expect((screen.getByRole('radio', { name: 'CarPlay' }) as HTMLButtonElement).disabled).toBe(false)
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
