import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Shell } from '../shell/Shell'
import { FakeBackend } from '../fakeBackend'
import { appState } from '../testing'

afterEach(cleanup)

describe('Ethernet adapter selection', () => {
  for (const setupDone of [false, true]) {
    it(`saves adapter identity from ${setupDone ? 'settings' : 'Get started'} and holds Connect until selection`, async () => {
      const state = appState({ phase: 'idle', setupDone })
      state.setup = { ...state.setup, mfiPresent: true, firewall: 'present', pairedIphone: 'iPhone', pcWifi: null,
        ethernet: [
          { id: 'adapter-a', label: 'Ethernet', addresses: ['192.168.1.2'] },
          { id: 'adapter-b', label: 'USB Ethernet', addresses: ['10.0.0.2'] },
        ], ethernetSelected: null }
      const backend = new FakeBackend()
      const { rerender } = render(<Shell state={state} backend={backend} />)
      const select = await screen.findByRole('combobox', { name: 'Ethernet adapter' })
      expect((screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement).disabled).toBe(true)
      fireEvent.change(select, { target: { value: 'adapter-b' } })
      await waitFor(() => expect(backend.saved).toContainEqual({ 'network.ethernet.adapter': 'adapter-b' }))
      rerender(<Shell state={{ ...state, setup: { ...state.setup, ethernetSelected: 'adapter-b' } }} backend={backend} />)
      expect((screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement).disabled).toBe(false)
      fireEvent.click(screen.getByRole('button', { name: 'Connect' }))
      expect(backend.connects).toBe(1)
    })
  }
})

describe('Wi-Fi network hidden by Windows', () => {
  for (const setupDone of [false, true]) {
    it(`holds Connect and opens the location settings from ${setupDone ? 'settings' : 'Get started'}`, async () => {
      const state = appState({ phase: 'idle', setupDone })
      state.setup = { ...state.setup, mfiPresent: true, firewall: 'present', pairedIphone: 'iPhone', pcWifi: null,
        ethernet: [], ethernetSelected: null, wifiHidden: true }
      const backend = new FakeBackend()
      render(<Shell state={state} backend={backend} />)
      fireEvent.click(await screen.findByRole('button', { name: 'Open location settings' }))
      expect(backend.opened).toEqual(['location'])
      expect((screen.getByRole('button', { name: 'Connect' }) as HTMLButtonElement).disabled).toBe(true)
    })
  }
})
