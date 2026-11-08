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

  it('asks the app to open the settings in the browser', async () => {
    const requests = answer(200)
    expect(await new Api().openInBrowser()).toBe(true)
    expect(requests[0].url).toBe('/api/open-in-browser')
    expect(requests[0].init.method).toBe('POST')

    answer(500)
    expect(await new Api().openInBrowser()).toBe(false)
  })

  it('asks the app to open a local network address in the browser', async () => {
    const requests = answer(200)
    expect(await new Api().openInBrowser('http://192.168.1.20:7529')).toBe(true)
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ url: 'http://192.168.1.20:7529' })
  })
})

describe('Api.subscribe', () => {
  // A page back from the back/forward cache had its stream closed while it was kept, and may get no error for it.
  it('opens its stream again when the page comes back from going back', () => {
    const sources: { url: string; closed: boolean }[] = []
    vi.stubGlobal('EventSource', class {
      onmessage: ((event: MessageEvent) => void) | null = null
      onerror: (() => void) | null = null
      closed = false
      constructor(readonly url: string) {
        sources.push(this)
      }
      close() {
        this.closed = true
      }
    })

    const stop = new Api().subscribe(() => {}, () => {})
    window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: false }))
    expect(sources).toHaveLength(1)
    window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }))

    expect(sources).toHaveLength(2)
    expect(sources[0].closed).toBe(true)
    expect(sources[1].url).toBe('/api/events')
    stop()
    window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }))
    expect(sources).toHaveLength(2)
  })
})
