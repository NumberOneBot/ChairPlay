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
    expect(await new Api('p1').checkUpdates()).toBe(true)
    expect(requests[0].url).toBe('/api/check-updates')
    expect(requests[0].init.method).toBe('POST')
    expect(requests[0].init.headers).not.toHaveProperty('Authorization')
    answer(409)
    expect(await new Api('p1').checkUpdates()).toBe(false)
  })

  it('asks the app to show or hide a window', async () => {
    const requests = answer(200)

    expect(await new Api('p1').window('controls', false)).toBe(true)

    expect(requests).toHaveLength(1)
    expect(requests[0].url).toBe('/api/window')
    expect(requests[0].init.method).toBe('POST')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ name: 'controls', visible: false })
  })

  it('tells when a window is not available', async () => {
    answer(404)

    expect(await new Api('p1').window('altscreen', true)).toBe(false)
  })

  it('asks the app to disconnect', async () => {
    const requests = answer(200)

    await new Api('p1').disconnect()

    expect(requests[0].url).toBe('/api/disconnect')
    expect(requests[0].init.method).toBe('POST')
  })

  it('asks the app to bring the main window to the actual size', async () => {
    const requests = answer(200)

    expect(await new Api('p1').actualSize('altscreen')).toBe(true)

    expect(requests[0].url).toBe('/api/window-size')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ actual: true, window: 'altscreen' })
  })

  it('reads the key bindings, or none when the app has none', async () => {
    const requests = answer(200)
    expect(await new Api('p1').keyBindings()).toEqual({})
    expect(requests[0].url).toBe('/api/controls')

    answer(404)
    expect(await new Api('p1').keyBindings()).toBeNull()
  })

  it('saves key bindings as settings are saved', async () => {
    const requests = answer(200)

    expect(await new Api('p1').saveKeyBindings({ 'key.home': 'F6' })).toEqual({ ok: true, applies: {} })
    expect(requests[0].url).toBe('/api/controls')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ changes: { 'key.home': 'F6' } })
  })

  it('asks the app to forget the iPhone', async () => {
    const requests = answer(200)

    expect(await new Api('p1').forget()).toBe(true)
    expect(requests[0].url).toBe('/api/forget')
  })

  it('asks the app to add the firewall rule, and tells whether it did', async () => {
    const requests = answer(200)
    expect(await new Api('p1').addFirewallRule()).toBe(true)
    expect(requests[0].url).toBe('/api/firewall-rule')

    answer(409)
    expect(await new Api('p1').addFirewallRule()).toBe(false)
  })

  // A link to a Windows page names the place; the app knows what to start for it.
  it('asks the app to open a Windows page', async () => {
    const requests = answer(200)

    expect(await new Api('p1').open('bluetooth')).toBe(true)

    expect(requests[0].url).toBe('/api/open')
    expect(requests[0].init.method).toBe('POST')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ place: 'bluetooth' })
  })

  it('tells when a Windows page did not open', async () => {
    answer(404)

    expect(await new Api('p1').open('firewall-rules')).toBe(false)
  })

  it('names its page on every request', async () => {
    const requests = answer(200)

    await new Api('p1').connect()
    await new Api('p1').state()

    for (const request of requests) expect(request.init.headers).toHaveProperty('X-ChairPlay-Page', 'p1')
  })

  it('reports its presence, and its closing without waiting for the answer', async () => {
    const requests = answer(200)
    const api = new Api('p1')

    await api.presence(true)
    void api.presence(false, true)

    expect(requests[0].url).toBe('/api/page')
    expect(requests[0].init.method).toBe('POST')
    expect(JSON.parse(String(requests[0].init.body))).toEqual({ visible: true })
    expect(JSON.parse(String(requests[1].init.body))).toEqual({ visible: false, closing: true })
    expect(requests[1].init.keepalive).toBe(true)
  })

  it('asks the window to open the settings in the browser', async () => {
    const requests = answer(200)
    expect(await new Api('window').openInBrowser()).toBe(true)
    expect(requests[0].url).toBe('/api/open-in-browser')
    expect(requests[0].init.method).toBe('POST')

    answer(409)
    expect(await new Api('window').openInBrowser()).toBe(false)
  })
})

describe('Api.subscribe', () => {
  afterEach(() => {
    vi.useRealTimers()
    Reflect.deleteProperty(document, 'visibilityState')
  })

  // Review Focus 5: after sleep or a network drop the page comes back as itself, as visible as it is then.
  it('reopens its stream with its page and its visibility, the hand-over key only the first time', async () => {
    const sources: { url: string; onerror: (() => void) | null }[] = []
    vi.stubGlobal('EventSource', class {
      onmessage: ((event: MessageEvent) => void) | null = null
      onerror: (() => void) | null = null
      constructor(readonly url: string) {
        sources.push(this)
      }
      close() {}
    })
    answer(200)
    vi.useFakeTimers()

    const stop = new Api('p1', 'k1').subscribe(() => {}, () => {})
    expect(sources[0].url).toBe('/api/events?page=p1&visible=1&handover=k1')
    sources[0].onerror!()
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    await vi.advanceTimersByTimeAsync(2000)

    expect(sources[1].url).toBe('/api/events?page=p1&visible=0')
    stop()
  })
})
