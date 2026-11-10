import { useEffect, useState } from 'react'
import type { AppState, Backend, DemoKind, FolderSizes } from '../api'
import { keyLabel } from '../ControlsScreen'
import { PhoneIcon } from '../gps/icons'
import { Group, Row, Segmented, Switch, type Choice } from '../shell/controls'
import { LogsIcon, ScreenshotsIcon } from '../shell/icons'
import type { Settings } from '../shell/useSettings'
import { byteSize } from './byteSize'
import { Changed, parts, pendingOf } from './parts'

/** The logs and screenshots folders' sizes, read as the page opens: adding them up walks every file in them. */
function useFolderSizes(backend: Backend): FolderSizes {
  const [sizes, setSizes] = useState<FolderSizes>({})
  useEffect(() => {
    let open = true
    backend.folderSizes().then(
      (next) => open && setSizes(next),
      () => {},
    )
    return () => {
      open = false
    }
  }, [backend])
  return sizes
}

const LEVELS: readonly Choice[] = [
  ['error', 'Errors'],
  ['warning', 'Warnings'],
  ['info', 'Info'],
  ['debug', 'Debug'],
  ['trace', 'Trace'],
]

const DEMOS: readonly (readonly [kind: DemoKind, label: string, title: string])[] = [
  ['maneuvers', 'Maneuvers', "Play a made-up route on the AltScreen's HUD"],
  ['lanes', 'Lane changes', "Switch the lanes under one maneuver on the AltScreen's HUD"],
  ['gauges', 'Gauges', "Run the gauges up and down on the AltScreen, large, in the style set on the GPS page"],
]

/**
 * What is still being tried, and what helps to look into it: the HUD's demos, played on the AltScreen without an iPhone,
 * the app log's level, the CarPlay data log, and the folders the logs and the screenshots go to.
 */
export function ExperimentsPage({
  state,
  backend,
  settings,
}: {
  state: AppState
  backend: Backend
  settings: Settings
}) {
  const demo = state.demo
  const { note } = parts(settings)
  const screenshotKey = state.controls?.keys.screenshot
  const sizes = useFolderSizes(backend)
  // Another app drives the GPS car only while the GPS is on.
  const gpsOn = settings.value('gps.enabled') === 'true'
  return (
    <>
      <Group title="Logs" note={note(['log.carplay.data'])} />
      <Row label="Log level" title="How much the app's log in the logs folder tells; Trace adds every packet">
        <Segmented
          label="Log level"
          value={settings.value('log.level') || 'info'}
          options={LEVELS}
          onChange={(level) => settings.save({ 'log.level': level })}
        />
      </Row>
      <Row
        label="CarPlay data"
        title="The iPhone's route, song, call, and battery messages, one JSON object a line, in the logs folder"
      >
        <Switch
          label="Write the iPhone's messages to a file"
          checked={settings.value('log.carplay.data') === 'true'}
          onChange={(checked) => settings.save({ 'log.carplay.data': String(checked) })}
        />
        <Changed keys={['log.carplay.data']} pending={pendingOf(state)} />
      </Row>
      <Row label="Folders">
        <span className="folder">
          <button
            type="button"
            className="btn small"
            title="Open the logs folder in File Explorer"
            onClick={() => backend.open('logs')}
          >
            <LogsIcon />
            Logs
          </button>
          {sizes.logs ? <span className="folder-size">{byteSize(sizes.logs)}</span> : null}
        </span>
        <span className="folder">
          <button
            type="button"
            className="btn small"
            title={`Open the screenshots folder in File Explorer${screenshotKey ? ` (${keyLabel(screenshotKey)} saves to it)` : ''}`}
            onClick={() => backend.open('screenshots')}
          >
            <ScreenshotsIcon />
            Screenshots
          </button>
          {sizes.screenshots ? <span className="folder-size">{byteSize(sizes.screenshots)}</span> : null}
        </span>
        {/* Only once a package of developer tools is downloaded: the app reports a size then. */}
        {sizes.developerTools !== undefined && (
          <span className="folder">
            <button
              type="button"
              className="btn small"
              title="Open the developer tools folder in File Explorer"
              onClick={() => backend.open('developer-tools')}
            >
              <PhoneIcon />
              Developer tools
            </button>
            {sizes.developerTools ? <span className="folder-size">{byteSize(sizes.developerTools)}</span> : null}
          </span>
        )}
      </Row>
      <Group title="Integrations" note="With other apps on this PC" />
      <Row
        label="CarPlay picture"
        title="Let another app on this PC show the CarPlay picture on its own screen"
      >
        <Switch
          label="Publish as a shared texture"
          checked={settings.value('display.share') === 'true'}
          onChange={(checked) => settings.save({ 'display.share': String(checked) })}
        />
      </Row>
      <Row
        label="GPS car"
        title={
          gpsOn
            ? 'Move the GPS car by the position, heading, and speed another app sends; the gamepad and keys drive it again when it stops sending'
            : 'Turn on the GPS on its page first'
        }
      >
        <Switch
          label="Drive from other apps"
          checked={settings.value('gps.external') === 'true'}
          disabled={!gpsOn}
          onChange={(checked) => settings.save({ 'gps.external': String(checked) })}
        />
      </Row>
      <Group title="HUD demo" note="On the AltScreen's HUD" />
      {DEMOS.map(([kind, label, title]) => {
        const playing = demo?.playing === kind
        // No AltScreen in this connection, or one turned off on its page since: no demo starts; one playing can stop.
        const off = !demo?.available || settings.value('altscreen.enabled') !== 'true'
        return (
          <Row key={kind} label={label}>
            <button
              type="button"
              className="btn small"
              disabled={off && !playing}
              title={playing ? 'Stop the demo' : off ? 'Turn on the AltScreen on its page first' : title}
              onClick={() => backend.demo(playing ? null : kind)}
            >
              {playing ? 'Stop' : 'Play'}
            </button>
          </Row>
        )
      })}
    </>
  )
}
