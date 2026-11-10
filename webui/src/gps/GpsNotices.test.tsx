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
  // One block over the switch for every stage of every operation; none without an operation.
  it('shows the operation running as one block, and no block without one', async () => {
    const { update } = await openSection(gpsState(outputs.switchingToUsb))
    expect(blocks()).toHaveLength(1)
    for (const output of [outputs.switchingToCarplay, outputs.stoppingUsb]) {
      update(gpsState(output))
      expect(blocks()).toHaveLength(1)
    }
    update(gpsState(outputs.carplayOff))
    expect(blocks()).toHaveLength(0)
  })

  it('shows the stages of one operation as it moves on, in the same block', async () => {
    const operation = outputs.switchingToUsb.operation!
    const { update } = await openSection(gpsState(outputs.switchingToUsb))
    const first = blocks()[0].textContent
    update(gpsState({ ...outputs.switchingToUsb, operation: { ...operation, stage: 'start' } }))
    expect(blocks()).toHaveLength(1)
    expect(blocks()[0].textContent).not.toBe(first)
  })

  // Reset command sent goes at its time (10 seconds in the app); a notice that stays (an error) does not.
  it('drops the reset result at its time and keeps the notices that stay', async () => {
    const output = gpsOutput({
      selected: 'usb', usbPrepared: true,
      notices: [
        { ...notice('reset-sent', 'info', Date.now() + 400), id: 1 },
        { ...notice('cleanup-unconfirmed', 'warn'), id: 2 },
      ],
    })
    await openSection(gpsState(output))
    expect(blocks()).toHaveLength(1)
    expect(alerts()).toHaveLength(1)

    await waitFor(() => expect(blocks()).toHaveLength(0))
    expect(alerts()).toHaveLength(1)
  })

  it('does not show a reset result that has already gone', async () => {
    await openSection(gpsState(gpsOutput({ notices: [notice('reset-sent', 'info', Date.now() - 1)] })))
    expect(blocks()).toHaveLength(0)
  })

  // D4: the app never reconnects CarPlay by itself; the notice offers it, and the button is the user's.
  it('offers Reconnect for a reconnect that is needed, and reconnects only on the button', async () => {
    const { backend } = await openSection(gpsState(outputs.reconnectNeeded))
    expect(backend.reconnects).toBe(0)
    fireEvent.click(within(page()).getByRole('button', { name: 'Reconnect' }))
    await waitFor(() => expect(backend.reconnects).toBe(1))
  })

  it('warns of Windows location unavailable and of a reset not confirmed', async () => {
    const { update } = await openSection(gpsState(outputs.windowsUnavailable))
    expect(alerts()).toHaveLength(1)
    update(gpsState(outputs.cleanupUnconfirmed))
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
    const warning = screen.getByRole('alert')
    expect(page().contains(warning)).toBe(false)

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

describe('the window while an operation runs', () => {
  it('holds the toolbar, the sections, and the page, and leaves the message readable', async () => {
    await openSection(gpsState(outputs.switchingToUsb))
    expect(screen.getByRole('banner').closest('[inert]')).not.toBeNull()
    expect(inert(screen.getByRole('tab', { name: 'About' }))).toBe(true)
    expect(inert(screen.getByRole('switch', { name: 'Enable signal simulation' }))).toBe(true)
    expect(inert(screen.getByRole('radio', { name: 'CarPlay' }))).toBe(true)
    expect(inert(screen.getByRole('textbox', { name: 'latitude' }))).toBe(true)
    expect(inert(blocks()[0])).toBe(false)
    expect(screen.getByRole('switch', { name: 'Enable signal simulation' }).closest('[aria-busy]')).not.toBeNull()
  })

  it('holds the other sections too, and says why on them', async () => {
    // The sections cannot be clicked while it runs: Experiments is open when the operation begins.
    const { update } = await openSection(gpsState(outputs.carplayOff), undefined, 'Experiments')
    update(gpsState(outputs.stoppingUsb))
    expect(inert(screen.getByRole('button', { name: 'Logs' }))).toBe(true)
    expect(within(page().parentElement!).getAllByRole('status')).toHaveLength(1)
  })

  it('gives the window back when the operation ends', async () => {
    const { update } = await openSection(gpsState(outputs.switchingToUsb))
    update(gpsState(outputs.usbPreparedNoCable))
    expect(inert(screen.getByRole('tab', { name: 'About' }))).toBe(false)
    expect(inert(screen.getByRole('switch', { name: 'Enable signal simulation' }))).toBe(false)
    expect(screen.getByRole('banner').closest('[inert]')).toBeNull()
  })
})
