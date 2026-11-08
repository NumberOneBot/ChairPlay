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

export class Api implements Backend {
  private async request(method: 'GET' | 'POST', path: string, body?: unknown): Promise<{ status: number; data: any }> {
    const response = await fetch(`/api/${path}`, {
      method,
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
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

  async openInBrowser(url?: string): Promise<boolean> {
    return (await this.request('POST', 'open-in-browser', url === undefined ? {} : { url })).status === 200
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
      source = new EventSource('/api/events')
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
    // Back from the back/forward cache, the page's stream was closed while it was kept, with no error to say so.
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
