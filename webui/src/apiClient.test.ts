import { afterEach, describe, expect, it, vi } from 'vitest'
import { Api } from './apiClient'

afterEach(() => vi.unstubAllGlobals())

/** Stubs fetch to answer [status] and records each request. */
function answer(status: number) {
  const requests: { url: string; init: RequestInit }[] = []
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    requests.push({ url, init })
    return new Response('{}', { status })
  })
  return requests
}

describe('Api', () => {
  it('requests the desktop update check and reports when it is busy', async () => {
    const requests = answer(200)
    expect(await new Api().checkUpdates()).toBe(true)
    expect(requests[0].url).toBe('/api/check-updates')
    expect(requests[0].init.method).toBe('POST')
    expect(requests[0].init.headers).not.toHaveProperty('Authorization')
    answer(409)
    expect(await new Api().checkUpdates()).toBe(false)
  })

  it('asks the app to show or hide a window', async () => {
    const requests = answer(200)

    expect(await new Api().window('controls', false)).toBe(true)

    expect(requests).toHaveLength(1)
    expect(requests[0].url).toBe('/api/window')
    expect(requests[0].init.method).toBe('POST')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ name: 'controls', visible: false })
  })

  it('tells when a window is not available', async () => {
    answer(404)

    expect(await new Api().window('altscreen', true)).toBe(false)
  })

  it('asks the app to disconnect', async () => {
    const requests = answer(200)

    await new Api().disconnect()

    expect(requests[0].url).toBe('/api/disconnect')
    expect(requests[0].init.method).toBe('POST')
  })

  it('asks the app to bring the main window to the actual size', async () => {
    const requests = answer(200)

    expect(await new Api().actualSize('altscreen')).toBe(true)

    expect(requests[0].url).toBe('/api/window-size')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ actual: true, window: 'altscreen' })
  })

  it('reads the key bindings, or none when the app has none', async () => {
    const requests = answer(200)
    expect(await new Api().keyBindings()).toEqual({})
    expect(requests[0].url).toBe('/api/controls')

    answer(404)
    expect(await new Api().keyBindings()).toBeNull()
  })

  it('saves key bindings as settings are saved', async () => {
    const requests = answer(200)

    expect(await new Api().saveKeyBindings({ 'key.home': 'F6' })).toEqual({ ok: true, applies: {} })
    expect(requests[0].url).toBe('/api/controls')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ changes: { 'key.home': 'F6' } })
  })

  it('asks the app to forget the iPhone', async () => {
    const requests = answer(200)

    expect(await new Api().forget()).toBe(true)
    expect(requests[0].url).toBe('/api/forget')
  })

  it('asks the app to add the firewall rule, and tells whether it did', async () => {
    const requests = answer(200)
    expect(await new Api().addFirewallRule()).toBe(true)
    expect(requests[0].url).toBe('/api/firewall-rule')

    answer(409)
    expect(await new Api().addFirewallRule()).toBe(false)
  })

  // A link to a Windows page names the place; the app knows what to start for it.
  it('asks the app to open a Windows page', async () => {
    const requests = answer(200)

    expect(await new Api().open('bluetooth')).toBe(true)

    expect(requests[0].url).toBe('/api/open')
    expect(requests[0].init.method).toBe('POST')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ place: 'bluetooth' })
  })

  it('tells when a Windows page did not open', async () => {
    answer(404)

    expect(await new Api().open('firewall-rules')).toBe(false)
  })
})
