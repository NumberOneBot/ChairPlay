import { describe, expect, it } from 'vitest'
import { appState } from '../testing'
import { setupSteps } from './steps'

describe('Ethernet setup readiness', () => {
  it('holds Connect until an adapter is available and selected', () => {
    const state = appState()
    state.setup = { ...state.setup, mfiPresent: true, firewall: 'present', pairedIphone: 'iPhone', pcWifi: null,
      ethernet: [], ethernetSelected: null }
    expect(setupSteps(state, null).allDone).toBe(false)
    state.setup.ethernet = [{ id: 'a', label: 'Ethernet', addresses: ['192.168.1.2'] }]
    state.setup.ethernetSelected = 'a'
    expect(setupSteps(state, null).allDone).toBe(true)
    state.setup.ethernetSelected = null
    expect(setupSteps(state, null).allDone).toBe(false)
  })
})
