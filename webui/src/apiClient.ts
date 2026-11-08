// The app's API client (UiServer.kt): JSON over HTTP on 127.0.0.1, to the address the page came from. The types it
// speaks are in api.ts.
import type {
  AltscreenContent,
  AppState,
  Backend,
  ControlsData,
  DemoKind,
  FolderSizes,
  KeyPhase,
  PictureWindow,
  SaveOutcome,
  Screen,
  SettingsData,
  WindowName,
  WindowsLocation,
  WindowsPlace,
} from './api'

export class ApiError extends Error {}

/**
 * Every request names [page] (page.ts) for the settings lock; [handOver], the key Open in browser gave this tab, goes
 * with its first event stream only.
 */
export class Api implements Backend {
  constructor(
    readonly page: string,
    private handOver: string | null = null,
  ) {}

  private async request(
    method: 'GET' | 'POST',
    path: string,
    body?: unknown,
    keepalive = false,
  ): Promise<{ status: number; data: any }> {
    const headers: Record<string, string> = { 'X-ChairPlay-Page': this.page }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const response = await fetch(`/api/${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      ...(keepalive ? { keepalive } : {}),
    })
    const data = await response.json().catch(() => ({}))
    return { status: response.status, data }
  }

  async state(): Promise<AppState> {
    return (await this.request('GET', 'state')).data as AppState
  }

  async settings(): Promise<SettingsData> {
    return (await this.request('GET', 'settings')).data as SettingsData
  }

  async save(changes: Record<string, string>): Promise<SaveOutcome> {
    return this.saveTo('settings', changes)
  }

  async folderSizes(): Promise<FolderSizes> {
    return (await this.request('GET', 'folder-sizes')).data as FolderSizes
  }

  async savedPassphrase(): Promise<string | null> {
    return ((await this.request('GET', 'passphrase')).data as { passphrase: string | null }).passphrase
  }

  async demo(kind: DemoKind | null): Promise<boolean> {
    return (await this.request('POST', 'demo', { kind })).status === 200
  }

  async volume(level: number): Promise<boolean> {
    return (await this.request('POST', 'volume', { level })).status === 200
  }

  async windowsLocation(): Promise<WindowsLocation> {
    return (await this.request('POST', 'windows-location', {})).data as WindowsLocation
  }

  async checkUpdates(): Promise<boolean> {
    return (await this.request('POST', 'check-updates', {})).status === 200
  }

  async driveMode(on: boolean): Promise<boolean> {
    return (await this.request('POST', 'drive-mode', { on })).status === 200
  }

  async showAltscreenFrame(view?: string): Promise<boolean> {
    return (await this.request('POST', 'altscreen-frame', view ? { view } : {})).status === 200
  }

  async forget(): Promise<boolean> {
    return (await this.request('POST', 'forget', {})).status === 200
  }

  async addFirewallRule(): Promise<boolean> {
    return (await this.request('POST', 'firewall-rule', {})).status === 200
  }

  async keyBindings(): Promise<ControlsData | null> {
    const { status, data } = await this.request('GET', 'controls')
    return status === 200 ? (data as ControlsData) : null
  }

  async saveKeyBindings(changes: Record<string, string>): Promise<SaveOutcome> {
    return this.saveTo('controls', changes)
  }

  private async saveTo(path: 'settings' | 'controls', changes: Record<string, string>): Promise<SaveOutcome> {
    const { status, data } = await this.request('POST', path, { changes })
    if (status === 200) return { ok: true, applies: data.applies ?? {} }
    if (status === 400 && data.errors) return { ok: false, errors: data.errors }
    throw new ApiError(data.error ?? `saving failed (${status})`)
  }

  async connect(): Promise<string | null> {
    const { status, data } = await this.request('POST', 'connect', {})
    return status === 200 ? null : (data.error ?? `failed (${status})`)
  }

  async reconnect(): Promise<void> {
    await this.request('POST', 'reconnect', {})
  }

  async disconnect(): Promise<void> {
    await this.request('POST', 'disconnect', {})
  }

  async show(screen: Screen): Promise<boolean> {
    return (await this.request('POST', 'screen', { screen })).status === 200
  }

  async window(name: WindowName, visible: boolean): Promise<boolean> {
    return (await this.request('POST', 'window', { name, visible })).status === 200
  }

  async control(action: string, down: boolean): Promise<void> {
    await this.request('POST', 'control', { action, down })
  }

  async key(name: string, phase: KeyPhase, everything: boolean): Promise<void> {
    await this.request('POST', 'key', { name, phase, everything })
  }
  async keyboardFocus(client: string, focused: boolean): Promise<void> {
    await this.request('POST', 'keyboard-focus', { client, focused })
  }

  async altscreen(content: AltscreenContent): Promise<boolean> {
    return (await this.request('POST', 'altscreen', { content })).status === 200
  }

  async open(place: WindowsPlace): Promise<boolean> {
    return (await this.request('POST', 'open', { place })).status === 200
  }

  async actualSize(window: PictureWindow): Promise<boolean> {
    return (await this.request('POST', 'window-size', { actual: true, window })).status === 200
  }

  /** The page going away does not wait for the answer: keepalive lets the request outlive it. */
  async presence(visible: boolean, closing = false): Promise<void> {
    const sent = this.request('POST', 'page', closing ? { visible, closing } : { visible }, closing).catch(() => {})
    if (!closing) await sent
  }

  async openInBrowser(): Promise<boolean> {
    return (await this.request('POST', 'open-in-browser', {})).status === 200
  }

  /** The event stream's address, as the page is at this open: its id, its visibility, and a hand-over key once. */
  private eventsUrl(): string {
    const visible = document.visibilityState === 'hidden' ? 0 : 1
    let url = `/api/events?page=${encodeURIComponent(this.page)}&visible=${visible}`
    if (this.handOver !== null) url += `&handover=${encodeURIComponent(this.handOver)}`
    this.handOver = null
    return url
  }

  /**
   * Calls [onState] with the state now and on every change, over server-sent events; while those fail (the app is
   * gone or restarting) it polls instead. Returns a function that stops it.
   */
  subscribe(onState: (state: AppState) => void, onLost: (lost: boolean) => void): () => void {
    let source: EventSource | null = null
    let poll: number | undefined
    let stopped = false
    const open = () => {
      source = new EventSource(this.eventsUrl())
      source.onmessage = (event) => {
        onLost(false)
        onState(JSON.parse(event.data) as AppState)
      }
      source.onerror = () => {
        onLost(true)
        source?.close()
        if (!stopped) poll = window.setTimeout(retry, 2000)
      }
    }
    const retry = async () => {
      try {
        onState(await this.state())
        onLost(false)
        open()
      } catch {
        if (!stopped) poll = window.setTimeout(retry, 2000)
      }
    }
    // Back from the back/forward cache, the tab's stream was closed while it was kept, with no error to say so.
    const back = (event: PageTransitionEvent) => {
      if (!event.persisted || stopped) return
      source?.close()
      window.clearTimeout(poll)
      open()
    }
    open()
    window.addEventListener('pageshow', back)
    return () => {
      stopped = true
      source?.close()
      window.clearTimeout(poll)
      window.removeEventListener('pageshow', back)
    }
  }
}
